package com.app.jekyllposter.core.jekyll

/** Small edits to `_config.yml` that leave the rest of it as the writer wrote it. */
object ConfigEdit {
    /** A top-level `timezone:` line: unindented, so a nested key of the same name isn't it. */
    private val timezoneLine = Regex("""(?m)^(timezone|"timezone"|'timezone')[ \t]*:[^\r\n]*""")

    /**
     * [config] with its top-level `timezone` set to [zone]: the existing line replaced (the last,
     * if there are several, as YAML reads the last), or a line added at the end.
     */
    fun withTimezone(config: String?, zone: String): String {
        val text = config.orEmpty()
        val line = "timezone: $zone"
        val last = timezoneLine.findAll(text).lastOrNull()
        if (last != null) return text.replaceRange(last.range, line)
        // In the file's own line endings, so a Windows-edited config doesn't end up mixed.
        val eol = if ("\r\n" in text) "\r\n" else "\n"
        val joined = if (text.isEmpty() || text.endsWith("\n")) text else "$text$eol"
        return "$joined$line$eol"
    }
}
