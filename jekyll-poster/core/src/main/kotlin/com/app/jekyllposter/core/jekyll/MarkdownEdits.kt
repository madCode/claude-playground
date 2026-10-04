package com.app.jekyllposter.core.jekyll

/** Text with a selection (`start == end` is a cursor), as the editor's toolbar changes it. */
data class Edit(val text: String, val start: Int, val end: Int) {
    init {
        require(start in 0..end && end <= text.length) { "Selection $start..$end outside the text" }
    }

    val selected: String get() = text.substring(start, end)
}

/** The toolbar's Markdown, one function per button; bold, italic, code and the line prefixes are undone by pressing them again. */
object MarkdownEdits {
    /**
     * Wraps the selection in [marker] (`**` bold, `_` italic, `` ` `` code), or unwraps it if
     * it's already wrapped. With nothing selected, leaves the cursor between the markers.
     */
    fun wrap(e: Edit, marker: String): Edit {
        val before = e.text.substring(0, e.start)
        val after = e.text.substring(e.end)
        if (before.endsWith(marker) && after.startsWith(marker)) {
            return Edit(before.dropLast(marker.length) + e.selected + after.drop(marker.length), e.start - marker.length, e.end - marker.length)
        }
        return Edit(before + marker + e.selected + marker + after, e.start + marker.length, e.end + marker.length)
    }

    /**
     * Starts each selected line with [prefix] (`## `, `- `, `> `), or takes it off if every line
     * already has it.
     */
    fun linePrefix(e: Edit, prefix: String): Edit {
        val lineStart = e.text.lastIndexOf('\n', (e.start - 1).coerceAtLeast(0)).let { if (e.start == 0 || it < 0) 0 else it + 1 }
        // A selection ending just after a line break stops at that line, not the next one.
        val last = if (e.end > e.start && e.text[e.end - 1] == '\n') e.end - 1 else e.end
        val lineEnd = e.text.indexOf('\n', last).let { if (it < 0) e.text.length else it }
        val lines = e.text.substring(lineStart, lineEnd).split('\n')
        val removing = lines.all { it.startsWith(prefix) }
        val changed = lines.map { if (removing) it.removePrefix(prefix) else prefix + it }
        val delta = if (removing) -prefix.length else prefix.length
        val text = e.text.substring(0, lineStart) + changed.joinToString("\n") + e.text.substring(lineEnd)
        return Edit(
            text,
            (e.start + delta).coerceIn(lineStart, text.length),
            (e.end + delta * lines.size).coerceIn(lineStart, text.length),
        )
    }

    /**
     * Makes the selection a link. A selected URL becomes the link's address with the cursor in its
     * text; other text becomes the link's text with the cursor where the address goes.
     */
    fun link(e: Edit): Edit {
        val s = e.selected
        val before = e.text.substring(0, e.start)
        val after = e.text.substring(e.end)
        return if (s.startsWith("http://") || s.startsWith("https://")) {
            val inserted = "[]($s)"
            Edit(before + inserted + after, e.start + 1, e.start + 1)
        } else {
            val inserted = "[$s]()"
            val cursor = e.start + s.length + 3
            Edit(before + inserted + after, cursor, cursor)
        }
    }

    /**
     * Puts [block] (a photo's Markdown) on a paragraph of its own at the cursor, or after the
     * selection (never over it: the writer's words aren't lost to a photo), with the cursor after it.
     */
    fun insertBlock(e: Edit, block: String): Edit {
        val before = e.text.substring(0, e.end).trimEnd(' ')
        val after = e.text.substring(e.end).trimStart(' ')
        val lead = when {
            before.isEmpty() || before.endsWith("\n\n") -> ""
            before.endsWith("\n") -> "\n"
            else -> "\n\n"
        }
        val trail = when {
            after.startsWith("\n\n") -> ""
            after.startsWith("\n") -> "\n"
            else -> "\n\n"
        }
        val text = before + lead + block + trail + after
        val cursor = before.length + lead.length + block.length + trail.length
        return Edit(text, cursor, cursor)
    }
}
