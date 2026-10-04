package com.app.jekyllposter.core.blog

import com.app.jekyllposter.core.github.FileChange
import com.app.jekyllposter.core.github.GitHubClient
import com.app.jekyllposter.core.github.TreeEntry
import com.app.jekyllposter.core.jekyll.PostPath
import com.app.jekyllposter.core.jekyll.PostSummary
import com.app.jekyllposter.core.jekyll.SiteConfig
import com.app.jekyllposter.core.jekyll.Taxonomy

/** Everything the app knows about a blog after reading its repository. */
data class SiteIndex(
    val config: SiteConfig,
    val posts: List<PostSummary>,
    val taxonomy: Taxonomy,
    /** Where the blog keeps its images, so new ones go beside them. */
    val imageFolder: String,
    /** Every path on the branch, to keep new files from overwriting old ones. */
    val paths: Set<String>,
)

/** A Jekyll blog in one GitHub repository and branch. */
class Blog(
    private val client: GitHubClient,
    val owner: String,
    val name: String,
    val branch: String,
) {
    /**
     * Reads the blog. [known] holds summaries from an earlier read, by blob sha; only posts that
     * changed since are fetched, so reopening a big blog costs a request or two.
     */
    suspend fun index(known: Map<String, PostSummary> = emptyMap()): SiteIndex {
        val files = client.files(owner, name, branch)
        val configEntry = files.firstOrNull { it.path == "_config.yml" } ?: files.firstOrNull { it.path == "_config.yaml" }
        val postEntries = files.filter { PostPath(it.path).isPost }
        val missing = postEntries.filter { known[it.sha]?.path?.path != it.path }.map { it.sha } +
            listOfNotNull(configEntry?.sha)
        val texts = if (missing.isEmpty()) emptyMap() else client.blobTexts(owner, name, missing)
        val posts = postEntries.mapNotNull { entry ->
            known[entry.sha]?.takeIf { it.path.path == entry.path }
                ?: texts[entry.sha]?.let { PostSummary.of(PostPath(entry.path), entry.sha, it) }
        }
        val config = SiteConfig.parse(configEntry?.let { texts[it.sha] })
        return SiteIndex(
            config = config,
            posts = posts.sortedByDescending { it.path.date },
            taxonomy = Taxonomy.of(posts),
            imageFolder = imageFolder(files),
            paths = files.map { it.path }.toSet(),
        )
    }

    suspend fun read(path: String): String? = client.text(owner, name, branch, path)

    /** Writes the changes as one commit, returning its sha. */
    suspend fun commit(message: String, changes: List<FileChange>): String =
        client.commit(owner, name, branch, message, changes)

    companion object {
        private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "svg")
        private val conventional = listOf("assets/images", "assets/img", "images", "img", "assets")

        /** The folder holding most of the blog's images, or a conventional one if it has none. */
        internal fun imageFolder(files: List<TreeEntry>): String {
            val folders = files
                .filter { it.path.substringAfterLast('.', "").lowercase() in imageExtensions }
                .map { it.path.substringBeforeLast('/', "") }
                // Theme and site chrome (favicons, logos) aren't where posts' pictures go.
                .filterNot { it.isEmpty() || it.startsWith("_") || it.contains("favicon") || it.contains("icons") }
                .map { folder -> conventional.firstOrNull { folder == it || folder.startsWith("$it/") } ?: folder }
            return folders.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "assets/images"
        }
    }
}
