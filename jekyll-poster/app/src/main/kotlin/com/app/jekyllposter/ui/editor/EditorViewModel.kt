package com.app.jekyllposter.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.jekyllposter.AppContainer
import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.app.jekyllposter.core.jekyll.Edit
import com.app.jekyllposter.core.jekyll.Images
import com.app.jekyllposter.core.jekyll.MarkdownEdits
import com.app.jekyllposter.core.jekyll.Preview
import com.app.jekyllposter.core.obsidian.ObsidianNote
import com.app.jekyllposter.data.Destination
import com.app.jekyllposter.data.DraftImage
import com.app.jekyllposter.data.chooseVault
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
import kotlinx.coroutines.Dispatchers
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
        /** Photos just added, waiting for the writer to describe them (alt text), first first. */
        val describing: List<String> = emptyList(),
        val addingPhoto: Boolean = false,
        /** Publish was stopped by the front matter; the screen opens it and says why. */
        val frontMatterBlocked: String? = null,
        val photoError: String? = null,
        /** Photos in a shared Obsidian note, waiting for the writer to choose the vault folder. */
        val vaultPhotos: Int = 0,
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

    /** Where the cursor is in the body, for the toolbar and for placing photos. */
    var bodySelection by mutableStateOf(TextRange(0))
        private set

    /**
     * The keyboard's word in progress. Kept with the selection: a field rebuilt without it makes
     * predictive keyboards lose or repeat what's being typed.
     */
    var bodyComposition by mutableStateOf<TextRange?>(null)
        private set
    private val flags = MutableStateFlow(State())
    private var saveJob: Job? = null

    val state: StateFlow<State> = combine(local, container.drafts.watch(id), container.blogs.taxonomy, flags) { mine, stored, taxonomy, f ->
        // The stored row wins for publishing state, which the worker changes; the text is the writer's.
        val draft = when {
            stored == null -> null
            mine == null -> stored
            else -> stored.copy(title = mine.title, body = mine.body, categories = mine.categories, tags = mine.tags, images = mine.images, extraFrontMatter = mine.extraFrontMatter, noteDate = mine.noteDate)
        }
        f.copy(draft = draft, taxonomy = taxonomy)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State())

    /** The draft read from the database; a camera photo arriving first waits for it. */
    private val loading: Job

    init {
        loading = viewModelScope.launch {
            val loaded = container.drafts.get(id)
            // Applied at once, so the screen sees it even if no frame is pending to pick it up.
            Snapshot.withMutableSnapshot { text = loaded }
            loaded?.body?.let { bodySelection = TextRange(it.length) }
            container.sharedPhotos.remove(id)?.forEach(::addPhoto)
            container.sharedEmbeds.remove(id)?.let(::addEmbeds)
        }
    }

    /**
     * Finds a shared note's `![[photo]]` embeds in the Obsidian vault folder, and puts each photo
     * in its place, prepared like any other (no location). Without a folder chosen, they wait for
     * one; one that isn't in the vault stays as written, and the writer is told.
     */
    private fun addEmbeds(embeds: List<ObsidianNote.Embed>) {
        embeds.forEach { embedNames[it.raw] = it.name }
        val previous = photoJob
        photoJob = viewModelScope.launch {
            previous?.join()
            val tree = container.settings.obsidianVault()
            // Shown while the vault is walked too: on a big one that takes a while, and Back waits for it.
            if (tree != null) flags.update { it.copy(addingPhoto = true) }
            val vault = tree?.let { runCatching { container.vaultFiles(it) }.getOrNull() }
            val files = vault?.files
            if (files == null) {
                flags.update {
                    it.copy(
                        addingPhoto = false,
                        vaultPhotos = embeds.size,
                        photoError = if (tree != null) "Couldn't open your Obsidian vault folder. Choose it again." else it.photoError,
                    )
                }
                return@launch
            }
            val missing = mutableListOf<String>()
            try {
                for (embed in embeds) {
                    // Gone from the text meanwhile: the writer took it out.
                    if (!stillThere(embed)) continue
                    val file = ObsidianNote.resolve(embed.name, files.map { it.path })?.let { p -> files.first { it.path == p } }
                    if (file == null) { missing += embed.raw; continue }
                    val prepared = try {
                        container.images.import(file.uri)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        missing += embed.raw
                        continue
                    }
                    val draft = text ?: return@launch
                    val taken = draft.images.map { it.sitePath }.toSet() + container.blogs.paths
                    val sitePath = Images.sitePath(container.blogs.imageFolder.value, LocalDateTime.now().year, "photo", prepared.extension, taken)
                    // Checked again: the writer may have deleted it while the photo was prepared.
                    if (!stillThere(embed)) { prepared.file.delete(); continue }
                    edit { it.copy(images = it.images + DraftImage(sitePath, prepared.file.path), body = ObsidianNote.replaceEmbed(it.body, embed.raw, Images.markdown(sitePath, embed.alt))) }
                    if (embed.alt.isBlank()) flags.update { it.copy(describing = it.describing + sitePath) }
                }
            } finally {
                flags.update { it.copy(addingPhoto = false) }
            }
            if (missing.isNotEmpty()) {
                // Named as the text has them: the vault's own name may hold a word a rule hides.
                val where = if (vault.complete) "in your Obsidian vault folder" else "in the first 50,000 files of your Obsidian vault folder"
                flags.update { it.copy(photoError = "Not found $where, so left as written: ${missing.distinct().joinToString(", ")}") }
            }
        }
    }

    /**
     * The vault file each embed names, by how the text writes it. In memory only: after the app
     * is pushed out of memory, an embed whose name a rule changed isn't found, and says so.
     */
    private val embedNames = mutableMapOf<String, String>()

    private fun stillThere(embed: ObsidianNote.Embed) = text?.body?.let { body -> ObsidianNote.embeds(body).any { it.raw == embed.raw } } == true

    /**
     * The writer chose the vault folder: kept for later notes, and the photos the text embeds are
     * found in it. Read from the text, not remembered: the picker may have pushed the app out of
     * memory, and this is then a new ViewModel.
     */
    fun vaultChosen(tree: Uri) {
        flags.update { it.copy(vaultPhotos = 0) }
        viewModelScope.launch {
            chooseVault(container.context, container.settings, tree)
            loading.join()
            // The note's own file names where known: its rules may have changed them in the text.
            text?.body?.let { body -> addEmbeds(ObsidianNote.embeds(body).map { e -> embedNames[e.raw]?.let { e.copy(name = it) } ?: e }) }
        }
    }

    /** Not now: the embeds stay in the text as written. */
    fun skipVault() = flags.update { it.copy(vaultPhotos = 0) }

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

    /** What the preview loads, fetched without the WebView's telling user agent. */
    val previewFetcher get() = container.previewFetcher

    /** The post as a page, with site images loaded from the live site (or GitHub, before Pages has one). */
    suspend fun previewHtml(dark: Boolean): String = withContext(Dispatchers.IO) { buildPreview(dark) }

    private suspend fun buildPreview(dark: Boolean): String {
        val draft = text ?: return ""
        val account = container.accounts.current()
        val base = account?.siteUrl?.trimEnd('/')
            ?: account?.let { "https://raw.githubusercontent.com/${it.owner}/${it.repo}/${it.branch}" }
            ?: ""
        val local = draft.images.associate { it.sitePath to it.file }
        val preview = Preview(container.blogs.config.value) { path ->
            // A photo not on the site yet is shown from the phone, inline: the preview has no file access.
            local[path]?.let(::File)?.takeIf { it.exists() }?.let(::dataUri) ?: (base + path)
        }
        return preview.page(draft.title, draft.body, dark)
    }

    /** Prepares a picked photo and adds its link to the end of the post. */
    fun addPhoto(uri: Uri) {
        if (text == null || (state.value.draft != null && !state.value.editable)) return
        flags.update { it.copy(addingPhoto = true, photoError = null) }
        // One at a time, in order: several photos shared at once go in as they were picked.
        val previous = photoJob
        photoJob = viewModelScope.launch {
            previous?.join()
            flags.update { it.copy(addingPhoto = true) }
            try {
                val prepared = container.images.import(uri)
                val draft = text ?: return@launch
                val taken = draft.images.map { it.sitePath }.toSet() + container.blogs.paths
                // A placeholder name until publishing names it after the post.
                val sitePath = Images.sitePath(container.blogs.imageFolder.value, LocalDateTime.now().year, "photo", prepared.extension, taken)
                val link = Images.markdown(sitePath, "")
                // At the cursor, on a paragraph of its own; the image list first, so the text never
                // links to a photo the draft doesn't know.
                edit { it.copy(images = it.images + DraftImage(sitePath, prepared.file.path)) }
                format { MarkdownEdits.insertBlock(it, link) }
                flags.update { it.copy(describing = it.describing + sitePath) }
            } catch (e: Exception) {
                flags.update { it.copy(photoError = "Couldn't add that photo: ${e.message ?: "it couldn't be read"}") }
            } finally {
                flags.update { it.copy(addingPhoto = false) }
            }
        }
    }

    /**
     * Where the camera app should write a photo for this post: a new file in the cache, offered
     * to it through the FileProvider. The screen keeps [CameraTarget.path] in its saved state,
     * because the camera app may push this app out of memory and its answer then reaches a new
     * ViewModel. Null if it can't be made.
     */
    fun cameraTarget(): CameraTarget? = runCatching {
        val dir = cameraDir(container.context).apply { mkdirs() }
        val file = File.createTempFile("photo-", ".jpg", dir)
        CameraTarget(file.path, androidx.core.content.FileProvider.getUriForFile(container.context, "${container.context.packageName}.camera", file))
    }.getOrNull()

    /** The camera app came back: adds the photo at [path] if one was taken, then deletes the original. */
    fun photoTaken(path: String, taken: Boolean) {
        val file = File(path)
        if (!taken || !file.exists() || file.length() == 0L) {
            file.delete()
            return
        }
        viewModelScope.launch {
            // After the app was pushed out of memory, the answer reaches a new editor before
            // its draft has loaded.
            try {
                loading.join()
            } catch (e: kotlinx.coroutines.CancellationException) {
                file.delete()
                throw e
            }
            if (text == null || (state.value.draft != null && !state.value.editable)) {
                file.delete()
                flags.update { it.copy(photoError = "The photo wasn't added: this post can't be changed now.") }
                return@launch
            }
            addPhoto(Uri.fromFile(file))
            // The original keeps its EXIF, location included; only the prepared copy stays. In the
            // app's scope, so the delete still happens if the editor closes mid-import.
            val importing = photoJob
            container.appScope.launch { importing?.join(); file.delete() }
        }
    }

    fun cameraNotReady() = flags.update { it.copy(photoError = "Couldn't get a file ready for the camera. Is the phone's storage full?") }

    fun cameraUnavailable(path: String?) {
        path?.let { File(it).delete() }
        flags.update { it.copy(photoError = "No camera app to take a photo with.") }
    }

    /** A photo still being prepared; Publish and Back wait for it, so it isn't lost. */
    private var photoJob: Job? = null

    /** Sets the alt text of the photo being described; blank leaves it empty. */
    fun describe(sitePath: String, alt: String) {
        if (alt.isNotBlank()) edit { it.copy(body = Images.withAlt(it.body, sitePath, alt)) }
        flags.update { it.copy(describing = it.describing - sitePath) }
    }

    fun dismissPhotoError() = flags.update { it.copy(photoError = null) }

    fun setTitle(title: String) = edit { it.copy(title = title) }

    /** Drops a shared note's date: the post is then dated when it's published. */
    fun useThePublishDay() = edit { it.copy(noteDate = null) }

    fun setExtraFrontMatter(yaml: String) {
        // An edit opened before the app kept front matter doesn't know the post's other keys, so
        // writing here would replace keys the writer never saw.
        if (text?.editingPath != null && text?.extraFrontMatter == null) return
        edit { it.copy(extraFrontMatter = yaml) }
    }

    fun frontMatterShown() = flags.update { it.copy(frontMatterBlocked = null) }

    /** Why the "more front matter" can't be published as it is, or null. */
    val extraProblem: String? get() = text?.frontMatterProblem
    fun setBody(value: TextFieldValue) {
        bodySelection = value.selection
        bodyComposition = value.composition
        if (value.text != text?.body) edit { it.copy(body = value.text) }
    }

    fun setBody(body: String) = setBody(TextFieldValue(body, TextRange(body.length)))

    /** Applies a toolbar button to the body at the cursor or selection. */
    fun format(change: (Edit) -> Edit) {
        val body = text?.body ?: return
        val sel = bodySelection
        val result = change(Edit(body, sel.min.coerceIn(0, body.length), sel.max.coerceIn(0, body.length)))
        bodySelection = TextRange(result.start, result.end)
        bodyComposition = null
        edit { it.copy(body = result.text) }
    }

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
        val saved = stored.copy(title = mine.title, body = mine.body, categories = mine.categories, tags = mine.tags, images = mine.images, extraFrontMatter = mine.extraFrontMatter, noteDate = mine.noteDate)
        // Nothing changed: no write, so an edit opened and left alone still reads as unchanged.
        if (saved == stored) return
        container.drafts.update(stored.copy(title = mine.title, body = mine.body, categories = mine.categories, tags = mine.tags, images = mine.images, extraFrontMatter = mine.extraFrontMatter, noteDate = mine.noteDate, updatedAt = System.currentTimeMillis()))
    }

    /** Sends the post to [destination]: the site's `_posts`, or the blog's `_drafts`. */
    fun publish(destination: Destination? = null) {
        viewModelScope.launch {
            photoJob?.join()
            saveJob?.cancel()
            save()
            val draft = container.drafts.get(id) ?: return@launch
            if (draft.title.isBlank()) {
                flags.update { it.copy(titleMissing = true) }
                return@launch
            }
            // Publishing would write YAML the blog can't read: say why instead.
            draft.frontMatterProblem?.let { problem ->
                flags.update { it.copy(frontMatterBlocked = problem) }
                return@launch
            }
            // A failed post that never attempted a commit gets a fresh name and date: the old ones
            // may be days stale. One that did keeps them, so a commit that landed unheard is
            // recognised rather than published twice.
            val again = draft.state == PostState.Failed && draft.editingPath == null && draft.sentShas.isEmpty()
            container.drafts.update(
                draft.copy(
                    state = PostState.Queued, error = null, updatedAt = System.currentTimeMillis(),
                    destination = destination ?: draft.destination,
                    blog = draft.blog ?: container.accounts.current()?.blogKey,
                    // A failed post sent again later gets a fresh name and date: the old ones may
                    // be days stale, or taken by now.
                    // A delete's path marker isn't a name to publish under.
                    targetPath = if (again || draft.destination == Destination.Delete) null else draft.targetPath,
                    publishDate = if (again) null else draft.publishDate,
                ),
            )
            container.schedulePublish(id)
            flags.update { it.copy(closed = true) }
        }
    }

    /**
     * Queues the blog's copy of the post being edited for deletion. The writer's unsent changes
     * go with it; the confirmation says so.
     */
    fun deleteFromBlog() {
        viewModelScope.launch {
            photoJob?.join()
            saveJob?.cancel()
            // Under the save lock: an autosave already writing would otherwise put the row back
            // to Draft after it was queued, and the delete would silently never happen.
            val queued = saving.withLock {
                withContext(NonCancellable) {
                    val draft = container.drafts.get(id) ?: return@withContext false
                    if (draft.editingPath == null || (draft.state != PostState.Draft && draft.state != PostState.Failed)) return@withContext false
                    container.drafts.update(
                        draft.copy(
                            state = PostState.Queued, error = null, destination = Destination.Delete,
                            // A delete sent before keeps its marker: its commit may have landed.
                            targetPath = if (draft.destination == Destination.Delete) draft.targetPath else null,
                            updatedAt = System.currentTimeMillis(), blog = draft.blog ?: container.accounts.current()?.blogKey,
                        ),
                    )
                    true
                }
            }
            if (!queued) return@launch
            container.schedulePublish(id)
            flags.update { it.copy(closed = true) }
        }
    }

    /** Leaving the editor: saves, and drops a draft that was never written in. */
    fun close() {
        viewModelScope.launch {
            photoJob?.join()
            saveJob?.cancel()
            save()
            container.drafts.get(id)?.let { if (it.isEmpty && it.state == PostState.Draft) container.drafts.delete(id) }
            container.drafts.deleteIfUnchangedEdit(id)
            flags.update { it.copy(closed = true) }
        }
    }

    /**
     * The editor gone without Back (the app closed under it, e.g. by the launcher's shortcut):
     * the last keystrokes, still waiting on the autosave's delay, are saved anyway.
     */
    override fun onCleared() {
        if (saveJob?.isActive == true) {
            saveJob?.cancel()
            container.appScope.launch { save() }
        }
    }

    fun delete() {
        viewModelScope.launch {
            saveJob?.cancel()
            photoJob?.cancel()
            container.drafts.get(id)?.images?.forEach { File(it.file).delete() }
            container.drafts.delete(id)
            flags.update { it.copy(closed = true) }
        }
    }
}

private fun dataUri(file: File): String {
    val type = when (file.extension) { "png" -> "image/png"; "gif" -> "image/gif"; else -> "image/jpeg" }
    return "data:$type;base64," + android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
}

/** A file for the camera app to write to: its [path] here, and the [uri] it's offered as. */
data class CameraTarget(val path: String, val uri: Uri)

/** Where camera photos wait to be prepared: the cache, never backed up. */
fun cameraDir(context: android.content.Context) = File(context.cacheDir, "camera")
