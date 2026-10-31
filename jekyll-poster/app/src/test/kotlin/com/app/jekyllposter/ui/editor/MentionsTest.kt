package com.app.jekyllposter.ui.editor

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.testutil.SAMPLE_BLOG
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.ui.PosterNavHost
import com.app.jekyllposter.ui.theme.PosterTheme
import com.app.jekyllposter.ui.waitFor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A post's editor shows what webmention.io has received for it, for a blog that asks. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, qualifiers = "w411dp-h891dp")
class MentionsTest {
    @get:Rule val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val io = app.webmentionIo

    @After fun close() = app.github.close()

    private val feed = """{"type":"feed","children":[
        {"type":"entry","author":{"name":"Ada"},"url":"https://ada.example/notes/1","wm-property":"in-reply-to","content":{"text":"Which essay first?"}},
        {"type":"entry","author":{"name":""},"url":"https://bo.example/likes/9","wm-property":"like-of"}
    ]}"""

    private fun openReadingList(show: Boolean) {
        runBlocking {
            app.signIn()
            app.container.privacy.update(SAMPLE_BLOG) { it.copy(showWebmentions = show) }
        }
        compose.setContent { PosterTheme { PosterNavHost(app.container) } }
        compose.waitFor("What I read in April")
        compose.onNodeWithText("What I read in April").performClick()
        compose.waitFor("A few books, a few essays.")
    }

    @Test fun aBlogPostOpenedToEditListsItsMentions() {
        io.enqueue(MockResponse.Builder().body(feed).build())
        openReadingList(show = true)
        compose.waitFor("1 reply · 1 like")
        // Asked for the post's address as the site builds it.
        assertEquals("https://sample.github.io/sample-blog/writing/2025/04/20/reading-list/", io.takeRequest().url.queryParameter("target"))
        compose.onNodeWithTag("mentions").performClick()
        compose.onNodeWithText("\u2068Ada\u2069 replied").assertExists()
        compose.onNodeWithText("Which essay first?").assertExists()
        compose.onNodeWithText("Someone liked it").assertExists()
    }

    @Test fun webmentionIoDownSaysSo() {
        io.enqueue(MockResponse.Builder().code(502).build())
        openReadingList(show = true)
        compose.waitFor("Couldn't read webmention.io just now")
    }

    @Test fun offWebmentionIoIsNeverAsked() {
        openReadingList(show = false)
        compose.onNodeWithTag("mentions").assertDoesNotExist()
        assertNull(io.takeRequest(500, TimeUnit.MILLISECONDS))
    }
}
