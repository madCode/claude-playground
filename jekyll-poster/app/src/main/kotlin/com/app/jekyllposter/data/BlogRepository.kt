package com.app.jekyllposter.data

import com.app.jekyllposter.core.blog.Blog
import com.app.jekyllposter.core.blog.SiteIndex
import com.app.jekyllposter.core.github.GitHubClient
import com.app.jekyllposter.core.jekyll.PostPath
import com.app.jekyllposter.core.jekyll.PostSummary
import com.app.jekyllposter.core.jekyll.SiteConfig
import com.app.jekyllposter.core.jekyll.Taxonomy
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The writer's blogs: each one's posts as last read, and fresh reads from GitHub. What it holds in
 * memory (the site's config, address, image folder and paths) is the open blog's; reading another
 * blog, for a post going out to it, leaves that alone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BlogRepository(
    private val accounts: AccountStore,
    private val posts: PostDao,
    private val clientFor: (Account) -> GitHubClient,
) {
    /** The open blog's posts as last read. */
    val cachedPosts: Flow<List<CachedPost>> = accounts.currentKey.flatMapLatest { key -> key?.let { posts.all(it) } ?: flowOf(emptyList()) }

    /** Categories and tags from the posts as last read, for the editor's pickers. */
    val taxonomy: Flow<Taxonomy> = cachedPosts.map { list -> Taxonomy.of(list.map { it.toSummary() }) }

    /** The open blog. */
    suspend fun blog(): Blog? = accounts.current()?.let { blog(it) }

    fun blog(account: Account) = Blog(clientFor(account), account.owner, account.repo, account.branch)

    private val _config = MutableStateFlow(SiteConfig())

    /** The blog's `_config.yml` as last read; defaults until the first read. */
    val config: StateFlow<SiteConfig> = _config

    private val _siteUrl = MutableStateFlow<String?>(null)

    /** The site's address, as last worked out from the blog. */
    val siteUrl: StateFlow<String?> = _siteUrl

    private val _imageFolder = MutableStateFlow("assets/images")

    /** Where the blog keeps its images, as last read. */
    val imageFolder: StateFlow<String> = _imageFolder

    /** Whether the site's `post_url` already includes the baseurl (Jekyll 4), as last read. */
    @Volatile var postUrlHasBaseurl: Boolean = false
        private set

    /** Every path on the branch as last read, so new files don't take an existing name. */
    @Volatile var paths: Set<String> = emptySet()
        private set

    /** Reads the open blog again; see [refresh]. */
    suspend fun refresh(): SiteIndex? = blog()?.let { refresh(it) }

    /**
     * Reads [blog] again, fetching only posts that changed, and keeps the result: its posts for
     * it, and the rest only if it's the open blog.
     */
    suspend fun refresh(blog: Blog): SiteIndex {
        val key = blog.key
        val known = posts.snapshot(key).associate { it.path to it.toSummary() }
        val index = blog.index(known)
        // Signed out of it while reading: what's kept of a blog goes with its sign-in.
        if (!accounts.has(key)) return index
        val cached = index.posts.map { it.toCached(key) }
        posts.insertAll(cached)
        val gone = posts.snapshot(key).map { it.path } - cached.map { it.path }.toSet()
        // In chunks: older Android's SQLite takes at most 999 values in one statement.
        gone.chunked(500).forEach { posts.deletePaths(key, it) }
        accounts.setTitle(key, index.config.title)
        view.withLock {
            if (accounts.currentKey() == key) {
                _config.value = index.config
                _imageFolder.value = index.imageFolder
                _siteUrl.value = index.siteUrl
                paths = index.paths
                postUrlHasBaseurl = index.postUrlHasBaseurl
            }
        }
        return index
    }

    /**
     * Held while the open blog's details are checked and set, or cleared: a clear after a switch
     * then always comes after any read of the blog left.
     */
    private val view = Mutex()

    /**
     * Forgets the open blog's title, address and image folder. Called after switching or removing
     * the open blog's sign-in, so another blog starts without them.
     */
    suspend fun clear() = view.withLock {
        _config.value = SiteConfig()
        _siteUrl.value = null
        _imageFolder.value = "assets/images"
        paths = emptySet()
        postUrlHasBaseurl = false
    }

    /** Forgets [keys]' posts: their sign-in was removed. */
    suspend fun forget(keys: List<String>) = posts.clear(keys)

    /**
     * Of [posts], those a `[[link]]` can go to, newest first: ones the site builds. GitHub Pages
     * skips drafts, unpublished and future-dated posts, and `post_url` fails on them.
     */
    fun linkable(posts: List<CachedPost>): List<CachedPost> {
        val today = LocalDate.now(config.value.timezone ?: ZoneOffset.UTC)
        return posts.filter { post -> post.published && PostPath(post.path).let { !it.isDraft && (it.date?.let { d -> d <= today } ?: false) } }
            .sortedByDescending { PostPath(it.path).date }
    }

    private fun PostSummary.toCached(blog: String) = CachedPost(blog, path.path, sha, title, categories, tags, published, path.date?.toString())
}

fun CachedPost.toSummary() = PostSummary(PostPath(path), sha, title, categories, tags, published)

/** Which blog this is, as [Account.blogKey] names it. */
val Blog.key: String get() = blogKey(owner, name, branch)
