package com.app.jekyllposter.core.jekyll

import com.app.jekyllposter.core.frontmatter.FrontMatterDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class JekyllTest {
    @Test fun slugs() {
        assertEquals("sourdough-again-a-72-loaf", Slug.of("Sourdough, again: a 72% loaf!"))
        assertEquals("cafe-creme", Slug.of("Café crème"))
        assertEquals("привет-мир", Slug.of("Привет, мир"))
        assertEquals("", Slug.of("!!!"))
        val long = Slug.of("a fairly long title that goes on and on well past where any url should end")
        assertTrue(long.length <= 60)
        assertFalse(long.endsWith("-"))
    }

    @Test fun postPaths() {
        val nested = PostPath("travel/_posts/2025/2025-06-08-coastal-walk.md")
        assertTrue(nested.isPost)
        assertEquals(listOf("travel"), nested.folderCategories)
        assertEquals(LocalDate.of(2025, 6, 8), nested.date)
        assertEquals("coastal-walk", nested.slug)
        assertTrue(PostPath("_drafts/idea.md").isPost)
        assertTrue(PostPath("_drafts/idea.md").isDraft)
        assertFalse(PostPath("_posts/notes.md").isPost) // Jekyll skips undated posts
        assertFalse(PostPath("_posts/2025-01-01-pic.jpg").isPost)
        assertFalse(PostPath("about.md").isPost)
        assertFalse(PostPath("_posts/.hidden/2025-01-01-x.md").isPost)
        assertEquals("_posts/2026-10-04-hello.md", PostPath.newPost(LocalDate.of(2026, 10, 4), "hello").path)
    }

    @Test fun siteConfig() {
        val config = SiteConfig.parse(
            """
            title: Notes
            url: https://example.com/
            baseurl: /blog/
            timezone: Europe/Paris
            defaults:
              - scope: { path: "", type: posts }
                values: { layout: article }
            """.trimIndent(),
        )
        assertEquals("https://example.com", config.url)
        assertEquals("/blog", config.baseurl)
        assertEquals(ZoneId.of("Europe/Paris"), config.timezone)
        assertEquals("article", config.defaultPostLayout)
        assertNull(SiteConfig.parse("timezone: Not/AZone").timezone)
        assertEquals(SiteConfig(), SiteConfig.parse(null))
    }

    @Test fun summaryTakesCategoriesFromFoldersAndBothKeys() {
        val s = PostSummary.of(PostPath("travel/_posts/2025-06-08-coastal-walk.md"), "x", "---\ncategory: Walks\ntags: [a]\n---\n")
        assertEquals(listOf("travel", "Walks"), s.categories)
        assertEquals("Coastal walk", s.title)
        assertTrue(s.published)
        assertFalse(PostSummary.of(PostPath("_posts/2025-01-01-x.md"), "y", "---\npublished: false\n---\n").published)
    }

    @Test fun taxonomyMergesSpellingsAndSortsByUse() {
        fun post(vararg categories: String) = PostSummary(PostPath("_posts/2025-01-01-x.md"), "s", "t", categories.toList(), emptyList(), true)
        val t = Taxonomy.of(
            listOf(
                post("writing"), post("Writing"), post("writing", "WRITING"), post("cooking"),
                PostSummary(PostPath("_drafts/x.md"), "d", "t", listOf("garden"), emptyList(), true),
            ),
        )
        assertEquals(listOf(Term("writing", 3), Term("cooking", 1)), t.categories)
    }

    @Test fun newPostCarriesTheOffsetAndOnlyNeededKeys() {
        val at = ZonedDateTime.of(2026, 10, 4, 22, 15, 0, 0, ZoneId.of("America/Los_Angeles"))
        val doc = PostWriter.newPost(PostContent("Late night: notes", "Hello.", listOf("writing"), emptyList()), at, SiteConfig(defaultPostLayout = "post"))
        assertEquals(
            "---\ntitle: \"Late night: notes\"\ndate: 2026-10-04 22:15:00 -0700\ncategories: [writing]\n---\n\nHello.\n",
            doc.render(),
        )
        assertTrue(PostWriter.newPost(PostContent("t", "b"), at, SiteConfig()).render().contains("layout: post"))
        assertFalse(PostWriter.newDraft(PostContent("t", "b"), SiteConfig()).render().contains("date:"))
    }

    @Test fun editingKeepsTheWritersKeysAndTheirSpelling() {
        val original = FrontMatterDocument.parse("---\nlayout: post\ntitle: Old\ncategory: Writing\nimage: /a.png\n---\n\nOld body\n")
        val edited = PostWriter.edit(original, PostContent("New", "New body", listOf("Writing"), listOf("books")))
        assertEquals("---\nlayout: post\ntitle: New\ncategory: Writing\nimage: /a.png\ntags: [books]\n---\n\nNew body\n", edited.render())
        val spaced = PostWriter.edit(FrontMatterDocument.parse("---\ncategory: Web Development\n---\n"), PostContent("T", "b", listOf("Web Development")))
        assertEquals("---\ncategory: Web Development\ntitle: T\n---\n\nb\n", spaced.render())
        val two = PostWriter.edit(original, PostContent("New", "b", listOf("Writing", "Books")))
        assertEquals(listOf("Writing", "Books"), two.list("categories"))
        assertFalse(two.keys.contains("category"))
    }
}
