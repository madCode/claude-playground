package com.app.jekyllposter.core.jekyll

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownEditsTest {
    /** `[` and `]` mark the selection; `|` a cursor. */
    private fun edit(marked: String): Edit {
        val cursor = marked.indexOf('|')
        if (cursor >= 0) return Edit(marked.removeRange(cursor, cursor + 1), cursor, cursor)
        val start = marked.indexOf('[')
        val end = marked.indexOf(']') - 1
        return Edit(marked.replace("[", "").replace("]", ""), start, end)
    }

    private fun show(e: Edit) = if (e.start == e.end) e.text.substring(0, e.start) + "|" + e.text.substring(e.start)
    else e.text.substring(0, e.start) + "[" + e.selected + "]" + e.text.substring(e.end)

    @Test fun boldWrapsAndAgainUnwraps() {
        val bold = MarkdownEdits.wrap(edit("a [word] here"), "**")
        assertEquals("a **[word]** here", show(bold))
        assertEquals("a [word] here", show(MarkdownEdits.wrap(bold, "**")))
        assertEquals("a **|** b", show(MarkdownEdits.wrap(edit("a | b"), "**")))
    }

    @Test fun headingsAndListsGoOnEachSelectedLine() {
        assertEquals("one\n## t|wo\nthree", show(MarkdownEdits.linePrefix(edit("one\nt|wo\nthree"), "## ")))
        val listed = MarkdownEdits.linePrefix(edit("x\n[a\nb]\ny"), "- ")
        assertEquals("x\n- a\n- b\ny", listed.text)
        assertEquals("x\na\nb\ny", MarkdownEdits.linePrefix(listed, "- ").text)
        assertEquals("> |", show(MarkdownEdits.linePrefix(edit("|"), "> ")))
    }

    @Test fun aSelectionEndingAtALineBreakLeavesTheNextLineAlone() {
        assertEquals("## a\nb", MarkdownEdits.linePrefix(edit("[a\n]b"), "## ").text)
    }

    @Test fun aPhotoGoesAfterTheSelectionNotOverIt() {
        assertEquals("keep these words\n\n![](x)\n\n|", show(MarkdownEdits.insertBlock(edit("keep [these words]"), "![](x)")))
    }

    @Test fun theCursorStaysOnItsWordWhenALineGetsAPrefix() {
        assertEquals("## ti|tle", show(MarkdownEdits.linePrefix(edit("ti|tle"), "## ")))
    }

    @Test fun linksTakeTheSelectionAsTextOrAddress() {
        assertEquals("see [the docs](|)", show(MarkdownEdits.link(edit("see [the docs]"))))
        assertEquals("see [|](https://example.com)", show(MarkdownEdits.link(edit("see [https://example.com]"))))
    }

    @Test fun aPhotoGetsAParagraphOfItsOwn() {
        assertEquals("Before.\n\n![](x)\n\n|After.", show(MarkdownEdits.insertBlock(edit("Before. |After."), "![](x)")))
        assertEquals("![](x)\n\n|", show(MarkdownEdits.insertBlock(edit("|"), "![](x)")))
        assertEquals("Para.\n\n![](x)\n\n|Next.", show(MarkdownEdits.insertBlock(edit("Para.\n\n|Next."), "![](x)")))
    }

    @Test fun aPostLinkStartsAtTheCursorAndSearchesForTheSelection() {
        assertEquals(Edit("As in [[", 8, 8), MarkdownEdits.postLink(Edit("As in ", 6, 6)))
        assertEquals(Edit("As in [[April", 13, 13), MarkdownEdits.postLink(Edit("As in April", 6, 11)))
        // A selection over lines isn't a title: [[ goes after it.
        assertEquals(Edit("a\nb[[", 5, 5), MarkdownEdits.postLink(Edit("a\nb", 0, 3)))
    }
}
