package com.app.jekyllposter.core.jekyll

/** Small edits to `_config.yml` that leave the rest of it as the writer wrote it. */
object ConfigEdit {
    /** A top-level `timezone:` line: unindented, so a nested key of the same name isn't it. */
    private val timezoneLine = Regex("""(?m)^timezone[ \t]*:.*$""")

    /**
     * [config] with its top-level `timezone` set to [zone]: the existing line replaced (the last,
     * if there are several, as YAML reads the last), or a line added at the end.
     */
    fun withTimezone(config: String?, zone: String): String {
        val text = config.orEmpty()
        val line = "timezone: $zone"
        val last = timezoneLine.findAll(text).lastOrNull()
        if (last != null) return text.replaceRange(last.range, line)
        val joined = if (text.isEmpty() || text.endsWith("\n")) text else "$text\n"
        return "$joined$line\n"
    }
}
