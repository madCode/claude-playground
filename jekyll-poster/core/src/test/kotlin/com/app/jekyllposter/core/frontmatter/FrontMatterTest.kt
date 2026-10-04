package com.app.jekyllposter.core.frontmatter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontMatterTest {
    private val post = """
        ---
        # The theme reads image for the card.
        layout: post
        title: "Sourdough, again: a 72% loaf"
        tags:
          - baking
          - weekend
        image: /assets/img/a.png # hand-tuned
        ---

        Body text.
    """.trimIndent() + "\n"

    @Test fun readsValuesAndBody() {
        val doc = FrontMatterDocument.parse(post)
        assertEquals("Sourdough, again: a 72% loaf", doc.string("title"))
        assertEquals(listOf("baking", "weekend"), doc.list("tags"))
        assertEquals("Body text.\n", doc.text)
    }

    @Test fun aSpaceSeparatedStringIsSeveralTermsAsInJekyll() {
        val doc = FrontMatterDocument.parse("---\ncategories: cooking bread\n---\n")
        assertEquals(listOf("cooking", "bread"), doc.list("categories"))
    }

    @Test fun unchangedDocumentRendersByteForByte() {
        assertEquals(post, FrontMatterDocument.parse(post).render())
    }

    @Test fun settingOneKeyLeavesCommentsQuotingAndOrderAlone() {
        val doc = FrontMatterDocument.parse(post)
        doc.set("tags", listOf("baking"))
        val out = doc.render()
        assertTrue(out.contains("# The theme reads image for the card.\nlayout: post\ntitle: \"Sourdough, again: a 72% loaf\"\ntags: [baking]\nimage: /assets/img/a.png # hand-tuned\n"))
    }

    @Test fun removingAMultiLineKeyRemovesAllItsLines() {
        val doc = FrontMatterDocument.parse(post)
        doc.set("tags", null)
        assertFalse(doc.render().contains("weekend"))
        assertEquals(listOf("layout", "title", "image"), doc.keys)
    }

    @Test fun aCommentStaysWithTheKeyBelowItWhenTheKeyAboveIsReplaced() {
        val doc = FrontMatterDocument.parse("---\ntags:\n  - a\n\n# about the image\nimage: x.png\ndescription: |\n  one\n\n  two\n# the end\n---\n")
        doc.set("tags", listOf("b"))
        assertEquals("---\ntags: [b]\n\n# about the image\nimage: x.png\ndescription: |\n  one\n\n  two\n# the end\n---\n", doc.render())
        assertEquals("one\n\ntwo\n", doc.string("description"))
    }

    @Test fun windowsLineEndingsAndBomAreRead() {
        val doc = FrontMatterDocument.parse("﻿---\r\ntitle: Hi\r\n---\r\nBody\r\n")
        assertEquals("Hi", doc.string("title"))
        assertEquals("Body\n", doc.text)
    }

    @Test fun aFileWithoutFrontMatterKeepsItsTextAsBody() {
        val doc = FrontMatterDocument.parse("Just text\n---\nmore")
        assertFalse(doc.hasFrontMatter)
        assertEquals("Just text\n---\nmore", doc.body)
    }

    @Test fun brokenYamlReadsAsEmptyRatherThanCrashing() {
        val doc = FrontMatterDocument.parse("---\ntitle: [unclosed\n---\nBody")
        assertEquals(null, doc.string("title"))
    }

    @Test fun valuesYamlWouldMisreadAreQuoted() {
        assertEquals("plain words", Yaml.render("plain words"))
        assertEquals("\"yes\"", Yaml.render("yes"))
        assertEquals("\"2024\"", Yaml.render("2024"))
        assertEquals("\"Re: colons\"", Yaml.render("Re: colons"))
        assertEquals("\"say \\\"hi\\\"\"", Yaml.render("say \"hi\""))
        assertEquals("[c++, \"a, b\", \"#hash\"]", Yaml.render(listOf("c++", "a, b", "#hash")))
    }

    @Test fun everyRenderedValueReadsBackAsWritten() {
        val tricky = listOf("yes", "1.5", "Re: x", "[x]", "a'b", "tab\there", "line\nbreak", "émoji 🎉", "-dash", "null", "  padded ")
        val doc = FrontMatterDocument.empty()
        tricky.forEachIndexed { i, s -> doc.set("k$i", s) }
        doc.set("list", tricky)
        val back = FrontMatterDocument.parse(doc.render())
        tricky.forEachIndexed { i, s -> assertEquals(s, back.string("k$i")) }
        assertEquals(tricky, back.values()["list"])
    }
}
