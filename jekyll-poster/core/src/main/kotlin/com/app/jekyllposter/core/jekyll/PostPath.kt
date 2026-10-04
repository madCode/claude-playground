package com.app.jekyllposter.core.jekyll

import java.time.LocalDate

/**
 * Where a post lives in the repository. Jekyll reads posts from any `_posts` folder, including ones
 * nested under a category folder (`travel/_posts/…` puts the post in "travel") and dated subfolders
 * (`_posts/2024/…`). Drafts live in `_drafts` and have no date in their name.
 */
data class PostPath(val path: String) {
    val fileName: String get() = path.substringAfterLast('/')

    val isDraft: Boolean get() = segments.contains("_drafts")

    /** True for files Jekyll would render as posts or drafts. */
    val isPost: Boolean
        get() = (segments.contains("_posts") || segments.contains("_drafts")) &&
            extensions.any { fileName.endsWith(it) } && (isDraft || dated.matches(fileName)) &&
            !segments.any { it.startsWith(".") }

    /** Categories Jekyll gives a post from the folders above `_posts`. */
    val folderCategories: List<String>
        get() {
            val index = segments.indexOfFirst { it == "_posts" || it == "_drafts" }
            return if (index <= 0) emptyList() else segments.take(index).filterNot { it.startsWith("_") }
        }

    val date: LocalDate?
        get() = dated.find(fileName)?.let { runCatching { LocalDate.parse(it.groupValues[1]) }.getOrNull() }

    /** The slug part of the file name: `2024-05-01-hello-world.md` → `hello-world`. */
    val slug: String
        get() = fileName.substringBeforeLast('.').let { dated.find(fileName)?.let { m -> it.removePrefix(m.groupValues[1] + "-") } ?: it }

    private val segments: List<String> get() = path.split('/').dropLast(1)

    companion object {
        val extensions = listOf(".md", ".markdown", ".mkdown", ".mkd", ".mkdn", ".html")
        private val dated = Regex("""^(\d{4}-\d{2}-\d{2})-.+""")

        /** A new post's path in the top-level `_posts`, e.g. `_posts/2026-10-04-my-title.md`. */
        fun newPost(date: LocalDate, slug: String) = PostPath("_posts/$date-$slug.md")

        fun newDraft(slug: String) = PostPath("_drafts/$slug.md")
    }
}
