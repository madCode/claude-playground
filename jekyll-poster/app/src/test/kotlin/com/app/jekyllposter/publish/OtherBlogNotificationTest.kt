package com.app.jekyllposter.publish

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.core.testing.FakeGitHub
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.data.Removal
import com.app.jekyllposter.testutil.SAMPLE_BLOG
import com.app.jekyllposter.testutil.TestApp
import android.os.Looper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A post's notification when its blog isn't the open one: it names the blog, and a tap that opens
 * the app opens it on that blog, where Home lists the post.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, qualifiers = "w411dp-h891dp")
class OtherBlogNotificationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container
    private val manager = app.getSystemService(NotificationManager::class.java)
    private lateinit var night: FakeGitHub
    private val nightKey = "night/night-notes@main"

    @Before fun setUp() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.signIn()
        c.blogs.refresh()
        // A second account, signed in with a token of its own.
        night = app.github.addAccount("night", "night-token", "night-notes", mapOf("_config.yml" to "title: Night Notes\n".toByteArray()))
        c.accounts.save(Account("night", "night-token", "night", "night-notes", "main"))
        c.blogs.refresh()
        assertEquals(nightKey, c.accounts.currentKey())
    }

    @After fun close() = app.github.close()

    /** The main looper as well as Compose's frames: Room, DataStore and the switch finish elsewhere. */
    private fun waitFor(condition: () -> Boolean) = compose.waitUntil(10_000) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    /** Publishes a post to Night Notes, opens the sample blog, and lets Night Notes' build end as [conclusion]. */
    private fun builtElsewhere(conclusion: String): Notification = runBlocking {
        val id = c.drafts.insert(Draft(blog = nightKey, title = "Bus notes", body = "Written at night.", state = PostState.Queued))
        assertEquals(Publisher.Outcome.Done, c.publisher.publish(id))
        assertTrue(c.open(SAMPLE_BLOG))
        night.runs[c.drafts.get(id)!!.commitSha!!] = "completed" to conclusion
        assertTrue(c.buildWatcher.check(id, giveUp = false))
        shadowOf(manager).allNotifications.single()
    }

    private fun Notification.publicText() = publicVersion.extras.run { "${getCharSequence(Notification.EXTRA_TITLE)} ${getCharSequence(Notification.EXTRA_TEXT)}" }

    @Test fun aPostLiveOnTheOpenBlogDoesNotNameIt() = runBlocking {
        val id = c.drafts.insert(Draft(blog = nightKey, title = "Bus notes", body = "x", state = PostState.Queued))
        assertEquals(Publisher.Outcome.Done, c.publisher.publish(id))
        night.runs[c.drafts.get(id)!!.commitSha!!] = "completed" to "success"
        assertTrue(c.buildWatcher.check(id, giveUp = false))
        assertEquals("“Bus notes” is live", shadowOf(shadowOf(manager).allNotifications.single()).contentTitle)
    }

    @Test fun aPostLiveOnAnotherBlogNamesItAndOpensOnTheSite() {
        val n = builtElsewhere("success")
        assertEquals("“Bus notes” is live on Night Notes", shadowOf(n).contentTitle)
        // A locked phone shows neither the post nor the blog.
        assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
        assertTrue(n.publicText().let { "A post is live" in it && "Night" !in it && "Bus" !in it })
        val intent = shadowOf(n.contentIntent).savedIntent
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(intent.dataString!!.contains("bus-notes"))
    }

    @Test fun aFailedBuildOnAnotherBlogOpensTheAppOnThatBlogAndSaysSo() {
        val n = builtElsewhere("failure")
        assertEquals("Night Notes didn't rebuild", shadowOf(n).contentTitle)
        assertTrue(n.publicText().let { "Your site didn't rebuild" in it && "Night" !in it && "Bus" !in it })

        val scenario = ActivityScenario.launch<com.app.jekyllposter.MainActivity>(shadowOf(n.contentIntent).savedIntent)
        waitFor { runBlocking { c.accounts.currentKey() } == nightKey }
        waitFor { compose.onAllNodesWithText("Switched to Night Notes").fetchSemanticsNodes().isNotEmpty() }
        // Home lists the post that didn't go: it was hidden under the sample blog.
        waitFor { compose.onAllNodesWithText("Bus notes").fetchSemanticsNodes().isNotEmpty() }

        // The writer switches back; a rotation doesn't switch again.
        runBlocking { assertTrue(c.open(SAMPLE_BLOG)) }
        scenario.recreate()
        compose.waitForIdle()
        assertEquals(SAMPLE_BLOG, runBlocking { c.accounts.currentKey() })
        scenario.close()
    }

    @Test fun anotherAppCannotSwitchTheBlog() {
        // The extra, sent to the exported activity as any app could: only the app's own alias counts.
        val intent = Intent(app, com.app.jekyllposter.MainActivity::class.java).putExtra(com.app.jekyllposter.MainActivity.EXTRA_BLOG, SAMPLE_BLOG)
        val scenario = ActivityScenario.launch<com.app.jekyllposter.MainActivity>(intent)
        waitFor { compose.onAllNodesWithText("Night Notes").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(nightKey, runBlocking { c.accounts.currentKey() })
        scenario.close()
    }

    @Test fun aTapAfterTheBlogWasRemovedSaysSo() {
        val n = builtElsewhere("failure")
        runBlocking { assertTrue(c.remove(c.database.accounts().blog(nightKey)!!.signInId) is Removal.Removed) }

        val scenario = ActivityScenario.launch<com.app.jekyllposter.MainActivity>(shadowOf(n.contentIntent).savedIntent)
        waitFor { compose.onAllNodesWithText("That post's blog isn't in the app any more.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(SAMPLE_BLOG, runBlocking { c.accounts.currentKey() })
        scenario.close()
    }
}
