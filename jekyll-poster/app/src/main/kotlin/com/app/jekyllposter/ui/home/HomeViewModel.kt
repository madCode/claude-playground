package com.app.jekyllposter.ui.home

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.jekyllposter.AppContainer
import com.app.jekyllposter.PendingShare
import com.app.jekyllposter.Shared
import com.app.jekyllposter.core.frontmatter.FrontMatterDocument
import com.app.jekyllposter.core.io.readAtMost
import com.app.jekyllposter.core.jekyll.PostPath
import com.app.jekyllposter.core.jekyll.PostSummary
import com.app.jekyllposter.core.jekyll.PostWriter
import com.app.jekyllposter.core.jekyll.Taxonomy
import com.app.jekyllposter.core.jekyll.Term
import com.app.jekyllposter.core.obsidian.ObsidianNote
import com.app.jekyllposter.core.text.Tracking
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.CachedPost
import com.app.jekyllposter.data.Destination
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.ui.forWriter
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    data class State(
        val account: Account? = null,
        /** The blog's own title, from `_config.yml`. */
        val siteTitle: String? = null,
        /** Posts on the phone: being written, waiting, failed, or published and being watched. */
        val onPhone: List<Draft> = emptyList(),
        val onBlog: List<CachedPost> = emptyList(),
        val refreshing: Boolean = false,
        /** Shows only the blog's posts in this category; null shows all. */
        val category: String? = null,
        /** The blog's categories, most used first, to filter by. */
        val categories: List<String> = emptyList(),
        /** What the writer is searching for; null when not searching. */
        val query: String? = null,
        val blogHasPosts: Boolean = false,
        val waitingForVpn: Boolean = false,
        val error: String? = null,
    ) {
        val searching: Boolean get() = !query.isNullOrBlank()
    }

    private val status = MutableStateFlow(State())

    private val withTitle = combine(status, container.blogs.config, container.waitingForVpn) { s, config, vpn -> s.copy(siteTitle = config.title, waitingForVpn = vpn) }

    val state: StateFlow<State> = combine(container.accounts.account, container.drafts.all(), container.blogs.cachedPosts, withTitle) { account, drafts, posts, s ->
        // Drafts for another blog wait, hidden, until that blog is signed in again.
        val mine = drafts.filter { it.blog == null || it.blog == account?.blogKey }
        val categories = Taxonomy.of(
            posts.map { PostSummary(PostPath(it.path), it.sha, it.title, it.categories, it.tags, it.published) },
        ).categories.map { it.name }
        // A category gone since it was chosen (renamed, another blog) filters nothing: show all.
        val category = s.category?.takeIf { c -> categories.any { it.equals(c, ignoreCase = true) } }
        val q = s.query?.trim().orEmpty()
        val inCategory = category?.let { c -> posts.filter { post -> post.categories.any { it.equals(c, ignoreCase = true) } } } ?: posts
        val shown = if (q.isEmpty()) inCategory else inCategory.filter { it.matches(q) }
        val listed = mine.filter { (it.state != PostState.Published || recent(it)) && !it.untouched }
        // Search narrows the blog's posts only: the phone's are few, and a failed one must stay in sight.
        s.copy(account = account, onPhone = listed, blogHasPosts = posts.isNotEmpty(), onBlog = shown, categories = categories, category = category)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    init {
        refresh()
        // Posts started and never written in, left when the app closed under the editor (no Back
        // to drop them). Only stale ones: a recent one may be open in another window, or about to
        // receive the last keystrokes of an editor that's closing.
        viewModelScope.launch {
            val day = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            container.drafts.list().filter { it.untouched && it.updatedAt < day }.forEach { container.drafts.delete(it.id) }
        }
    }

    fun refresh() {
        status.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            val error = runCatching { container.blogs.refresh() }.exceptionOrNull()
            status.update { it.copy(refreshing = false, error = error?.forWriter()) }
        }
    }

    /** Filters the blog's posts to [category], or shows them all again when it's already the filter. */
    fun filter(category: String?) = status.update { it.copy(category = if (it.category.equals(category, ignoreCase = true)) null else category) }

    fun dismissError() = status.update { it.copy(error = null) }

    fun startSearch() = status.update { it.copy(query = it.query ?: "") }

    fun search(query: String) = status.update { it.copy(query = query) }

    fun stopSearch() = status.update { it.copy(query = null) }

    /** A post started from a share, for the screen to open; null once opened. */
    val opened = MutableStateFlow<Long?>(null)

    /**
     * Starts a post from what another app shared, outside the screen's own coroutine: a rotation
     * would cancel that and lose the share. Home shows it working meanwhile.
     */
    fun startShared(shared: Shared) {
        status.update { it.copy(refreshing = true) }
        // The app's scope, not this screen's: Switch blog clears Home, and the share mustn't go with it.
        container.appScope.launch {
            try {
                createShared(shared)?.let { opened.value = it }
            } finally {
                status.update { it.copy(refreshing = false) }
            }
        }
    }

    /**
     * The post a share starts; null, with [State.error] saying why, when it can't be taken. Text
     * and notes go through [ObsidianNote], which leaves plain text as it is: a note's `[[links]]`,
     * find/replace rules and front matter are what change.
     */
    suspend fun createShared(shared: Shared): Long? {
        val fromFile = shared.note?.let { uri -> readNote(uri) ?: run {
            status.update { it.copy(error = "Couldn't read the shared note.") }
            return null
        } }
        val text = fromFile?.second ?: shared.text
        // Fresh, if GitHub can be reached: a link to a post deleted since the last look would fail
        // the site's build. Best effort; the cached list does otherwise. Only for a note with links.
        if (text.contains("[[")) withTimeoutOrNull(10_000) { runCatching { container.blogs.refresh() } }
        val today = LocalDate.now(container.blogs.config.value.timezone ?: ZoneOffset.UTC)
        val posts = container.blogs.cachedPosts.first()
            // Only posts the site builds: GitHub Pages skips future-dated ones, and post_url fails on them.
            .filter { it.published && !PostPath(it.path).isDraft && (PostPath(it.path).date?.let { d -> d <= today } ?: false) }
            .map { ObsidianNote.LinkTarget(it.path, it.title) }
        // Off the main thread: a big note or a slow pattern mustn't freeze the screen.
        val converted = withContext(Dispatchers.Default) { ObsidianNote.convert(text, fromFile?.first, posts, container.blogs.postUrlHasBaseurl) }
        val note = when (val result = converted) {
            is ObsidianNote.Result.Problem -> {
                status.update { it.copy(error = result.message) }
                return null
            }
            is ObsidianNote.Result.Converted -> result
        }
        // Before the editor opens, so the writer sees the links as they'll be published.
        val body = if (container.settings.removeTrackingCodes()) Tracking.strip(note.body) else note.body
        val taxonomy = container.blogs.taxonomy.first()
        // The blog's spelling wins, as when a term is picked in the editor.
        fun spelled(terms: List<String>, known: List<Term>) =
            terms.map { t -> known.firstOrNull { it.name.equals(t, ignoreCase = true) }?.name ?: t }.distinctBy { it.lowercase() }
        val id = container.drafts.insert(
            Draft(
                blog = container.accounts.current()?.blogKey, title = note.title, body = body,
                categories = spelled(note.categories, taxonomy.categories), tags = spelled(note.tags, taxonomy.tags),
                extraFrontMatter = note.extra,
                // As written: it's read when publishing, in the site's time zone as it is then.
                noteDate = note.date,
            ),
        )
        container.pendingShares[id] = PendingShare(shared.images, note.embeds)
        return id
    }

    /** A shared file's name and text; null when it can't be read, or is too big to be a note. */
    private suspend fun readNote(uri: Uri): Pair<String?, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = container.context.contentResolver
            val name = runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull() ?: uri.lastPathSegment
            // Read up to one byte past the limit, so a bigger file is told apart without reading it all.
            val bytes = resolver.openInputStream(uri)?.use { it.readAtMost(MAX_NOTE) } ?: return@runCatching null
            if (bytes.size > MAX_NOTE) null else name to bytes.toString(Charsets.UTF_8)
        }.getOrNull()
    }

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
        container.drafts.openEditOf(post.path, account.blogKey)?.let { open ->
            // One never changed (left when the app closed under it) holds nothing of the writer's,
            // only an older copy of the post: GitHub's is fetched instead.
            if (!open.unchangedEdit) return open.id
            container.drafts.deleteIfUnchangedEdit(open.id)
        }
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
        // One clock reading for both: a draft whose times still agree was never changed.
        val now = System.currentTimeMillis()
        return container.drafts.insert(
            Draft(
                createdAt = now,
                updatedAt = now,
                blog = account.blogKey,
                title = doc.string("title") ?: post.title,
                body = doc.text,
                categories = doc.terms("category", "categories"),
                tags = doc.terms("tag", "tags"),
                extraFrontMatter = doc.others(PostWriter.MANAGED),
                extraFrontMatterOpened = doc.others(PostWriter.MANAGED),
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

/** A new post nothing was written in yet: not shown on the list. */
private val Draft.untouched: Boolean get() = (isEmpty && state == PostState.Draft && editingPath == null) || unchangedEdit

/** A post matches a search by its title, or by one of its categories or tags. */
private fun CachedPost.matches(query: String): Boolean =
    title.contains(query, ignoreCase = true) || (categories + tags).any { it.contains(query, ignoreCase = true) }

/** A shared note bigger than this is surely not one: 1 MB of text. */
private const val MAX_NOTE = 1024 * 1024
