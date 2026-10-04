package com.app.jekyllposter.publish

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.BuildState
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.testutil.TestApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class PublisherTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container
    private val github = app.github
    private val evening = ZonedDateTime.of(2026, 10, 4, 22, 15, 0, 0, ZoneId.of("America/Los_Angeles"))
    private val publisher = Publisher(c.drafts, c.blogs) { evening }

    @Before fun signIn() = runBlocking {
        c.accounts.save(Account("sample", "good-token", "sample", "sample-blog", "main"))
    }

    @After fun close() = github.close()

    private fun queue(draft: Draft): Long = runBlocking { c.drafts.insert(draft.copy(state = PostState.Queued)) }

    @Test fun aNewPostLandsWithItsDateOffsetAndCategories() = runBlocking {
        val id = queue(Draft(title = "Late night: notes", body = "Hello from the phone.", categories = listOf("writing", "late"), tags = listOf("phone")))
        assertEquals(Publisher.Outcome.Done, publisher.publish(id))
        assertEquals(
            "---\ntitle: \"Late night: notes\"\ndate: 2026-10-04 22:15:00 -0700\ncategories: [writing, late]\ntags: [phone]\n---\n\nHello from the phone.\n",
            github.text("_posts/2026-10-04-late-night-notes.md"),
        )
        val draft = c.drafts.get(id)!!
        assertEquals(PostState.Published, draft.state)
        assertEquals(github.head, draft.commitSha)
        assertEquals(BuildState.Building, draft.buildState)
        assertEquals("Add post: Late night: notes", github.commits.getValue(github.head).message)
        // The blog is read again, so the post shows under "On your blog".
        assertTrue(c.database.posts().snapshot().any { it.path == "_posts/2026-10-04-late-night-notes.md" })
    }

    @Test fun aRetryAfterTheCommitLandedDoesNotPostTwice() = runBlocking {
        val id = queue(Draft(title = "Once", body = "Only once."))
        publisher.publish(id)
        val head = github.head
        // As if the app died after the commit but before marking the post published.
        c.drafts.update(c.drafts.get(id)!!.copy(state = PostState.Queued))
        assertEquals(Publisher.Outcome.Done, publisher.publish(id))
        assertEquals(head, github.head)
        assertEquals(1, github.files().keys.count { it.contains("once") })
    }

    @Test fun aTakenNameGetsANumber() = runBlocking {
        github.push("Laptop", mapOf("_posts/2026-10-04-hello.md" to "---\ntitle: Hello\n---\n"))
        val id = queue(Draft(title = "Hello", body = "Again"))
        publisher.publish(id)
        assertEquals("_posts/2026-10-04-hello-2.md", c.drafts.get(id)!!.targetPath)
        assertTrue(github.text("_posts/2026-10-04-hello-2.md")!!.contains("Again"))
    }

    @Test fun anEditKeepsTheKeysTheAppDoesNotTouch() = runBlocking {
        c.blogs.refresh()
        val path = "_posts/2025-04-20-reading-list.md"
        val sha = c.database.posts().snapshot().first { it.path == path }.sha
        val id = queue(Draft(title = "What I read in April", body = "Updated list.", categories = listOf("Writing"), tags = listOf("books", "essays"), editingPath = path, baseSha = sha))
        assertEquals(Publisher.Outcome.Done, publisher.publish(id))
        assertEquals(
            "---\nlayout: post\ntitle: What I read in April\ncategory: Writing\ntags: [books, essays]\n# Kept by hand: the theme reads this for the post card.\nimage: /assets/img/books.png\n---\n\nUpdated list.\n",
            github.text(path),
        )
        assertEquals("Update post: What I read in April", github.commits.getValue(github.head).message)
    }

    @Test fun anEditOfAPostChangedElsewhereIsRefused() = runBlocking {
        c.blogs.refresh()
        val path = "_posts/2025-01-12-welcome.md"
        val sha = c.database.posts().snapshot().first { it.path == path }.sha
        github.push("Laptop edit", mapOf(path to "---\ntitle: Changed on the laptop\n---\n"))
        val id = queue(Draft(title = "Mine", body = "Mine", editingPath = path, baseSha = sha))
        assertTrue(publisher.publish(id) is Publisher.Outcome.Failed)
        assertEquals("---\ntitle: Changed on the laptop\n---\n", github.text(path))
        val draft = c.drafts.get(id)!!
        assertEquals(PostState.Failed, draft.state)
        assertTrue(draft.error!!.contains("changed on GitHub"))
    }

    @Test fun aRevokedTokenFailsWithWhatToDo() = runBlocking {
        github.token = "rotated"
        val id = queue(Draft(title = "T", body = "B"))
        assertTrue(publisher.publish(id) is Publisher.Outcome.Failed)
        assertTrue(c.drafts.get(id)!!.error!!.contains("Sign in again"))
    }

    @Test fun anOutageIsRetriedLaterAndKeepsThePost() = runBlocking {
        github.failures["repos/sample/sample-blog/git/trees/"] = 503
        val id = queue(Draft(title = "T", body = "B"))
        assertEquals(Publisher.Outcome.Retry, publisher.publish(id))
        assertEquals(PostState.Queued, c.drafts.get(id)!!.state)
    }

    @Test fun onlyQueuedPostsAreSent() = runBlocking {
        val id = runBlocking { c.drafts.insert(Draft(title = "Still writing", body = "x")) }
        assertEquals(Publisher.Outcome.Done, publisher.publish(id))
        assertNull(github.files().keys.firstOrNull { it.contains("still-writing") })
    }

    @Test fun theBuildWatcherFollowsThePagesRun() = runBlocking {
        val id = queue(Draft(title = "Watch me", body = "x"))
        publisher.publish(id)
        val sha = c.drafts.get(id)!!.commitSha!!
        assertEquals(false, c.buildWatcher.check(id, giveUp = false))
        github.runs[sha] = "in_progress" to null
        assertEquals(false, c.buildWatcher.check(id, giveUp = false))
        github.runs[sha] = "completed" to "success"
        assertEquals(true, c.buildWatcher.check(id, giveUp = false))
        assertEquals(BuildState.Live, c.drafts.get(id)!!.buildState)
    }

    @Test fun aFailedBuildIsReportedAndSilenceEndsAsUnknown() = runBlocking {
        val a = queue(Draft(title = "Broken", body = "x"))
        publisher.publish(a)
        github.runs[c.drafts.get(a)!!.commitSha!!] = "completed" to "failure"
        c.buildWatcher.check(a, giveUp = false)
        assertEquals(BuildState.Failed, c.drafts.get(a)!!.buildState)

        val b = queue(Draft(title = "Quiet", body = "x"))
        publisher.publish(b)
        assertEquals(true, c.buildWatcher.check(b, giveUp = true))
        assertEquals(BuildState.Unknown, c.drafts.get(b)!!.buildState)
    }
}
