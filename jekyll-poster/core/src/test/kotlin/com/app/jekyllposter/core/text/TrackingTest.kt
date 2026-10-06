package com.app.jekyllposter.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingTest {
    @Test fun campaignTagsAndClickIdsGo() {
        assertEquals("https://example.com/read", Tracking.strip("https://example.com/read?utm_source=x&utm_medium=social&fbclid=abc"))
    }

    @Test fun theRestOfTheLinkStaysInOrder() {
        assertEquals(
            "[A good read](https://example.com/search?q=jekyll&page=2#results)\n",
            Tracking.strip("[A good read](https://example.com/search?q=jekyll&utm_campaign=z&page=2&gclid=9#results)\n"),
        )
    }

    @Test fun youtubesShareIdGoesButNotItsVideo() {
        assertEquals("https://youtu.be/dQw4w9WgXcQ?t=42", Tracking.strip("https://youtu.be/dQw4w9WgXcQ?si=Ab12Cd&t=42"))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", Tracking.strip("https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=Ab12Cd"))
        // Elsewhere, si may be the page's own.
        assertEquals("https://example.com/?si=7", Tracking.strip("https://example.com/?si=7"))
    }

    @Test fun eachSitesOwnShareCodesGoButOnlyOnThatSite() {
        assertEquals("https://x.com/a/status/1", Tracking.strip("https://x.com/a/status/1?s=46&t=AbC"))
        assertEquals("https://www.reddit.com/r/x/comments/1/", Tracking.strip("https://www.reddit.com/r/x/comments/1/?share_id=Zz&utm_source=share"))
        assertEquals("https://www.amazon.co.uk/dp/B0", Tracking.strip("https://www.amazon.co.uk/dp/B0?tag=me-21&ref_=x"))
        assertEquals("https://www.google.com/search?q=jekyll", Tracking.strip("https://www.google.com/search?q=jekyll&ved=1&ei=2"))
        assertEquals("https://blog.substack.com/p/post", Tracking.strip("https://blog.substack.com/p/post?r=abc"))
        // Elsewhere `t` and `r` may be the page itself: a video's start time, say.
        assertEquals("https://www.youtube.com/watch?v=x&t=42", Tracking.strip("https://www.youtube.com/watch?v=x&t=42"))
        assertEquals("https://example.com/?r=2&tag=news", Tracking.strip("https://example.com/?r=2&tag=news"))
        // A brand's name at the front of someone else's domain isn't the brand.
        assertEquals("https://amazon.example.org/?tag=news", Tracking.strip("https://amazon.example.org/?tag=news"))
        assertEquals("https://www.instagram.com/p/X/?img_index=3", Tracking.strip("https://www.instagram.com/p/X/?img_index=3"))
    }

    @Test fun textAroundLinksAndLinksWithoutQueriesAreUntouched() {
        val text = "Two links: https://a.example/x and https://b.example/y?utm_source=feed, both good."
        assertEquals("Two links: https://a.example/x and https://b.example/y, both good.", Tracking.strip(text))
    }

    @Test fun markdownAroundALinkStays() {
        assertEquals("**https://x.example/?a=1**", Tracking.strip("**https://x.example/?a=1&utm_source=b**"))
        assertEquals("`https://x.example/`", Tracking.strip("`https://x.example/?utm_source=a`"))
        assertEquals("| https://x.example/ |", Tracking.strip("| https://x.example/?utm_source=a |"))
        assertEquals("_https://x.example/?a=1_", Tracking.strip("_https://x.example/?a=1&utm_source=b_"))
    }

    @Test fun htmlEscapedAmpersandsKeepTheirParameters() {
        assertEquals("https://x.example/p?id=5&amp;page=2", Tracking.strip("https://x.example/p?utm_source=s&amp;id=5&amp;page=2"))
    }
}
