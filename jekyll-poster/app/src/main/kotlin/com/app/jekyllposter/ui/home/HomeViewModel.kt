package com.app.jekyllposter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.jekyllposter.AppContainer
import com.app.jekyllposter.core.frontmatter.FrontMatterDocument
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.CachedPost
import com.app.jekyllposter.core.jekyll.PostPath
import com.app.jekyllposter.data.Destination
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
        /** The blog's own title, from `_config.yml`. */
        val siteTitle: String? = null,
        /** Posts on the phone: being written, waiting, failed, or published and being watched. */
        val onPhone: List<Draft> = emptyList(),
        val onBlog: List<CachedPost> = emptyList(),
        val refreshing: Boolean = false,
        val error: String? = null,
    )

    private val status = MutableStateFlow(State())

    private val withTitle = combine(status, container.blogs.config) { s, config -> s.copy(siteTitle = config.title) }

    val state: StateFlow<State> = combine(container.accounts.account, container.drafts.all(), container.blogs.cachedPosts, withTitle) { account, drafts, posts, s ->
        // Drafts for another blog wait, hidden, until that blog is signed in again.
        val mine = drafts.filter { it.blog == null || it.blog == account?.blogKey }
        s.copy(account = account, onPhone = mine.filter { it.state != PostState.Published || recent(it) }, onBlog = posts)
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
        viewModelScope.launch { open(container.drafts.insert(Draft(blog = container.accounts.current()?.blogKey))) }
    }

    fun edit(post: CachedPost, open: (Long) -> Unit) {
        viewModelScope.launch { editPost(post)?.let(open) }
    }

    /**
     * A draft that edits a post already on the blog, starting from GitHub's copy. An unfinished
     * edit of the same post is reopened instead, so its changes aren't lost to a second copy.
     */
    suspend fun editPost(post: CachedPost): Long? {
        val account = container.accounts.current() ?: return null
        container.drafts.openEditOf(post.path, account.blogKey)?.let { return it.id }
        // The text and its sha from the branch now, not the list's cache, which may be older.
        val file = try {
            container.blogs.blog(account).file(post.path)
        } catch (e: Exception) {
            status.update { it.copy(error = e.forWriter()) }
            return null
        } ?: run {
            status.update { it.copy(error = "That post isn't on the blog any more.") }
            return null
        }
        val doc = FrontMatterDocument.parse(file.text)
        if (!doc.readable) {
            // Editing would write the title and categories over what the app couldn't read.
            status.update { it.copy(error = "This post's front matter isn't valid YAML, so the app can't edit it safely. Fix it on GitHub first.") }
            return null
        }
        return container.drafts.insert(
            Draft(
                blog = account.blogKey,
                title = doc.string("title") ?: post.title,
                body = doc.text,
                categories = doc.terms("category", "categories"),
                tags = doc.terms("tag", "tags"),
                editingPath = post.path,
                baseSha = file.sha,
                // Updating a Jekyll draft keeps it one; publishing it is a separate choice.
                destination = if (PostPath(post.path).isDraft) Destination.Drafts else Destination.Posts,
            ),
        )
    }

    /** Published posts stay on the phone's list for a day, so the writer sees them go live. */
    private fun recent(draft: Draft) = System.currentTimeMillis() - draft.updatedAt < 24 * 60 * 60 * 1000L
}
