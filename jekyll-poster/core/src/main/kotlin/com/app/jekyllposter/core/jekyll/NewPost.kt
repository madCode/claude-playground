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
)

object PostWriter {
    /** Jekyll's own timestamp shape, e.g. `2026-10-04 08:15:00 -0700`. */
    private val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")

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
        return doc
    }
}
