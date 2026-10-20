package com.app.jekyllposter.core.jekyll

import com.app.jekyllposter.core.frontmatter.FrontMatterDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Editing a post's title, categories and tags leaves every other key as YAML reads it, or isn't done. */
class EditKeepsTheRestTest {
    private fun doc(yaml: String) = FrontMatterDocument.parse("---\n$yaml---\nBody\n")

    /** Front matter as people write it, the plain and the odd. */
    private val shapes = listOf(
        "title: Old\nc#: x\n",
        "title: Old\n? complex key\n: v\n",
        "title: Old\n? |\n  block key\n: v\n",
        "{title: Old, image: a.png}\n",
        "title: Old\nimage: {src: a.png, alt: b}\n",
        "title: Old\nmulti: line one\n  line two\n",
        "title: |\n  Old\n  more\nimage: a.png\n",
        "anchor: &a hello\ntitle: Old\ncopy: *a\n",
        "title: Old\n<<: {image: a.png}\n",
        "title : Old\nimage : a.png\n",
        "\"title\": Old\n'ti''tle2': x\n",
        "title: Old\n2024: year\nnull: nothing\n",
        "title: Old\nog:image: a.png\nurl: http://x.y/z#frag\n",
        "title: Old\ncats:\n- a\n- b\ncategories: [x, y]\n",
        "title: Old\ntitle: Dup\n",
        "title: Old # comment\nimage: a.png #c\n",
        "title: Old\nexcerpt: \"a\n  b\"\n",
        "title: Old\n!!str tagged: v\n",
        "title: Old\n[a, b]: v\n",
        "title: Old\nkey: {a: 1,\nb: 2}\n",
        "category: a\ntag: [x, y]\ntitle: Old\nimage: a.png\n",
        "layout: post\ndate: 2025-01-02 10:00:00 +0000\ntitle: Old\n",
    )

    /** What a writer might change: the title, and the number of categories and tags. */
    private fun changes(d: FrontMatterDocument) = listOf(
        PostContent("A new title: with a colon", "New body", d.terms("category", "categories"), d.terms("tag", "tags")),
        PostContent("Old", "Body", listOf("one", "two"), emptyList()),
        PostContent("Old", "Body", emptyList(), listOf("solo")),
    )

    @Test fun anEditTheAppAllowsKeepsEveryOtherKey() {
        for (shape in shapes) {
            val d = doc(shape)
            if (!PostWriter.canEdit(d)) continue
            val others = d.values().filterKeys { it !in setOf("title", "category", "categories", "tag", "tags") }
            for (content in changes(d)) {
                val after = FrontMatterDocument.parse(PostWriter.edit(doc(shape), content).render())
                assertEquals(shape, others, after.values().filterKeys { it !in setOf("title", "category", "categories", "tag", "tags") })
                assertEquals(shape, content.title, after.string("title"))
                assertEquals(shape, content.categories, after.terms("category", "categories"))
            }
        }
    }

    @Test fun keysTheLineEditorCantSeeStopTheEdit() {
        assertFalse(PostWriter.canEdit(doc("title: Old\n? complex key\n: v\n")))
        assertFalse(PostWriter.canEdit(doc("{title: Old, image: a.png}\n")))
    }

    @Test fun aChangeToMoreFrontMatterIsTheWritersOwn() {
        val existing = doc("layout: post\ntitle: Old\ndate: 2025-01-02\nimage: /a.png\n")
        val content = PostContent("Old", "Body", extra = "image: /b.png\nexcerpt: hi")
        val edited = PostWriter.edit(existing, content)
        assertTrue(PostWriter.keepsTheRest(existing, edited, content))
        assertEquals("/b.png", FrontMatterDocument.parse(edited.render()).string("image"))
    }

    @Test fun binaryValuesAndSelfReferencesNeitherRefuseNorCrash() {
        assertTrue(PostWriter.canEdit(doc("title: Old\nicon: !!binary aGVsbG8=\n")))
        assertFalse(PostWriter.canEdit(doc("title: Old\nr: &r [*r]\n")))
    }

    @Test fun oddButSafeFrontMatterCanStillBeEdited() {
        val editable = shapes.filter { PostWriter.canEdit(doc(it)) }
        // Only the two shapes above, and the block `?` key, are refused.
        assertEquals(shapes.size - 3, editable.size)
        assertTrue(PostWriter.canEdit(doc("title: Old\nkey: {a: 1,\nb: 2}\n")))
    }
}
