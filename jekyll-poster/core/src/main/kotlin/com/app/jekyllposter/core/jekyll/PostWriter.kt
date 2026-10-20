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
        if (extraChanged(existing, content)) doc.replaceOthers(content.extra!!, MANAGED)
        return doc
    }

    private fun extraChanged(existing: FrontMatterDocument, content: PostContent) =
        content.extra != null && content.extra.trim() != existing.others(MANAGED).trim()

    /** Why a post [canEdit] refuses isn't opened. */
    const val CANT_OPEN = "The app can't change this post without changing front matter it doesn't edit: " +
        "it's written in a way the app can't follow, like a `?` key or `{…}` around all of it. Edit it on GitHub instead."

    /** Why an edit [keepsTheRest] refuses isn't published. */
    const val CANT_EDIT = "Publishing this would change front matter the app doesn't edit, which the app can't " +
        "follow (a `?` key, `{…}` around all of it, or an alias). Copy your text, discard this edit, and change the post on GitHub."

    /** The keys [edit] writes; every other key must read the same after it. */
    private val EDITED = setOf("title", "category", "categories", "tag", "tags")

    /** What must still read the same when the writer's "more front matter" replaced the rest. */
    private val KEPT_WHEN_EXTRA_CHANGES = MANAGED - EDITED

    /**
     * Whether [edited], made by [edit] from [existing], keeps what it should as YAML reads it:
     * every key it doesn't write or, when the writer changed the "more front matter" (theirs to
     * change), `layout` and `date`. The line editor doesn't see some YAML (a `?` key, front
     * matter written as one `{…}` map), and writing around it can drop or change keys.
     */
    fun keepsTheRest(existing: FrontMatterDocument, edited: FrontMatterDocument, content: PostContent): Boolean = notCyclic {
        val after = FrontMatterDocument.parse(edited.render())
        if (!after.readable) return@notCyclic false
        val kept: (String) -> Boolean =
            if (extraChanged(existing, content)) { k -> k in KEPT_WHEN_EXTRA_CHANGES } else { k -> k !in EDITED }
        same(after.values().filterKeys(kept), existing.values().filterKeys(kept))
    }

    /** Equal as YAML reads them; `!!binary` values are byte arrays, compared by content. */
    private fun same(a: Any?, b: Any?): Boolean = when {
        a is ByteArray && b is ByteArray -> a.contentEquals(b)
        a is Map<*, *> && b is Map<*, *> -> a.keys == b.keys && a.keys.all { same(a[it], b[it]) }
        a is List<*> && b is List<*> -> a.size == b.size && a.indices.all { same(a[it], b[it]) }
        else -> a == b
    }

    /** Whether the app can edit [doc]'s post and leave what it doesn't edit as it is. */
    fun canEdit(doc: FrontMatterDocument): Boolean = notCyclic {
        val probe = PostContent("Edited: ${doc.string("title").orEmpty()}", doc.text, doc.terms("category", "categories"), doc.terms("tag", "tags"))
        keepsTheRest(doc, edit(doc, probe), probe)
    }

    /**
     * [check], or false when the front matter holds an alias inside itself (`r: &r [[*r]]`): reading
     * or comparing it never ends, and the post can't be edited.
     */
    private inline fun notCyclic(check: () -> Boolean): Boolean = try {
        check()
    } catch (e: StackOverflowError) {
        false
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
