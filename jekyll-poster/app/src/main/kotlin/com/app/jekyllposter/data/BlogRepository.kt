package com.app.jekyllposter.data

import com.app.jekyllposter.core.blog.Blog
import com.app.jekyllposter.core.blog.SiteIndex
import com.app.jekyllposter.core.github.GitHubClient
import com.app.jekyllposter.core.jekyll.PostPath
import com.app.jekyllposter.core.jekyll.PostSummary
import com.app.jekyllposter.core.jekyll.Taxonomy
import kotlinx.coroutines.flow.Flow
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
    suspend fun refresh(): SiteIndex? {
        val blog = blog() ?: return null
        val known = posts.snapshot().associate { it.sha to it.toSummary() }
        val index = blog.index(known)
        val cached = index.posts.map { it.toCached() }
        posts.insertAll(cached)
        posts.keepOnly(cached.map { "${it.sha} ${it.path}" })
        return index
    }

    suspend fun clear() = posts.clear()

    private fun CachedPost.toSummary() = PostSummary(PostPath(path), sha, title, categories, tags, published)

    private fun PostSummary.toCached() = CachedPost(sha, path.path, title, categories, tags, published, path.date?.toString())
}
