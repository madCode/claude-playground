package com.app.jekyllposter.core.jekyll

import com.app.jekyllposter.core.frontmatter.FrontMatterDocument
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** What the writer fills in. */
data class PostContent(
    val title: String,
    val body: String,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    /**
     * Front matter beyond what the editor has fields for (`image:`, `excerpt:`, …), as YAML.
     * Null leaves an existing post's other keys exactly as they are.
     */
    val extra: String? = null,
)

/** Why [yaml] can't be the "more front matter" of a post, or null if it can. */
fun extraFrontMatterProblem(yaml: String): String? {
    if (yaml.isBlank()) return null
    val doc = FrontMatterDocument.parse("---\n$yaml\n---\n")
    if (!doc.readable || doc.values().isEmpty()) return "This isn't YAML the blog can read, like `image: /assets/cover.jpg`."
    val clash = doc.keys.firstOrNull { it in PostWriter.MANAGED }
    if (clash != null) return "`$clash` has its own place in the editor; take it out of here."
    return null
}

object PostWriter {
    /** Keys the editor writes itself; the rest is the writer's "more front matter". */
    val MANAGED = setOf("layout", "title", "date", "categories", "category", "tags", "tag")

    /** Jekyll's own timestamp shape, e.g. `2026-10-04 08:15:00 -0700`. */
    private val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")

    fun timestamp(at: ZonedDateTime): String = at.format(timestamp)

    /**
     * The file for a new post. The date carries the phone's offset, so the post lands on the day
     * the writer sees whatever time zone the site builds in. Without an offset GitHub's build
     * reads it as UTC, and an evening post can be dated tomorrow, which Jekyll then hides as a
     * future post.
     */
    fun newPost(content: PostContent, at: ZonedDateTime, config: SiteConfig): FrontMatterDocument =
        create(content, config, date = at.format(timestamp))

    /** A new draft: no date, since Jekyll dates drafts by their file's modification time. */
    fun newDraft(content: PostContent, config: SiteConfig): FrontMatterDocument =
        create(content, config, date = null)

    /** An existing post with the writer's changes; every key the app doesn't edit stays as it was. */
    fun edit(existing: FrontMatterDocument, content: PostContent): FrontMatterDocument {
        val doc = existing.withBody(content.body)
        doc.set("title", content.title)
        // A post may spell it `category:`; writing `categories:` too would double up.
        if (doc.keys.contains("category") && content.categories.size <= 1) {
            doc.set("category", content.categories.firstOrNull())
        } else {
            doc.set("category", null)
            doc.set("categories", content.categories)
        }
        if (doc.keys.contains("tag") && content.tags.size <= 1) {
            doc.set("tag", content.tags.firstOrNull())
        } else {
            doc.set("tag", null)
            doc.set("tags", content.tags)
        }
        // Only when the writer changed it: untouched, the other keys stay byte for byte.
        if (content.extra != null && content.extra.trim() != doc.others(MANAGED).trim()) doc.replaceOthers(content.extra, MANAGED)
        return doc
    }

    private fun create(content: PostContent, config: SiteConfig, date: String?): FrontMatterDocument {
        val doc = FrontMatterDocument.empty(body = "\n" + content.body)
        // Themes without a posts default (minima's own starter has none) render a bare page otherwise.
        if (config.defaultPostLayout == null) doc.set("layout", "post")
        doc.set("title", content.title)
        if (date != null) doc.setRaw("date", date)
        doc.set("categories", content.categories)
        doc.set("tags", content.tags)
        content.extra?.takeIf { it.isNotBlank() }?.let { doc.replaceOthers(it, MANAGED) }
        return doc
    }
}
