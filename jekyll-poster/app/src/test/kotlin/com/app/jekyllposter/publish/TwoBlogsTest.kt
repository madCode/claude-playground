package com.app.jekyllposter.publish

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.core.testing.FakeGitHub
import com.app.jekyllposter.data.BuildState
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.data.Removal
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.idleUntil
import com.app.jekyllposter.ui.connect.ConnectViewModel
import com.app.jekyllposter.ui.home.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A post goes to the blog it was written for, whichever blog is open when it goes. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class TwoBlogsTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container
    private val a = app.github
    private lateinit var b: FakeGitHub

    @Before fun setUp() = runBlocking {
        b = a.addRepository(
            "other-blog",
            mapOf(
                "_config.yml" to "title: Another Notebook\ntimezone: Europe/Lisbon\n".toByteArray(),
                "_posts/2025-03-01-hello-from-b.md" to "---\ntitle: Hello from B\n---\nB's only post.\n".toByteArray(),
            ),
        )
        app.signIn()
        c.blogs.refresh()
        Unit
    }

    @After fun close() = a.close()

    /**
     * Opens [repo], as the writer does: from the switcher once it's added, or with Add a blog under
     * the open blog's sign-in before that.
     */
    private fun switchTo(repo: String) = runBlocking {
        val key = "$repo@main"
        if (c.database.accounts().blog(key) != null) return@runBlocking assertTrue(c.open(key))
        val vm = ConnectViewModel(c)
        vm.addBlogTo(c.database.accounts().blog(c.accounts.currentKey()!!)!!.signInId)
        idleUntil { vm.state.value.repos?.any { it.fullName == repo } == true }
        vm.choose(vm.state.value.repos!!.first { it.fullName == repo })
        idleUntil(10_000) { vm.state.value.done }
    }

    private fun aKey() = "sample/sample-blog@main"

    @Test fun aPostQueuedForOneBlogGoesThereAfterSwitchingToAnother() = runBlocking {
        val id = c.drafts.insert(
            Draft(blog = aKey(), title = "Waiting for its time", body = "Written for A.", state = PostState.Queued, sendAfter = System.currentTimeMillis() + 3_600_000),
        )
        // Not yet: its random time is an hour away.
        assertEquals(Publisher.Outcome.Retry, c.publisher.publish(id))

        switchTo("sample/other-blog")
        assertEquals("sample/other-blog@main", c.accounts.current()!!.blogKey)
        assertEquals("Another Notebook", c.blogs.config.value.title)

        // The hour has passed.
        c.drafts.update(c.drafts.get(id)!!.copy(sendAfter = System.currentTimeMillis() - 1))
        assertEquals(Publisher.Outcome.Done, c.publisher.publish(id))

        val path = a.files().keys.single { it.endsWith("-waiting-for-its-time.md") }
        assertTrue(a.text(path)!!.contains("Written for A."))
        assertTrue(b.files().keys.none { it.contains("waiting-for-its-time") })
        val draft = c.drafts.get(id)!!
        assertEquals(PostState.Published, draft.state)
        assertEquals(a.head, draft.commitSha)

        // B is still the open blog, its posts and title untouched by A's publish.
        assertEquals("Another Notebook", c.blogs.config.value.title)
        assertEquals(listOf("_posts/2025-03-01-hello-from-b.md"), c.blogs.cachedPosts.first().map { it.path })

        // The build is watched on A, where the commit is.
        a.runs[draft.commitSha!!] = "completed" to "success"
        assertTrue(c.buildWatcher.check(id, giveUp = false))
        assertEquals(BuildState.Live, c.drafts.get(id)!!.buildState)
        assertTrue(b.log.none { it.contains("actions/runs") })

        // Back on A, the post is listed with the phone's.
        switchTo("sample/sample-blog")
        val home = HomeViewModel(c)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        scope.launch { home.state.collect {} }
        idleUntil { home.state.value.onPhone.any { it.id == id } }
        scope.cancel()
    }

    @Test fun eachBlogKeepsItsOwnPosts() = runBlocking {
        switchTo("sample/other-blog")
        assertEquals(listOf("_posts/2025-03-01-hello-from-b.md"), c.database.posts().snapshot("sample/other-blog@main").map { it.path })
        assertTrue(c.database.posts().snapshot(aKey()).any { it.path == "_posts/2025-01-12-welcome.md" })
        switchTo("sample/sample-blog")
        assertTrue(c.blogs.cachedPosts.first().any { it.path == "_posts/2025-01-12-welcome.md" })
        assertTrue(c.blogs.cachedPosts.first().none { it.path.contains("hello-from-b") })
        assertEquals("A Sample Notebook", c.blogs.config.value.title)
    }

    @Test fun aPostWithNoBlogIsKeptWithTheBlogItWentTo() = runBlocking {
        val id = c.drafts.insert(Draft(title = "No blog yet", body = "x", state = PostState.Queued))
        assertEquals(Publisher.Outcome.Done, c.publisher.publish(id))
        assertEquals(aKey(), c.drafts.get(id)!!.blog)
        switchTo("sample/other-blog")
        val sha = c.drafts.get(id)!!.commitSha!!
        a.runs[sha] = "completed" to "success"
        assertTrue(c.buildWatcher.check(id, giveUp = false))
        assertEquals(BuildState.Live, c.drafts.get(id)!!.buildState)
    }

    @Test fun aPostForABlogWhoseSignInWasRemovedSaysToSignInToIt() = runBlocking {
        val id = c.drafts.insert(Draft(blog = aKey(), title = "Left behind", body = "x", state = PostState.Failed))
        assertTrue(c.remove(c.database.accounts().blog(aKey())!!.signInId) is Removal.Removed)
        // Published again from the editor, with the sign-in gone.
        c.drafts.update(c.drafts.get(id)!!.copy(state = PostState.Queued))
        val outcome = c.publisher.publish(id)
        assertTrue(outcome is Publisher.Outcome.Failed)
        assertTrue(c.drafts.get(id)!!.error!!.contains("sample/sample-blog"))
        assertTrue(a.files().keys.none { it.contains("left-behind") })
        assertNull(c.accounts.current())
    }
}
