package com.app.jekyllposter.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.jekyllposter.AppContainer
import android.net.Uri
import com.app.jekyllposter.core.jekyll.Images
import com.app.jekyllposter.core.jekyll.Preview
import com.app.jekyllposter.data.Destination
import com.app.jekyllposter.data.DraftImage
import java.io.File
import java.time.LocalDateTime
import com.app.jekyllposter.core.jekyll.Taxonomy
import com.app.jekyllposter.core.jekyll.Term
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditorViewModel(private val container: AppContainer, private val id: Long) : ViewModel() {
    enum class TermKind { Category, Tag }

    data class State(
        val draft: Draft? = null,
        val taxonomy: Taxonomy = Taxonomy.EMPTY,
        val titleMissing: Boolean = false,
        val previewing: Boolean = false,
        val addingPhoto: Boolean = false,
        val photoError: String? = null,
        val closed: Boolean = false,
    ) {
        /** Published posts and ones on their way are read-only; edit the blog's copy instead. */
        val editable: Boolean get() = draft?.state == PostState.Draft || draft?.state == PostState.Failed
    }

    /**
     * The writer's text, read and written synchronously: a text field bound to a flow that lags a
     * keystroke behind loses typing and jumps the cursor. Null until the draft has loaded.
     */
    var text by mutableStateOf<Draft?>(null)
        private set
    private val local = snapshotFlow { text }
    private val flags = MutableStateFlow(State())
    private var saveJob: Job? = null

    val state: StateFlow<State> = combine(local, container.drafts.watch(id), container.blogs.taxonomy, flags) { mine, stored, taxonomy, f ->
        // The stored row wins for publishing state, which the worker changes; the text is the writer's.
        val draft = when {
            stored == null -> null
            mine == null -> stored
            else -> stored.copy(title = mine.title, body = mine.body, categories = mine.categories, tags = mine.tags, images = mine.images)
        }
        f.copy(draft = draft, taxonomy = taxonomy)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State())

    init {
        viewModelScope.launch {
            val loaded = container.drafts.get(id)
            // Applied at once, so the screen sees it even if no frame is pending to pick it up.
            Snapshot.withMutableSnapshot { text = loaded }
        }
    }

    private fun edit(change: (Draft) -> Draft) {
        val base = text ?: return
        if (state.value.draft != null && !state.value.editable) return
        val next = change(base)
        text = next
        flags.update { it.copy(titleMissing = it.titleMissing && next.title.isBlank()) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            save()
        }
    }

    fun togglePreview() = flags.update { it.copy(previewing = !it.previewing) }

    /** The post as a page, with site images loaded from the live site (or GitHub, before Pages has one). */
    suspend fun previewHtml(dark: Boolean): String {
        val draft = text ?: return ""
        val account = container.accounts.current()
        val base = account?.siteUrl?.trimEnd('/')
            ?: account?.let { "https://raw.githubusercontent.com/${it.owner}/${it.repo}/${it.branch}" }
            ?: ""
        val local = draft.images.associate { it.sitePath to it.file }
        val preview = Preview(container.blogs.config.value) { path ->
            // A photo not on the site yet is shown from the phone, inline: the preview has no file access.
            local[path]?.let { dataUri(File(it)) } ?: (base + path)
        }
        return preview.page(draft.title, draft.body, dark)
    }

    /** Prepares a picked photo and adds its link to the end of the post. */
    fun addPhoto(uri: Uri) {
        if (text == null || !state.value.editable) return
        flags.update { it.copy(addingPhoto = true, photoError = null) }
        viewModelScope.launch {
            try {
                val prepared = container.images.import(uri)
                val draft = text ?: return@launch
                val taken = draft.images.map { it.sitePath }.toSet() + container.blogs.paths
                val sitePath = Images.sitePath(container.blogs.imageFolder.value, LocalDateTime.now(), prepared.extension, taken)
                val link = Images.markdown(sitePath, "")
                edit {
                    val body = if (it.body.isBlank()) link else it.body.trimEnd() + "\n\n" + link
                    it.copy(body = body + "\n", images = it.images + DraftImage(sitePath, prepared.file.path))
                }
            } catch (e: Exception) {
                flags.update { it.copy(photoError = "Couldn't add that photo: ${e.message ?: "it couldn't be read"}") }
            } finally {
                flags.update { it.copy(addingPhoto = false) }
            }
        }
    }

    fun dismissPhotoError() = flags.update { it.copy(photoError = null) }

    fun setTitle(title: String) = edit { it.copy(title = title) }
    fun setBody(body: String) = edit { it.copy(body = body) }

    fun add(kind: TermKind, term: String) {
        val clean = term.trim().trimStart('#')
        if (clean.isEmpty()) return
        edit {
            when (kind) {
                TermKind.Category -> if (it.categories.any { c -> c.equals(clean, true) }) it else it.copy(categories = it.categories + clean)
                TermKind.Tag -> if (it.tags.any { c -> c.equals(clean, true) }) it else it.copy(tags = it.tags + clean)
            }
        }
    }

    fun remove(kind: TermKind, term: String) = edit {
        when (kind) {
            TermKind.Category -> it.copy(categories = it.categories - term)
            TermKind.Tag -> it.copy(tags = it.tags - term)
        }
    }

    /** Existing terms matching [query], the post's own picks left out. */
    fun suggestions(kind: TermKind, query: String): List<Term> {
        val s = state.value
        val picked = (if (kind == TermKind.Category) s.draft?.categories else s.draft?.tags).orEmpty().map { it.lowercase() }.toSet()
        val all = if (kind == TermKind.Category) s.taxonomy.categories else s.taxonomy.tags
        val q = query.trim().lowercase()
        return all.filter { it.name.lowercase() !in picked && (q.isEmpty() || it.name.lowercase().contains(q)) }
    }

    private val saving = Mutex()

    /**
     * Saves the writer's text. A save, once started, always finishes, so Publish cancelling the
     * autosave can't cut a write short; the lock keeps the two saves from overlapping.
     */
    private suspend fun save() = saving.withLock { withContext(NonCancellable) { saveNow() } }

    private suspend fun saveNow() {
        val mine = text ?: return
        val stored = container.drafts.get(id) ?: return
        if (stored.state != PostState.Draft && stored.state != PostState.Failed) return
        container.drafts.update(stored.copy(title = mine.title, body = mine.body, categories = mine.categories, tags = mine.tags, images = mine.images, updatedAt = System.currentTimeMillis()))
    }

    /** Sends the post to [destination]: the site's `_posts`, or the blog's `_drafts`. */
    fun publish(destination: Destination? = null) {
        viewModelScope.launch {
            saveJob?.cancel()
            save()
            val draft = container.drafts.get(id) ?: return@launch
            if (draft.title.isBlank()) {
                flags.update { it.copy(titleMissing = true) }
                return@launch
            }
            val again = draft.state == PostState.Failed && draft.editingPath == null
            container.drafts.update(
                draft.copy(
                    state = PostState.Queued, error = null, updatedAt = System.currentTimeMillis(),
                    destination = destination ?: draft.destination,
                    blog = draft.blog ?: container.accounts.current()?.blogKey,
                    // A failed post sent again later gets a fresh name and date: the old ones may
                    // be days stale, or taken by now.
                    targetPath = if (again) null else draft.targetPath,
                    publishDate = if (again) null else draft.publishDate,
                ),
            )
            container.schedulePublish(id)
            flags.update { it.copy(closed = true) }
        }
    }

    /** Leaving the editor: saves, and drops a draft that was never written in. */
    fun close() {
        viewModelScope.launch {
            saveJob?.cancel()
            save()
            container.drafts.get(id)?.let { if (it.isEmpty && it.state == PostState.Draft) container.drafts.delete(id) }
            flags.update { it.copy(closed = true) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            saveJob?.cancel()
            container.drafts.delete(id)
            flags.update { it.copy(closed = true) }
        }
    }
}

private fun dataUri(file: File): String {
    val type = when (file.extension) { "png" -> "image/png"; "gif" -> "image/gif"; else -> "image/jpeg" }
    return "data:$type;base64," + android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
}
