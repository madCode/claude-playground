package com.app.jekyllposter.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.ui.theme.PosterTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Opening a post that's already on the blog. Publishing the edit is covered by
 * EditorViewModelTest: here, after another Compose test in the same JVM, the editor's Room write
 * waits forever (see DEVLOG, cycle 1); the same steps pass outside the Compose test harness.
 */
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
}
