package com.app.jekyllposter.core.frontmatter

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

/**
 * A Markdown file split into its YAML front matter and body.
 *
 * Editing keeps every key the app doesn't touch exactly as written (comments, quoting, order), so
 * opening and saving a post from someone's theme never rewrites the parts they hand-tuned. Only the
 * keys set through [set] are re-rendered.
 */
class FrontMatterDocument private constructor(
    private val entries: MutableList<Entry>,
    /** Lines before the first key (comments, blank lines): kept as they are. */
    private val preamble: List<String>,
    val body: String,
    /** False for a file without front matter, which Jekyll copies as-is instead of rendering. */
    val hasFrontMatter: Boolean,
    /** Comments and blank lines after the last key. */
    private val trailing: List<String> = emptyList(),
) {
    /** One top-level key: the comments and blank lines just above it, then its own lines. */
    private class Entry(val key: String, var lines: List<String>, val leading: List<String> = emptyList())

    val keys: List<String> get() = entries.map { it.key }

    /** The parsed values, or an empty map when the YAML can't be read. */
    fun values(): Map<String, Any?> = parseYaml(renderYaml())

    fun string(key: String): String? = when (val v = values()[key]) {
        null -> null
        is String -> v
        else -> v.toString()
    }

    /**
     * A key that Jekyll reads as a list: `categories`, `tags`. A YAML list is taken item by item; a
     * plain string is split on spaces, as Jekyll does (`categories: news tech` is two categories).
     */
    fun list(key: String): List<String> = when (val v = values()[key]) {
        null -> emptyList()
        is List<*> -> v.filterNotNull().map { it.toString().trim() }.filter { it.isNotEmpty() }
        else -> v.toString().split(Regex("""\s+""")).filter { it.isNotEmpty() }
    }

    /**
     * Categories or tags as Jekyll reads them: the singular key (`category:`) wins and is taken
     * whole, so `category: Web Development` is one category; otherwise the plural key, split as in
     * [list].
     */
    fun terms(singular: String, plural: String): List<String> {
        val values = values()
        if (values.containsKey(singular)) {
            return when (val v = values[singular]) {
                null -> emptyList()
                is List<*> -> v.filterNotNull().map { it.toString().trim() }.filter { it.isNotEmpty() }
                else -> listOf(v.toString().trim()).filter { it.isNotEmpty() }
            }
        }
        return list(plural)
    }

    /** False when there are keys but the YAML can't be read, so values would all look missing. */
    val readable: Boolean get() = entries.isEmpty() || values().isNotEmpty()

    /** Sets [key] to [value] (a String, Number, Boolean or List of those); null removes it. */
    fun set(key: String, value: Any?) {
        val existing = entries.indexOfFirst { it.key == key }
        if (value == null || (value is List<*> && value.isEmpty())) {
            if (existing >= 0) entries.removeAt(existing)
            return
        }
        setRaw(key, Yaml.render(value))
    }

    /** Sets [key] to YAML written exactly as given, for values like timestamps that must stay unquoted. */
    fun setRaw(key: String, yamlValue: String) {
        val line = "$key: $yamlValue"
        val existing = entries.indexOfFirst { it.key == key }
        if (existing >= 0) entries[existing].lines = listOf(line) else entries += Entry(key, listOf(line))
    }

    /** A copy with [newBody], separated from the front matter by one blank line. */
    fun withBody(newBody: String): FrontMatterDocument = FrontMatterDocument(
        entries.map { Entry(it.key, it.lines, it.leading) }.toMutableList(), preamble,
        "\n" + newBody.trimStart('\n'), hasFrontMatter = true, trailing,
    )

    /** The body as the writer sees it, without the blank line that follows the front matter. */
    val text: String get() = body.trimStart('\n')

    private fun renderYaml(): String = (preamble + entries.flatMap { it.leading + it.lines } + trailing).joinToString("\n")

    /** The whole file. A document with no keys still gets its `---` lines, so Jekyll renders it. */
    fun render(): String = buildString {
        append("---\n")
        val yaml = renderYaml()
        if (yaml.isNotEmpty()) append(yaml).append('\n')
        append("---\n")
        append(body)
        if (!endsWith("\n")) append('\n')
    }

    companion object {
        // A key is plain text up to the first colon followed by a space (so `og:image` is one key),
        // or a quoted string.
        private val keyLine = Regex("""^("[^"]*"|'[^']*'|[^\s#\-"'][^#]*?)\s*:(\s.*|)$""")

        fun empty(body: String = ""): FrontMatterDocument =
            FrontMatterDocument(mutableListOf(), emptyList(), body, hasFrontMatter = true)

        fun parse(text: String): FrontMatterDocument {
            val normalized = text.removePrefix("\uFEFF").replace("\r\n", "\n")
            val lines = normalized.split("\n")
            if (lines.firstOrNull()?.trimEnd() != "---") {
                return FrontMatterDocument(mutableListOf(), emptyList(), normalized, hasFrontMatter = false)
            }
            val end = (1 until lines.size).firstOrNull { lines[it].trimEnd() == "---" || lines[it].trimEnd() == "..." }
                ?: return FrontMatterDocument(mutableListOf(), emptyList(), normalized, hasFrontMatter = false)
            val yamlLines = lines.subList(1, end)
            val preamble = mutableListOf<String>()
            val entries = mutableListOf<Entry>()
            // Comments and blank lines at column 0 go with the key below them, so replacing the
            // key above doesn't take a comment about the next one with it.
            var pending = mutableListOf<String>()
            for (line in yamlLines) {
                val match = keyLine.matchEntire(line)
                when {
                    match != null -> {
                        val key = match.groupValues[1].trim().trim('"', '\'')
                        if (entries.isEmpty()) {
                            preamble += pending
                            entries += Entry(key, listOf(line))
                        } else {
                            entries += Entry(key, listOf(line), pending)
                        }
                        pending = mutableListOf()
                    }
                    line.isBlank() || line.startsWith("#") -> pending += line
                    entries.isEmpty() -> { preamble += pending; preamble += line; pending = mutableListOf() }
                    // Indented lines and list items at column 0 belong to the key above.
                    else -> { entries.last().lines = entries.last().lines + pending + line; pending = mutableListOf() }
                }
            }
            val body = lines.subList(end + 1, lines.size).joinToString("\n")
            return FrontMatterDocument(entries, preamble, body, hasFrontMatter = true, trailing = pending)
        }

        internal fun parseYaml(yaml: String): Map<String, Any?> = try {
            @Suppress("UNCHECKED_CAST")
            // Duplicate keys are allowed, last one winning, as Ruby's YAML (and so Jekyll) reads them.
            (Load(LoadSettings.builder().setAllowDuplicateKeys(true).build()).loadFromString(yaml) as? Map<String, Any?>) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

/** Renders the few value shapes the app writes, quoting only when YAML would misread plain text. */
internal object Yaml {
    private val plain = Regex("""^[\p{L}\p{N}][\p{L}\p{N} _./+\-]*$""")
    private val special = Regex("""^(?i)(true|false|yes|no|on|off|null|y|n|~)$""")
    private val numeric = Regex("""^[+\-]?(\d[\d_]*\.?\d*([eE][+\-]?\d+)?|\.\d+|0x[0-9a-fA-F]+|0o[0-7]+)$""")
    private val dateLike = Regex("""^\d{4}-\d{2}-\d{2}.*""")

    fun render(value: Any?): String = when (value) {
        null -> "null"
        is Boolean, is Number -> value.toString()
        is List<*> -> value.joinToString(", ", "[", "]") { scalar(it.toString(), inFlow = true) }
        else -> scalar(value.toString(), inFlow = false)
    }

    fun scalar(s: String, inFlow: Boolean): String {
        val needsQuotes = s.isEmpty() || !plain.matches(s) || special.matches(s) || numeric.matches(s) ||
            dateLike.matches(s) || s != s.trim() || (inFlow && s.contains(','))
        if (!needsQuotes) return s
        val escaped = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t")
        return "\"$escaped\""
    }
}
