package com.app.jekyllposter.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.jekyllposter.AppContainer
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
            else -> stored.copy(title = mine.title, body = mine.body, categories = mine.categories, tags = mine.tags)
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
        container.drafts.update(stored.copy(title = mine.title, body = mine.body, categories = mine.categories, tags = mine.tags, updatedAt = System.currentTimeMillis()))
    }

    fun publish() {
        viewModelScope.launch {
            saveJob?.cancel()
            save()
            val draft = container.drafts.get(id) ?: return@launch
            if (draft.title.isBlank()) {
                flags.update { it.copy(titleMissing = true) }
                return@launch
            }
            container.drafts.update(draft.copy(state = PostState.Queued, error = null, updatedAt = System.currentTimeMillis()))
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
