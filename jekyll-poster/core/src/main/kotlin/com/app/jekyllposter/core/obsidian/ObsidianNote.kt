package com.app.jekyllposter.core.obsidian

import com.app.jekyllposter.core.frontmatter.FrontMatterDocument
import com.app.jekyllposter.core.jekyll.PostPath
import com.app.jekyllposter.core.jekyll.PostWriter
import java.time.LocalDate

/**
 * An Obsidian note turned into what a Jekyll post needs, the way obyde does it: `[[links]]` to
 * the blog's posts become `post_url` links, obyde's `find:`/`replace:` rules are applied and
 * removed, and `![[photo.jpg]]` embeds are listed for the app to fetch from the vault.
 */
object ObsidianNote {
    /**
     * A post a `[[link]]` can point to. Only posts the site builds belong here: a `post_url` that
     * Jekyll can't find fails the whole site's build.
     */
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
            /** The note's `date:` as written, for the post to keep; null when it has none. */
            val date: String? = null,
        ) : Result

        /** The note can't be taken as it is; [message] says why, for the writer. */
        data class Problem(val message: String) : Result
    }

    /**
     * Keys dropped from a note: obyde's rules and Obsidian's own, and the ones plugins fill in
     * that say when and where the writer wrote (Map View's `location`), which a public post
     * shouldn't. `date` goes to [Result.Converted.date] rather than the front matter: the app
     * writes it itself, in the file name too.
     */
    private val dropped = setOf(
        "find", "replace", "aliases", "alias", "cssclasses", "cssclass", "date", "layout",
    )

    /** Plugins' keys for when and where a note was written, dropped in any case: they differ (`Created:`). */
    private val written = setOf("created", "modified", "updated", "date created", "date modified", "date updated", "location", "coordinates")

    private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "avif", "bmp", "svg", "heic")

    /**
     * Converts [text]. [fileName] is the note's file name when it came as a file: Obsidian titles
     * a note by it. [posts] are the blog's posts a link may resolve to. [postUrlHasBaseurl] is
     * true for a site built with Jekyll 4, whose `post_url` already starts with the baseurl.
     */
    fun convert(text: String, fileName: String?, posts: List<LinkTarget>, postUrlHasBaseurl: Boolean = false): Result {
        var doc = FrontMatterDocument.parse(text)
        // Unread, its find: rules couldn't be applied, and the words they hide would go out.
        if (doc.hasFrontMatter && !doc.readable) return Result.Problem("The note's front matter isn't YAML the app can read, so it wasn't added.")
        if (!doc.hasFrontMatter && text.trimStart('\uFEFF').startsWith("---") && Regex("""(?m)^(find|replace)\s*:""").containsMatchIn(text)) {
            return Result.Problem("The note's front matter has no closing ---, so its find/replace rules can't be read. It wasn't added.")
        }
        // YAML reads the last of two find: keys; the words in the first would go out unreplaced.
        if (doc.keys.count { it == "find" } > 1 || doc.keys.count { it == "replace" } > 1) {
            return Result.Problem("The note has find: or replace: twice; put each rule in one list. It wasn't added.")
        }
        val values = doc.values()
        val rules = when (val r = rules(values["find"], values["replace"])) {
            is Rules.Ok -> r.rules
            is Rules.Bad -> return Result.Problem(r.message)
        }
        // Out before the rules run: they hold the very words meant to stay private.
        doc.set("find", null)
        doc.set("replace", null)
        // Comments are the writer's notes to themselves (Jekyll would show them): out first, so
        // nothing in them is taken as the title, a photo or a link.
        doc = doc.withBody(withoutComments(doc.text))
        // Named before the rules run, which may change a file's name in the text too.
        val embedNames = embeds(doc.text).map { it.name }
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
        val categories = noteTerms(doc, "category", "categories")
        val tags = noteTerms(doc, "tag", "tags")
        // `Date:` too: Jekyll would ignore it, and the writer meant the post's date.
        val dateKey = doc.keys.firstOrNull { it.lowercase() == "date" }
        val date = dateKey?.let { doc.string(it) }?.trim()?.takeIf { it.isNotEmpty() }
        dateKey?.let { doc.set(it, null) }
        dropped.forEach { doc.set(it, null) }
        doc.keys.filter { it.lowercase() in written }.forEach { doc.set(it, null) }
        val extra = doc.others(PostWriter.MANAGED).takeIf { it.isNotBlank() }
        body = outsideCode(body) { segment -> links(segment, posts, if (postUrlHasBaseurl) "" else "{{ site.baseurl }}") }
        val found = embeds(body)
        // The rules may have renamed a file in the text; the vault still has it by its own name.
        val named = if (found.size == embedNames.size) found.mapIndexed { i, e -> e.copy(name = embedNames[i]) } else found
        return Result.Converted(title, body, categories, tags, extra, named, date)
    }

    /**
     * Categories or tags as a note means them: as Jekyll reads them, except that a plain string
     * under the plural key with a comma in it is split on commas alone, as Obsidian does
     * (`tags: personal-philosophy, jumping` is two tags, `Web Development, Design` two categories),
     * where Jekyll would split on spaces and keep the commas. A YAML list keeps each item whole,
     * comma or not, and so does the singular key: that's how a name with a comma is written. A
     * leading `#`, Obsidian's way of writing a tag, goes.
     */
    private fun noteTerms(doc: FrontMatterDocument, singular: String, plural: String): List<String> {
        val value = doc.values()[plural]
        val terms = if (!doc.values().containsKey(singular) && value is String && value.contains(',')) {
            value.split(',')
        } else {
            doc.terms(singular, plural)
        }
        return terms.map { it.trim().removePrefix("#").trim() }.filter { it.isNotEmpty() }.distinct()
    }

    /** Whether [name] is a file an image embed can name, by its extension. */
    fun isImage(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in imageExtensions

    /**
     * [body] with its `[[Post title]]` links to [posts] made `post_url` links, outside code, as a
     * shared note's are; ones that match no post stay as written. For posts written in the app.
     */
    fun linkPosts(body: String, posts: List<LinkTarget>, postUrlHasBaseurl: Boolean = false): String =
        outsideCode(body) { segment -> links(segment, posts, if (postUrlHasBaseurl) "" else "{{ site.baseurl }}") }

    /**
     * How a link to [post] is written so it finds exactly that post: `[[Title]]`, or by its file
     * name, `[[2025-01-12-welcome|Title]]`, when another post shares the title or the title holds
     * `#` or `|`, which a link reads otherwise. Null for one no link can reach: a title with
     * brackets, or a path `post_url` can't name.
     */
    fun linkTarget(post: LinkTarget, posts: List<LinkTarget>): String? {
        if (postUrlName(post.path) == null || post.title.any { it in "[]\n" } || post.title.isBlank()) return null
        val shared = posts.count { it.title.trim().equals(post.title.trim(), ignoreCase = true) } > 1
        return if (shared || post.title.any { it in "#|" }) "${PostPath(post.path).fileName.substringBeforeLast('.')}|${post.title.trim()}" else post.title.trim()
    }

    /** A `[[` being typed: where it starts in the text, and what's typed after it so far. */
    data class OpenLink(val start: Int, val query: String)

    /**
     * The `[[link` the cursor is in, still being typed, or null: the `[[` on the same line, not
     * closed yet, nor `![[` (an embed). What's typed so far narrows the posts to link.
     */
    fun openLink(text: String, cursor: Int): OpenLink? {
        if (cursor < 2 || cursor > text.length) return null
        val m = Regex("""(?<!!)\[\[([^\[\]\n|#]*)$""").find(text.substring(0, cursor)) ?: return null
        // Inside a link already closed, with text still after the cursor (fixing a typo in it):
        // nothing to complete. A `]]` right at the cursor is a keyboard's pair, and is used.
        val rest = text.substring(cursor).substringBefore('\n')
        if (!rest.startsWith("]]") && rest.contains("]]") && !rest.substringBefore("]]").contains("[[")) return null
        return OpenLink(m.range.first, m.groupValues[1])
    }

    /**
     * [text] with the [link] being typed at [cursor] completed as `[[target]]` (a `]]` already
     * after the cursor is used, not doubled), and where the cursor goes: after the `]]`.
     */
    fun completeLink(text: String, cursor: Int, link: OpenLink, target: String): Pair<String, Int> {
        val end = if (text.startsWith("]]", cursor)) cursor + 2 else cursor
        val written = "[[$target]]"
        return text.substring(0, link.start) + written + text.substring(end) to link.start + written.length
    }

    /** The image embeds in [body], outside code, in order. Embedded notes and PDFs aren't images. */
    fun embeds(body: String): List<Embed> {
        val out = mutableListOf<Embed>()
        outsideCode(body) { segment ->
            wikilink.findAll(segment).filter { it.groupValues[1] == "!" }.forEach { m ->
                val inner = m.groupValues[2]
                val target = inner.substringBefore('|').trim()
                val alias = inner.substringAfter('|', "").trim()
                if (isImage(target)) {
                    // `|300` or `|300x200` is a display size, not alt text.
                    out += Embed(m.value, target, alias.takeUnless { Regex("""\d+(x\d+)?""").matches(it) }.orEmpty())
                }
            }
            segment
        }
        return out
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

    /** [body] with the first [raw] outside code replaced by [markdown]; unchanged if there's none. */
    fun replaceEmbed(body: String, raw: String, markdown: String): String {
        var done = false
        return outsideCode(body) { segment ->
            if (done || !segment.contains(raw)) segment else segment.replaceFirst(raw, markdown).also { done = true }
        }
    }

    private val heading = Regex("""\A\s*#\s+(.+?)\s*#*\s*(\n|\z)""")
    private val wikilink = Regex("""(!?)\[\[([^\[\]\n]+?)]]""")
    private val fence = Regex("""^\s{0,3}(`{3,}|~{3,})""")
    private val blankLine = Regex("""\n[ \t]*\n""")

    /**
     * [text] without its Obsidian comments, `%%…%%`, as Obsidian hides them: all of one, code
     * inside included, and one left open runs to the end. A `%%` inside code (a fence, or an
     * inline span outside any comment) is just text.
     */
    internal fun withoutComments(text: String): String {
        val out = StringBuilder()
        var inComment = false
        var openFence: String? = null
        var i = 0
        while (i < text.length) {
            if (!inComment && (i == 0 || text[i - 1] == '\n')) {
                val end = text.indexOf('\n', i).let { if (it < 0) text.length else it + 1 }
                val line = text.substring(i, end)
                val marker = fence.find(line)?.groupValues?.get(1)
                val open = openFence
                if (open != null || marker != null) {
                    out.append(line)
                    openFence = when {
                        open == null -> marker
                        marker != null && marker[0] == open[0] && marker.length >= open.length && line.trim() == marker -> null
                        else -> open
                    }
                    i = end
                    continue
                }
            }
            when {
                text.startsWith("%%", i) -> { inComment = !inComment; i += 2 }
                inComment -> i++
                text[i] == '`' -> {
                    var n = 0
                    while (i + n < text.length && text[i + n] == '`') n++
                    val run = "`".repeat(n)
                    // A code span ends with its paragraph: a lone backtick (`don`t`) mustn't pair
                    // with one further on and carry a comment out as "code".
                    val paragraph = blankLine.find(text, i + n)?.range?.first ?: text.length
                    val close = text.indexOf(run, i + n).takeIf { it in 0 until paragraph }
                    val stop = if (close == null) i + n else close + n
                    out.append(text, i, stop)
                    i = stop
                }
                else -> { out.append(text[i]); i++ }
            }
        }
        return out.toString()
    }
    private val inlineCode = Regex("""(`+)[\s\S]*?\1""")

    private fun links(text: String, posts: List<LinkTarget>, prefix: String): String = wikilink.replace(text) { m ->
        if (m.groupValues[1] == "!") return@replace m.value
        val inner = m.groupValues[2]
        val target = inner.substringBefore('|').substringBefore('#').trim()
        val alias = inner.substringAfter('|', "").trim()
        val post = find(target, posts) ?: return@replace m.value
        val name = postUrlName(post.path) ?: return@replace m.value
        val shown = alias.ifEmpty { target }
        // post_url leaves out the baseurl on GitHub Pages' Jekyll 3, so a project site needs it added.
        "[${shown.replace("[", "\\[").replace("]", "\\]")}]($prefix{% post_url $name %})"
    }

    /** The post titled [target], or named it (`2025-01-12-welcome`); the newest if several. */
    private fun find(target: String, posts: List<LinkTarget>): LinkTarget? {
        if (target.isEmpty()) return null
        return posts.filter { p ->
            p.title.trim().equals(target, ignoreCase = true) || PostPath(p.path).fileName.substringBeforeLast('.') == target
        }.maxByOrNull { PostPath(it.path).date ?: LocalDate.MIN }
    }

    /**
     * The name `post_url` finds a post by, as Jekyll 3's matcher reads it: the file name without
     * its extension, after the folders above `_posts` (`travel/2025-06-01-lighthouse`) or inside it
     * (`2025/2025-03-03-spring`). Null for a post with folders on both sides, which no name finds.
     */
    internal fun postUrlName(path: String): String? {
        val above = path.substringBeforeLast("_posts/", "")
        val inside = path.substringAfterLast("_posts/").substringBeforeLast('/', "")
        if (above.isNotEmpty() && inside.isNotEmpty()) return null
        val name = path.substringAfterLast('/').substringBeforeLast('.')
        return above + inside.let { if (it.isEmpty()) "" else "$it/" } + name
    }

    /**
     * Applies [change] to the parts of Markdown [text] outside fenced and inline code. Indented
     * code isn't told apart: it can't be, from a nested list, without parsing the whole document.
     */
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
        data class Ok(val rules: List<Rule>) : Rules
        data class Bad(val message: String) : Rules
    }

    /** One find/replace pair; [groups] maps Python's group names to the Java ones in [regex]. */
    private class Rule(val regex: Regex, val replacement: String, val groups: Map<String, String>)

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
        // The rule is named by its number, not its text: the text is the very word to hide.
        val compiled = finds.mapIndexed { i, pattern ->
            val (javaPattern, groups) = pythonPattern(pattern)
            val regex = runCatching { Regex(javaPattern) }.getOrNull()
                ?: return Rules.Bad("Find/replace rule ${i + 1} isn't a pattern the app can read, so the note wasn't added.")
            val count = regex.toPattern().matcher("").groupCount()
            if (!references(replaces[i]).all { ref -> ref.toIntOrNull()?.let { it <= count } ?: (ref in groups) }) {
                return Rules.Bad("Find/replace rule ${i + 1} uses a group its find: doesn't have, so the note wasn't added.")
            }
            Rule(regex, replaces[i], groups)
        }
        return Rules.Ok(compiled)
    }

    private fun List<Rule>.scrub(text: String): String =
        fold(text) { acc, rule -> rule.regex.replace(acc) { m -> pythonReplacement(rule, m) } }

    /**
     * Python's named groups, as obyde's rules are written, in Java's spelling. Java's names allow
     * only letters and digits, so each is renamed (`first_name` → `py0`).
     */
    private fun pythonPattern(pattern: String): Pair<String, Map<String, String>> {
        val names = mutableMapOf<String, String>()
        val renamed = Regex("""\(\?P<(\w+)>""").replace(pattern) { m ->
            val java = names.getOrPut(m.groupValues[1]) { "py${names.size}" }
            "(?<$java>"
        }
        val back = Regex("""\(\?P=(\w+)\)""").replace(renamed) { m -> names[m.groupValues[1]]?.let { "\\k<$it>" } ?: m.value }
        return back to names
    }

    /** The groups a Python replacement refers to: numbers, or names from `\g<name>`. */
    private fun references(template: String): List<String> =
        // An escaped backslash (`\\1`) is matched first, so its digit isn't read as a group.
        Regex("""\\\\|\\(\d{1,2})|\\g<(\w+)>""").findAll(template).filter { it.value != "\\\\" }
            .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()

    /**
     * [Rule.replacement] expanded as Python's `re.sub` does: `\1` and `\g<name>` are groups, `\n`
     * a new line. Done by hand because Java's `$` syntax would misread a dollar sign in the text.
     */
    private fun pythonReplacement(rule: Rule, m: MatchResult): String = buildString {
        val template = rule.replacement
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
                    append((group.toIntOrNull()?.let { m.groups[it] } ?: rule.groups[group]?.let { m.groups[it] })?.value.orEmpty())
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
