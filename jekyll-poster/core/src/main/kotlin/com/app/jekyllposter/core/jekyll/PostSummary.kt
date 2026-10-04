package com.app.jekyllposter.core.jekyll

import com.app.jekyllposter.core.frontmatter.FrontMatterDocument

/** What the post list and the pickers need from one post file. */
data class PostSummary(
    val path: PostPath,
    val sha: String,
    val title: String,
    val categories: List<String>,
    val tags: List<String>,
    val published: Boolean,
) {
    companion object {
        fun of(path: PostPath, sha: String, content: String): PostSummary {
            val doc = FrontMatterDocument.parse(content)
            val frontCategories = doc.terms("category", "categories")
            return PostSummary(
                path = path,
                sha = sha,
                title = doc.string("title")?.takeIf { it.isNotBlank() } ?: titleFromSlug(path.slug),
                categories = (path.folderCategories + frontCategories).distinct(),
                tags = doc.terms("tag", "tags"),
                published = doc.values()["published"] != false,
            )
        }

        private fun titleFromSlug(slug: String) =
            slug.split('-').filter { it.isNotEmpty() }.joinToString(" ").replaceFirstChar { it.uppercase() }
    }
}
