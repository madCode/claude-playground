package com.app.jekyllposter.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.testutil.SAMPLE_BLOG
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.waitUntilLooped
import com.app.jekyllposter.ui.theme.PosterTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Opening a post that's already on the blog, changing it on the screen, and publishing the edit. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, qualifiers = "w411dp-h891dp")
class EditFlowTest {
    @get:Rule val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val github = app.github

    @After fun close() = github.close()

    @Test fun tappingAPostOnTheBlogOpensItsTextToEdit() {
        runBlocking { app.signIn() }
        compose.setContent { PosterTheme { PosterNavHost(app.container) } }
        compose.waitFor("What I read in April")
        compose.onNodeWithText("What I read in April").performClick()
        compose.waitFor("A few books, a few essays.")
        // The post spells it `category: Writing`; the editor shows it as one of the post's categories.
        compose.onNodeWithContentDescription("Remove Writing").assertExists()
        assertEquals("_posts/2025-04-20-reading-list.md", runBlocking { app.container.database.drafts().list() }.single().editingPath)
    }

    @Test fun anEditMadeOnTheScreenIsPublishedToThePost() {
        runBlocking { app.signIn() }
        compose.setContent { PosterTheme { PosterNavHost(app.container) } }
        compose.waitFor("What I read in April")
        compose.onNodeWithText("What I read in April").performClick()
        compose.waitFor("A few books, a few essays.")
        compose.onNodeWithTag("body").performTextInput("And one long poem. ")
        compose.onNodeWithText("Update").performClick()
        compose.waitUntilLooped(15_000) {
            github.files()["_posts/2025-04-20-reading-list.md"]?.toString(Charsets.UTF_8)?.contains("And one long poem.") == true
        }
        // The rest of the post, kept as it was.
        assertTrue(github.files().getValue("_posts/2025-04-20-reading-list.md").toString(Charsets.UTF_8).contains("image: /assets/img/books.png"))
    }

    @Test fun hidingAPostKeepsItsFileWithPublishedFalseAndShowingTakesThatOut() {
        val path = "_posts/2025-04-20-reading-list.md"
        fun post() = github.files().getValue(path).toString(Charsets.UTF_8)
        val before = post()
        runBlocking { app.signIn() }
        compose.setContent { PosterTheme { PosterNavHost(app.container) } }
        compose.waitFor("What I read in April")
        compose.onNodeWithText("What I read in April").performClick()
        compose.waitFor("A few books, a few essays.")
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Hide from the site").performClick()

        compose.waitUntilLooped(15_000) { post().contains("published: false") }
        // Only the key added: the rest of the post as it was.
        assertEquals(before.lines().filter { it != "published: false" }, post().lines().filter { it != "published: false" })
        compose.waitFor("Hidden · the site is rebuilding…")

        // The blog's copy, under the phone's own row for the edit just sent.
        compose.onAllNodesWithText("What I read in April")[1].performClick()
        compose.waitFor("A few books, a few essays.")
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Show on the site again").performClick()
        compose.waitUntilLooped(15_000) { !post().contains("published") }
        assertEquals(before, post())
    }

    @Test fun aHideGoesOutAtOnceChangingOnlyItsLineEvenInFrontMatterTheEditorWouldRefuse() {
        // Pushed from a laptop: a flow map over two lines, which the front matter field refuses.
        val path = "_posts/2025-05-05-odd-map.md"
        val odd = "---\ntitle: Odd map\nlinks: {a: 1,\nb: 2}\ndate: 2025-05-05\ntags: [x]\n---\n\nText.\n"
        github.push("Laptop", mapOf(path to odd))
        runBlocking {
            app.signIn()
            app.container.privacy.update(SAMPLE_BLOG) { it.copy(sendAtRandomTime = true) }
        }
        compose.setContent { PosterTheme { PosterNavHost(app.container) } }
        compose.waitFor("Odd map")
        compose.onNodeWithText("Odd map").performClick()
        compose.waitFor("Text.")
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Hide from the site").performClick()

        // Not held for a random time, and the one line added where Jekyll reads it, the rest in place.
        compose.waitUntilLooped(15_000) { github.text(path)?.contains("published: false") == true }
        assertEquals(odd.replace("tags: [x]\n", "tags: [x]\npublished: false\n"), github.text(path))
    }

    @Test fun anEarlierUpdateOfAHiddenPostNoLongerSaysItIsLive() {
        runBlocking { app.signIn() }
        compose.setContent { PosterTheme { PosterNavHost(app.container) } }
        compose.waitFor("What I read in April")
        compose.onNodeWithText("What I read in April").performClick()
        compose.waitFor("A few books, a few essays.")
        compose.onNodeWithTag("body").performTextInput("And one long poem. ")
        compose.onNodeWithText("Update").performClick()
        compose.waitUntilLooped(15_000) { github.text("_posts/2025-04-20-reading-list.md")?.contains("long poem") == true }

        compose.onAllNodesWithText("What I read in April")[1].performClick()
        compose.waitFor("And one long poem.")
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Hide from the site").performClick()
        compose.waitUntilLooped(15_000) { github.text("_posts/2025-04-20-reading-list.md")?.contains("published: false") == true }
        compose.waitUntilLooped(15_000) { runBlocking { app.container.database.drafts().list() }.size == 1 }
        assertTrue(runBlocking { app.container.database.drafts().list() }.single().hidden)
    }
}
