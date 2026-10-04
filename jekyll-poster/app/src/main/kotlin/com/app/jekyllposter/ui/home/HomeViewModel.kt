package com.app.jekyllposter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.jekyllposter.AppContainer
import com.app.jekyllposter.core.frontmatter.FrontMatterDocument
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.CachedPost
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.ui.forWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    data class State(
        val account: Account? = null,
        /** Posts on the phone: being written, waiting, failed, or published and being watched. */
        val onPhone: List<Draft> = emptyList(),
        val onBlog: List<CachedPost> = emptyList(),
        val refreshing: Boolean = false,
        val error: String? = null,
    )

    private val status = MutableStateFlow(State())

    val state: StateFlow<State> = combine(container.accounts.account, container.drafts.all(), container.blogs.cachedPosts, status) { account, drafts, posts, s ->
        s.copy(account = account, onPhone = drafts.filter { it.state != PostState.Published || recent(it) }, onBlog = posts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    init {
        refresh()
    }

    fun refresh() {
        status.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            val error = runCatching { container.blogs.refresh() }.exceptionOrNull()
            status.update { it.copy(refreshing = false, error = error?.forWriter()) }
        }
    }

    fun dismissError() = status.update { it.copy(error = null) }

    /** Opening a post goes through here so [open] runs on the main thread, where navigation must. */
    fun newDraft(open: (Long) -> Unit) {
        viewModelScope.launch { open(container.drafts.insert(Draft())) }
    }

    fun edit(post: CachedPost, open: (Long) -> Unit) {
        viewModelScope.launch { editPost(post)?.let(open) }
    }

    /**
     * A draft that edits a post already on the blog, starting from GitHub's copy. An unfinished
     * edit of the same post is reopened instead, so its changes aren't lost to a second copy.
     */
    suspend fun editPost(post: CachedPost): Long? {
        container.drafts.openEditOf(post.path)?.let { return it.id }
        val blog = container.blogs.blog() ?: return null
        val text = try {
            blog.read(post.path)
        } catch (e: Exception) {
            status.update { it.copy(error = e.forWriter()) }
            return null
        } ?: return null
        val doc = FrontMatterDocument.parse(text)
        return container.drafts.insert(
            Draft(
                title = doc.string("title") ?: post.title,
                body = doc.text,
                categories = doc.list("categories").ifEmpty { doc.list("category") },
                tags = doc.list("tags").ifEmpty { doc.list("tag") },
                editingPath = post.path,
                baseSha = post.sha,
            ),
        )
    }

    fun signOut() {
        viewModelScope.launch {
            container.accounts.signOut()
            container.blogs.clear()
        }
    }

    /** Published posts stay on the phone's list for a day, so the writer sees them go live. */
    private fun recent(draft: Draft) = System.currentTimeMillis() - draft.updatedAt < 24 * 60 * 60 * 1000L
}
