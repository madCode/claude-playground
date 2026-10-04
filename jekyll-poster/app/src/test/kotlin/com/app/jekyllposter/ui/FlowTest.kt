package com.app.jekyllposter.ui

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.ui.theme.PosterTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The writer's main journeys, through the real screens against a fake GitHub. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, qualifiers = "w411dp-h891dp")
class FlowTest {
    @get:Rule val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val github = app.github

    @After fun close() = github.close()

    private fun start() = compose.setContent { PosterTheme { PosterNavHost(app.container) } }

    private fun signIn() = runBlocking {
        app.container.accounts.save(Account("sample", "good-token", "sample", "sample-blog", "main"))
    }

    @Test fun connectWriteWithCategoriesOldAndNewAndPublish() {
        start()
        compose.waitFor("Connect with a token")
        compose.onNodeWithText("Token").performTextInput("good-token")
        compose.onNodeWithText("Continue").performClick()
        compose.waitFor("sample/sample-blog")
        // Only repositories the token can write to are offered.
        compose.onNodeWithText("sample/read-only").assertDoesNotExist()
        compose.onNodeWithText("sample/sample-blog").performClick()
        compose.waitFor("Welcome to the notebook")

        compose.onNodeWithText("New post", useUnmergedTree = true).performClick()
        compose.waitForTag("title")
        compose.onNodeWithTag("title").performTextInput("From the bus")
        compose.onNodeWithTag("body").performTextInput("Short one, written on the way home.")
        compose.onNode(hasContentDescription("Add category")).performClick()
        // The blog's own categories, most used first, with their counts.
        compose.waitFor("3 posts")
        compose.onNodeWithText("writing").performClick()
        compose.onNodeWithTag("termQuery").performTextInput("commute")
        compose.onNodeWithText("Add “commute” as a new category").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.waitUntil(3_000) { compose.onAllNodes(hasContentDescription("Remove commute")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Publish").performClick()

        compose.waitFor("A Sample Notebook")
        compose.waitUntil(5_000) { github.files().keys.any { it.endsWith("-from-the-bus.md") } }
        val text = github.files().entries.first { it.key.endsWith("-from-the-bus.md") }.value.toString(Charsets.UTF_8)
        assertTrue(text, Regex("""(?s)---\ntitle: From the bus\ndate: \d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} [+-]\d{4}\ncategories: \[writing, commute]\n---\n\nShort one, written on the way home.\n""").matches(text))
    }

    @Test fun signInWithGitHubShowsACodeThenTheBlogs() {
        start()
        compose.waitFor("Sign in with GitHub")
        compose.onNodeWithText("Sign in with GitHub").performClick()
        compose.waitFor("WDJB-MJHT")
        github.deviceApproved = true
        compose.waitFor("sample/sample-blog", timeoutMs = 15_000)
        compose.onNodeWithText("sample/sample-blog").performClick()
        compose.waitFor("Welcome to the notebook")
        val account = runBlocking { app.container.accounts.current() }!!
        assertEquals("refresh-1", account.refreshToken)
        assertTrue(account.expiresAt!! > System.currentTimeMillis())
    }

    @Test fun theBlogsPostsFilterByCategory() {
        signIn()
        start()
        compose.waitFor("A coastal walk")
        compose.onNode(clickLabel("Show posts in travel")).performClick()
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("What I read in April"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("A coastal walk", useUnmergedTree = true).assertExists()
        compose.onNode(clickLabel("Show all posts")).performClick()
        compose.waitFor("What I read in April")
    }

    @Test fun aPostNeedsATitle() {
        signIn()
        start()
        compose.waitFor("New post")
        compose.onNodeWithText("New post", useUnmergedTree = true).performClick()
        compose.waitForTag("body")
        compose.onNodeWithTag("body").performTextInput("No title yet")
        compose.onNodeWithText("Publish").performClick()
        compose.waitFor("A post needs a title: it becomes the address.")
        assertTrue(app.published.isEmpty())
    }

    @Test fun anEmptyNewPostIsDroppedOnBack() {
        signIn()
        start()
        compose.waitFor("New post")
        compose.onNodeWithText("New post", useUnmergedTree = true).performClick()
        compose.waitForTag("title")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitFor("A Sample Notebook")
        compose.waitUntil(3_000) { runBlocking { app.container.database.drafts().snapshotCount() } == 0 }
    }

    @Test fun aSharedLinkMakesOnePostEvenAfterGoingBack() {
        signIn()
        compose.setContent { PosterTheme { PosterNavHost(app.container, com.app.jekyllposter.Shared("[A good read](https://example.com/read)\n", emptyList())) } }
        compose.waitForTag("title")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitFor("On this phone")
        compose.waitForIdle()
        assertEquals(1, runBlocking { app.container.database.drafts().snapshotCount() })
        compose.onNodeWithTag("title").assertDoesNotExist()
    }

    @Test fun postsLeftEmptyWhenTheAppClosedAreHiddenThenDropped() {
        signIn()
        // Left by editors closed under them, without Back: one a day ago, one a moment ago (maybe
        // still open in another window).
        val old = System.currentTimeMillis() - 25 * 60 * 60 * 1000L
        val stale = runBlocking { app.container.drafts.insert(com.app.jekyllposter.data.Draft(createdAt = old, updatedAt = old)) }
        val recent = runBlocking { app.container.drafts.insert(com.app.jekyllposter.data.Draft()) }
        runBlocking { app.container.drafts.insert(com.app.jekyllposter.data.Draft(title = "Half an idea")) }
        start()
        compose.waitFor("Half an idea")
        compose.onNodeWithText("Untitled").assertDoesNotExist()
        compose.waitUntil(5_000) { runBlocking { app.container.drafts.get(stale) } == null }
        assertTrue(runBlocking { app.container.drafts.get(recent) } != null)
    }

    @Test fun searchingSaysWhatFoundNothingAndBackCloses() {
        signIn()
        start()
        compose.waitFor("Welcome to the notebook")
        compose.onNodeWithContentDescription("Search your posts").performClick()
        compose.onNodeWithTag("search").performTextInput("zeppelin")
        compose.waitFor("No posts match “zeppelin”.")
        compose.onNodeWithText("meta").performClick()
        compose.waitFor("No posts in meta match “zeppelin”.")
        androidx.test.espresso.Espresso.pressBack()
        compose.waitFor("Welcome to the notebook")
        compose.onNodeWithTag("search").assertDoesNotExist()
    }

    @Test fun aDraftIsKeptOnTheList() {
        signIn()
        start()
        compose.waitFor("New post")
        compose.onNodeWithText("New post", useUnmergedTree = true).performClick()
        compose.waitForTag("title")
        compose.onNodeWithTag("title").performTextInput("Half an idea")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitFor("On this phone")
        compose.waitFor("Half an idea")
        val drafts = runBlocking { app.container.database.drafts().list() }
        assertEquals(PostState.Draft, drafts.single().state)
    }

    @Test fun aWrongTokenSaysWhatToCheck() {
        start()
        compose.waitFor("Connect with a token")
        compose.onNodeWithText("Token").performTextInput("typo")
        compose.onNodeWithText("Continue").performClick()
        compose.waitFor("GitHub didn't accept that token. Check it was copied whole and hasn't expired.")
    }

    @Test fun switchingBlogListsTheSignInsRepositories() {
        signIn()
        start()
        compose.waitFor("A Sample Notebook")
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.waitFor("Switch blog")
        compose.onNodeWithText("Switch blog").performClick()
        compose.waitFor("Which blog?")
        compose.waitFor("sample/sample-blog")
    }

    @Test fun signingOutReturnsToConnect() {
        signIn()
        start()
        compose.waitFor("A Sample Notebook")
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.waitFor("sample/sample-blog")
        compose.onNodeWithText("Sign out").performClick()
        compose.waitFor("Connect with a token")
    }
}

fun ComposeContentTestRule.waitFor(text: String, timeoutMs: Long = 15_000) =
    waitUntil(timeoutMs) { onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

fun ComposeContentTestRule.waitForTag(tag: String, timeoutMs: Long = 5_000) =
    waitUntil(timeoutMs) { onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

/** A node whose click action is labelled [label], as TalkBack announces it. */
fun clickLabel(label: String) = androidx.compose.ui.test.SemanticsMatcher("click label $label") {
    it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsActions.OnClick) { null }?.label == label
}
