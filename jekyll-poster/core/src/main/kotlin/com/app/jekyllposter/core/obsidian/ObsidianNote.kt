package com.app.jekyllposter.core.obsidian

import com.app.jekyllposter.core.frontmatter.FrontMatterDocument
import com.app.jekyllposter.core.jekyll.PostPath
import com.app.jekyllposter.core.jekyll.PostWriter
import com.app.jekyllposter.core.jekyll.Slug

/**
 * An Obsidian note turned into what a Jekyll post needs, the way obyde does it: `[[links]]` to
 * the blog's posts become `post_url` links, obyde's `find:`/`replace:` rules are applied and
 * removed, and `![[photo.jpg]]` embeds are listed for the app to fetch from the vault.
 */
object ObsidianNote {
    /** A post a `[[link]]` can point to: one Jekyll builds, so `post_url` finds it. */
    data class LinkTarget(val path: String, val title: String)

    /** An image embed as written (`![[cat.jpg|A cat]]`), the file it names, and its alt text. */
    data class Embed(val raw: String, val name: String, val alt: String)

    sealed interface Result {
        data class Converted(
            val title: String,
            val body: String,
            val categories: List<String>,
            val tags: List<String>,
            /** Front matter beyond the editor's fields, as YAML; null with none. */
            val extra: String?,
            val embeds: List<Embed>,
        ) : Result

        /** The note can't be taken as it is; [message] says why, for the writer. */
        data class Problem(val message: String) : Result
    }

    /**
     * Keys dropped from a note: obyde's rules, Obsidian's own, and `date`, which the app sets when
     * the post is published.
     */
    private val dropped = setOf("find", "replace", "aliases", "alias", "cssclasses", "cssclass", "date", "layout")

    private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "avif", "bmp", "svg", "heic")

    /**
     * Converts [text]. [fileName] is the note's file name when it came as a file: Obsidian titles
     * a note by it. [posts] are the blog's posts a link may resolve to.
     */
    fun convert(text: String, fileName: String?, posts: List<LinkTarget>): Result {
        var doc = FrontMatterDocument.parse(text)
        if (doc.hasFrontMatter && !doc.readable) {
            // Unread, its find: rules couldn't be applied, and the words they hide would go out.
            return Result.Problem("The note's front matter isn't YAML the app can read, so it wasn't added.")
        }
        val values = doc.values()
        val rules = when (val r = rules(values["find"], values["replace"])) {
            is Rules.Ok -> r.rules
            is Rules.Bad -> return Result.Problem(r.message)
        }
        // Out before the rules run: they hold the very words meant to stay private.
        doc.set("find", null)
        doc.set("replace", null)
        if (rules.isNotEmpty()) {
            doc = FrontMatterDocument.parse(rules.scrub(doc.render()))
            if (doc.hasFrontMatter && !doc.readable) {
                return Result.Problem("After its find/replace rules, the note's front matter isn't YAML the app can read, so it wasn't added.")
            }
        }
        var body = doc.text
        var title = doc.string("title")?.trim().orEmpty()
            .ifEmpty { fileName?.substringAfterLast('/')?.substringBeforeLast('.')?.let { rules.scrub(it) }?.trim().orEmpty() }
        // A heading on the first line repeats the title, or is the only title there is.
        heading.find(body)?.let { m ->
            val text = m.groupValues[1].trim()
            if (title.isEmpty() || text.equals(title, ignoreCase = true)) {
                if (title.isEmpty()) title = text
                body = body.substring(m.range.last + 1).trimStart('\n')
            }
        }
        val categories = doc.terms("category", "categories").map { it.removePrefix("#") }.filter { it.isNotEmpty() }
        val tags = doc.terms("tag", "tags").map { it.removePrefix("#") }.filter { it.isNotEmpty() }
        dropped.forEach { doc.set(it, null) }
        val extra = doc.others(PostWriter.MANAGED).takeIf { it.isNotBlank() }
        val embeds = mutableListOf<Embed>()
        body = outsideCode(body) { segment -> links(segment, posts, embeds) }
        return Result.Converted(title, body, categories, tags, extra, embeds)
    }

    /**
     * Which of [files] (paths relative to the vault) an embed of [name] means, as Obsidian
     * resolves it: a path names that file; a bare name, the file of that name anywhere in the
     * vault, the shallowest first.
     */
    fun resolve(name: String, files: List<String>): String? {
        val wanted = name.trim().trimStart('/')
        files.firstOrNull { it == wanted }?.let { return it }
        val candidates = files.filter { it.substringAfterLast('/') == wanted.substringAfterLast('/') && it.endsWith(wanted) }
            .ifEmpty { files.filter { it.substringAfterLast('/').equals(wanted.substringAfterLast('/'), ignoreCase = true) } }
        return candidates.minByOrNull { it.count { c -> c == '/' } }
    }

    /** [body] with the embed [raw] replaced by [markdown], on a paragraph of its own as written. */
    fun replaceEmbed(body: String, raw: String, markdown: String): String = body.replaceFirst(raw, markdown)

    private val heading = Regex("""\A\s*#\s+(.+?)\s*#*\s*(\n|\z)""")
    private val wikilink = Regex("""(!?)\[\[([^\[\]\n]+?)]]""")
    private val fence = Regex("""^\s{0,3}(`{3,}|~{3,})""")
    private val inlineCode = Regex("""(`+)[\s\S]*?\1""")

    private fun links(text: String, posts: List<LinkTarget>, embeds: MutableList<Embed>): String = wikilink.replace(text) { m ->
        val inner = m.groupValues[2]
        val target = inner.substringBefore('|').trim()
        val alias = inner.substringAfter('|', "").trim()
        if (m.groupValues[1] == "!") {
            // Only images: an embedded note or PDF has no Jekyll equivalent, so it stays as written.
            if (target.substringAfterLast('.').lowercase() in imageExtensions) {
                // `|300` or `|300x200` is a display size, not alt text.
                embeds += Embed(m.value, target, alias.takeUnless { Regex("""\d+(x\d+)?""").matches(it) }.orEmpty())
            }
            return@replace m.value
        }
        val post = find(target.substringBefore('#').trim(), posts) ?: return@replace m.value
        val shown = alias.ifEmpty { target.substringBefore('#').trim().ifEmpty { post.title } }
        "[${shown.replace("[", "\\[").replace("]", "\\]")}]({% post_url ${postUrlName(post.path)} %})"
    }

    private fun find(target: String, posts: List<LinkTarget>): LinkTarget? {
        if (target.isEmpty()) return null
        val slug = Slug.of(target)
        val matches = posts.filter { p ->
            val path = PostPath(p.path)
            p.title.trim().equals(target, ignoreCase = true) ||
                path.fileName.substringBeforeLast('.') == target ||
                (slug.isNotEmpty() && path.slug == slug)
        }
        // A title match beats a slug match; among equals, the newest post.
        return matches.sortedWith(compareByDescending<LinkTarget> { it.title.trim().equals(target, ignoreCase = true) }
            .thenByDescending { PostPath(it.path).date }).firstOrNull()
    }

    /**
     * The name `post_url` knows a post by: its file name without the extension, with the folders
     * inside `_posts` (`2025/2025-01-12-welcome`), which Jekyll otherwise warns about.
     */
    internal fun postUrlName(path: String): String {
        val inside = path.substringAfterLast("_posts/")
        return inside.substringBeforeLast('.')
    }

    /** Applies [change] to the parts of Markdown [text] outside fenced and inline code. */
    private fun outsideCode(text: String, change: (String) -> String): String {
        val out = StringBuilder()
        val prose = StringBuilder()
        fun flush() {
            if (prose.isEmpty()) return
            var last = 0
            inlineCode.findAll(prose).forEach { m ->
                out.append(change(prose.substring(last, m.range.first))).append(m.value)
                last = m.range.last + 1
            }
            out.append(change(prose.substring(last)))
            prose.clear()
        }
        var open: String? = null
        text.split("\n").forEachIndexed { i, line ->
            val piece = if (i == 0) line else "\n$line"
            val marker = fence.find(line)?.groupValues?.get(1)
            when {
                open == null && marker != null -> { flush(); open = marker; out.append(piece) }
                open != null -> {
                    out.append(piece)
                    if (marker != null && marker[0] == open!![0] && marker.length >= open!!.length && line.trim() == marker) open = null
                }
                else -> prose.append(piece)
            }
        }
        flush()
        return out.toString()
    }

    private sealed interface Rules {
        data class Ok(val rules: List<Pair<Regex, String>>) : Rules
        data class Bad(val message: String) : Rules
    }

    private fun rules(find: Any?, replace: Any?): Rules {
        fun strings(v: Any?): List<String>? = when (v) {
            null -> emptyList()
            is List<*> -> v.map { it?.toString() ?: "" }
            is String, is Number, is Boolean -> listOf(v.toString())
            else -> null
        }
        val finds = strings(find)
        val replaces = strings(replace)
        if (finds == null || replaces == null) return Rules.Bad("The note's find: and replace: must be lists, so they weren't applied and the note wasn't added.")
        if (finds.size != replaces.size) {
            return Rules.Bad("The note has ${finds.size} find: and ${replaces.size} replace: entries; they go in pairs, so the note wasn't added.")
        }
        val compiled = finds.mapIndexed { i, pattern ->
            val regex = runCatching { Regex(pythonPattern(pattern)) }.getOrNull()
                ?: return Rules.Bad("find: \"$pattern\" isn't a pattern the app can read, so the note wasn't added.")
            regex to replaces[i]
        }
        return Rules.Ok(compiled)
    }

    private fun List<Pair<Regex, String>>.scrub(text: String): String =
        fold(text) { acc, (regex, replacement) -> regex.replace(acc) { m -> pythonReplacement(replacement, m) } }

    /** Python's named groups, as obyde's rules are written, in Java's spelling. */
    private fun pythonPattern(pattern: String): String =
        pattern.replace("(?P<", "(?<").replace(Regex("""\(\?P=(\w+)\)""")) { "\\k<${it.groupValues[1]}>" }

    /**
     * [template] expanded as Python's `re.sub` does: `\1` and `\g<name>` are groups, `\n` a new
     * line. Done by hand because Java's `$` syntax would misread a dollar sign in the text.
     */
    private fun pythonReplacement(template: String, m: MatchResult): String = buildString {
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c != '\\' || i + 1 == template.length) { append(c); i++; continue }
            val next = template[i + 1]
            when {
                next.isDigit() -> {
                    var end = i + 2
                    if (end < template.length && template[end].isDigit()) end++
                    append(m.groups[template.substring(i + 1, end).toInt()]?.value.orEmpty())
                    i = end
                }
                next == 'g' && template.getOrNull(i + 2) == '<' && template.indexOf('>', i + 3) > 0 -> {
                    val end = template.indexOf('>', i + 3)
                    val group = template.substring(i + 3, end)
                    append((group.toIntOrNull()?.let { m.groups[it] } ?: runCatching { m.groups[group] }.getOrNull())?.value.orEmpty())
                    i = end + 1
                }
                else -> {
                    append(when (next) { 'n' -> '\n'; 't' -> '\t'; '\\' -> '\\'; else -> "\\$next" })
                    i += 2
                }
            }
        }
    }
}
