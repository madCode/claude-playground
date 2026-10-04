package com.app.jekyllposter.core.jekyll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewTest {
    private val config = SiteConfig(url = "https://writer.github.io", baseurl = "/blog")
    private val preview = Preview(config) { "https://raw.example/main$it" }

    @Test fun liquidLinksResolveAsJekyllWould() {
        assertEquals("/blog/assets/a.jpg", preview.liquid("{{ '/assets/a.jpg' | relative_url }}"))
        assertEquals("https://writer.github.io/blog/about/", preview.liquid("{{\"/about/\" | absolute_url}}"))
        assertEquals("/blog/x and https://writer.github.io", preview.liquid("{{ site.baseurl }}/x and {{site.url}}"))
        assertEquals("{% include note.html %}", preview.liquid("{% include note.html %}"))
    }

    @Test fun siteImagesLoadFromWhereThePreviewCanReachThem() {
        val html = preview.body("![Loaf]({{ '/assets/images/loaf.jpg' | relative_url }}) and ![x](/assets/b.png) and ![y](https://cdn.example/c.png)")
        assertTrue(html, html.contains("src=\"https://raw.example/main/assets/images/loaf.jpg\""))
        assertTrue(html, html.contains("src=\"https://raw.example/main/assets/b.png\""))
        assertTrue(html, html.contains("src=\"https://cdn.example/c.png\""))
    }

    @Test fun tablesStrikethroughAndRawHtml() {
        val html = preview.body("| a | b |\n|---|---|\n| 1 | 2 |\n\n~~gone~~ <mark>kept</mark>")
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("<del>gone</del>"))
        assertTrue(html.contains("<mark>kept</mark>"))
    }

    @Test fun theTitleIsEscapedAndTheThemeFollowsThePhone() {
        val page = preview.page("Fish & <chips>", "Hi", dark = true)
        assertTrue(page.contains("<h1>Fish &amp; &lt;chips&gt;</h1>"))
        assertTrue(page.contains("#1a1110"))
    }
}

class ImagesTest {
    private val at = java.time.LocalDateTime.of(2026, 10, 4, 22, 15, 0)

    @Test fun pathsSitBesideTheBlogsImagesAndAvoidTakenOnes() {
        assertEquals("/assets/images/2026/20261004-221500.jpg", Images.sitePath("assets/images", at, "jpg", emptySet()))
        assertEquals(
            "/img/2026/20261004-221500-2.png",
            Images.sitePath("/img/", at, "png", setOf("img/2026/20261004-221500.png")),
        )
    }

    @Test fun markdownGoesThroughRelativeUrlAndPreviewResolvesIt() {
        val md = Images.markdown("/assets/images/2026/a.jpg", "A [loaf]")
        assertEquals("![A \\[loaf\\]]({{ '/assets/images/2026/a.jpg' | relative_url }})", md)
        val html = Preview(SiteConfig(baseurl = "/blog")) { "local:$it" }.body(md)
        assertTrue(html, html.contains("src=\"local:/assets/images/2026/a.jpg\""))
        assertTrue(Images.isUsed("text $md", "/assets/images/2026/a.jpg"))
    }
}

class PermalinkTest {
    private val evening = java.time.ZonedDateTime.of(2025, 4, 20, 22, 15, 0, 0, java.time.ZoneId.of("America/Los_Angeles"))

    @Test fun theSampleBlogsLivePermalinks() {
        // As GitHub Pages built them for the sample blog (permalink /:categories/:year/:month/:day/:title/).
        val config = SiteConfig(permalink = "/:categories/:year/:month/:day/:title/", timezone = java.time.ZoneId.of("America/Los_Angeles"), baseurl = "/sample-blog", url = "https://madcode.github.io")
        assertEquals("/writing/2025/04/20/reading-list/", Permalink.path(config, evening, "reading-list", listOf("Writing")))
        assertEquals("/cooking/bread/2025/04/20/x/", Permalink.path(config, evening, "x", listOf("cooking", "bread")))
        assertEquals("https://madcode.github.io/sample-blog", Permalink.siteUrl(config, "madCode", "sample-blog", null))
    }

    @Test fun builtInStylesAndTheUtcDayShift() {
        // No timezone in _config.yml: GitHub builds in UTC, where it's already the 21st.
        assertEquals("/2025/04/21/hello.html", Permalink.path(SiteConfig(), evening, "hello", emptyList()))
        assertEquals("/news/hello.html", Permalink.path(SiteConfig(permalink = "none"), evening, "hello", listOf("News")))
        assertEquals("/blog/25/4/21/hello/", Permalink.path(SiteConfig(permalink = "/blog/:short_year/:i_month/:i_day/:slug/"), evening, "hello", emptyList()))
    }

    @Test fun categoriesAreEscapedNotHyphenated() {
        assertEquals("/web%20development/2025/04/21/x.html", Permalink.path(SiteConfig(), evening, "x", listOf("Web Development")))
    }

    @Test fun siteAddresses() {
        // An Actions-built project site: url set, baseurl left for the workflow to pass.
        assertEquals("https://me.github.io/blog", Permalink.siteUrl(SiteConfig(url = "https://me.github.io"), "me", "blog", null))
        // A url that already names the repo isn't given it twice.
        assertEquals("https://me.github.io/blog", Permalink.siteUrl(SiteConfig(url = "https://me.github.io/blog"), "me", "blog", null))
        assertEquals("https://notes.example.com", Permalink.siteUrl(SiteConfig(), "me", "blog", "notes.example.com\n"))
        assertEquals("https://me.github.io", Permalink.siteUrl(SiteConfig(), "Me", "me.github.io", null))
        assertEquals("https://me.github.io/blog", Permalink.siteUrl(SiteConfig(), "me", "blog", null))
        assertEquals("https://example.com/b", Permalink.siteUrl(SiteConfig(url = "https://example.com", baseurl = "/b"), "me", "blog", null))
    }
}
