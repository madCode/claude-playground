package com.app.jekyllposter.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
}
