package com.app.jekyllposter.core.jekyll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun aPostUrlLinkStillReadsAsALink() {
        val html = preview.body("See [the list]({{ site.baseurl }}{% post_url 2025-04-20-reading-list %}).")
        assertTrue(html, html.contains("<a href=\"#\">the list</a>"))
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

    @Test fun nothingInThePostLetsThePreviewFetchPastTheApp() {
        val html = preview.page(
            "",
            "<link rel=\"preconnect\" href=\"https://tracker.example\">\n\n" +
                "<VIDEO src=\"https://cdn.example/v.mp4\"><source src=\"https://cdn.example/v.webm\"></VIDEO>\n\n<audio src=\"https://cdn.example/a.mp3\">\n\nText.",
            dark = false,
        )
        assertFalse(html, html.contains("example"))
        assertTrue(html, html.contains("(Video: shown on the site, not in the preview.)"))
        assertTrue(html, html.contains("Text."))
    }

    @Test fun theTitleIsEscapedAndTheThemeFollowsThePhone() {
        val page = preview.page("Fish & <chips>", "Hi", dark = true)
        assertTrue(page.contains("<h1>Fish &amp; &lt;chips&gt;</h1>"))
        assertTrue(page.contains("#1a1110"))
    }
}

class ImagesTest {

    @Test fun pathsSitBesideTheBlogsImagesNamedForThePostAndAvoidTakenOnes() {
        assertEquals("/assets/images/2026/a-walk.jpg", Images.sitePath("assets/images", 2026, "a-walk", "jpg", emptySet()))
        assertEquals("/img/2026/a-walk-2.png", Images.sitePath("/img/", 2026, "a-walk", "png", setOf("img/2026/a-walk.png")))
        assertTrue(Images.isNamedFor("/img/2026/a-walk-2.png", "/img/", "a-walk"))
        assertFalse(Images.isNamedFor("/img/2026/a-walking-tour.png", "/img/", "a-walk"))
        assertFalse(Images.isNamedFor("/img/2026/photo.png", "/img/", "a-walk"))
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

class AltTextTest {
    @Test fun altTextGoesOnTheRightPhoto() {
        val a = Images.markdown("/i/a.jpg", "")
        val b = Images.markdown("/i/b.jpg", "old")
        val body = "$a\n\n$b"
        assertEquals("${Images.markdown("/i/a.jpg", "")}\n\n${Images.markdown("/i/b.jpg", "A [cat] asleep")}", Images.withAlt(body, "/i/b.jpg", "A [cat] asleep "))
    }
}
