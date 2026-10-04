package com.app.jekyllposter.data

import com.app.jekyllposter.core.blog.Blog
import com.app.jekyllposter.core.blog.SiteIndex
import com.app.jekyllposter.core.github.GitHubClient
import com.app.jekyllposter.core.jekyll.PostPath
import com.app.jekyllposter.core.jekyll.PostSummary
import com.app.jekyllposter.core.jekyll.Taxonomy
import com.app.jekyllposter.core.jekyll.SiteConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** The signed-in blog: its posts as last read, and fresh reads from GitHub. */
class BlogRepository(
    private val accounts: AccountStore,
    private val posts: PostDao,
    private val clientFor: (Account) -> GitHubClient,
) {
    val cachedPosts: Flow<List<CachedPost>> = posts.all()

    /** Categories and tags from the posts as last read, for the editor's pickers. */
    val taxonomy: Flow<Taxonomy> = posts.all().map { list -> Taxonomy.of(list.map { it.toSummary() }) }

    suspend fun blog(): Blog? = accounts.current()?.let { blog(it) }

    fun blog(account: Account) = Blog(clientFor(account), account.owner, account.repo, account.branch)

    /** Reads the blog again, fetching only posts that changed, and keeps the result. */
    private val _config = MutableStateFlow(SiteConfig())

    /** The blog's `_config.yml` as last read; defaults until the first read. */
    val config: StateFlow<SiteConfig> = _config

    private val _siteUrl = MutableStateFlow<String?>(null)

    /** The site's address, as last worked out from the blog. */
    val siteUrl: StateFlow<String?> = _siteUrl

    private val _imageFolder = MutableStateFlow("assets/images")

    /** Where the blog keeps its images, as last read. */
    val imageFolder: StateFlow<String> = _imageFolder

    /** Every path on the branch as last read, so new files don't take an existing name. */
    @Volatile var paths: Set<String> = emptySet()
        private set

    suspend fun refresh(): SiteIndex? {
        val blog = blog() ?: return null
        val known = posts.snapshot().associate { it.path to it.toSummary() }
        val index = blog.index(known)
        val cached = index.posts.map { it.toCached() }
        posts.insertAll(cached)
        val gone = posts.snapshot().map { it.path } - cached.map { it.path }.toSet()
        // In chunks: older Android's SQLite takes at most 999 values in one statement.
        gone.chunked(500).forEach { posts.deletePaths(it) }
        _config.value = index.config
        _imageFolder.value = index.imageFolder
        _siteUrl.value = index.siteUrl
        paths = index.paths
        return index
    }

    suspend fun clear() = posts.clear()

    private fun CachedPost.toSummary() = PostSummary(PostPath(path), sha, title, categories, tags, published)

    private fun PostSummary.toCached() = CachedPost(path.path, sha, title, categories, tags, published, path.date?.toString())
}
