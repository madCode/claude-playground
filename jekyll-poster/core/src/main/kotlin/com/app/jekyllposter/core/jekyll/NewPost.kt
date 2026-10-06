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
    // A line of its own `---` or `...` would end the front matter there and lose what follows.
    if (yaml.lines().any { Regex("""^(---|\.\.\.)(\s|$)""").containsMatchIn(it) }) return "Take out the `---` line: the app writes those itself."
    // Only comments: nothing to write, nothing to break.
    if (yaml.lines().all { it.isBlank() || it.trimStart().startsWith("#") }) return null
    val doc = FrontMatterDocument.parse("---\n$yaml\n---\n")
    if (!doc.readable || doc.values().isEmpty()) return "This isn't YAML the blog can read, like `image: /assets/cover.jpg`."
    // Every key on a line of its own at the start, as front matter is written; indented, `{…}` or
    // `?` keys read differently to YAML than to the line editor that keeps the rest of the post.
    if (!doc.keysAgree()) return "Write one `key: value` per line, starting at the left edge."
    // `<<` merges another map's keys in, as Ruby's YAML reads it: a way round the check below.
    if ("<<" in doc.values().keys) return "Merge keys (`<<`) aren't allowed here."
    val clash = doc.values().keys.firstOrNull { it in PostWriter.MANAGED }
    return when (clash) {
        null -> null
        "date" -> "`date` is set when the post is published; take it out of here."
        "layout" -> "`layout` comes from your _config.yml; take it out of here."
        else -> "`$clash` has its own place in the editor; take it out of here."
    }
}

object PostWriter {
    /** Keys the editor writes itself; the rest is the writer's "more front matter". */
    val MANAGED = setOf("layout", "title", "date", "categories", "category", "tags", "tag")

    /** Jekyll's own timestamp shape, e.g. `2026-10-04 08:15:00 -0700`. */
    private val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")

    fun timestamp(at: ZonedDateTime): String = at.format(timestamp)

    /**
     * The `date:` for a post published [at]: Jekyll's timestamp, or with [dayOnly] just the day,
     * which says neither the time it went out nor the writer's time zone. Jekyll reads a bare
     * day as midnight in the site's time zone.
     */
    fun date(at: ZonedDateTime, dayOnly: Boolean): String = if (dayOnly) at.toLocalDate().toString() else timestamp(at)

    /**
     * The file for a new post. The date carries the phone's offset, so the post lands on the day
     * the writer sees whatever time zone the site builds in. Without an offset GitHub's build
     * reads it as UTC, and an evening post can be dated tomorrow, which Jekyll then hides as a
     * future post.
     */
    fun newPost(content: PostContent, at: ZonedDateTime, config: SiteConfig, dayOnly: Boolean = false): FrontMatterDocument =
        create(content, config, date = date(at, dayOnly))

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
