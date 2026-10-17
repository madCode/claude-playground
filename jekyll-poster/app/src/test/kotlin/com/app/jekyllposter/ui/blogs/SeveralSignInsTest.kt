package com.app.jekyllposter.ui.blogs

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.core.testing.FakeGitHub
import com.app.jekyllposter.data.BuildState
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.data.Removal
import com.app.jekyllposter.publish.Publisher
import com.app.jekyllposter.testutil.SAMPLE_BLOG
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.idleUntil
import com.app.jekyllposter.ui.connect.ConnectViewModel
import com.app.jekyllposter.ui.editor.EditorViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
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

/** Blogs under two GitHub accounts in one app: adding, switching, removing, and keeping them apart. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class SeveralSignInsTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container
    private val sample = app.github
    private lateinit var inkwell: FakeGitHub
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before fun setUp() = runBlocking {
        inkwell = sample.addAccount(
            "inkwell", "inkwell-token", "night-notes",
            mapOf(
                "_config.yml" to "title: Night Notes\n".toByteArray(),
                "_posts/2025-02-02-after-dark.md" to "---\ntitle: After dark\n---\nWritten at night.\n".toByteArray(),
            ),
        )
        app.signIn()
        c.blogs.refresh()
        Unit
    }

    @After fun close() {
        scope.cancel()
        sample.close()
    }

    /** Keeps [flow] collected, as a screen would, so its state stays live. */
    private fun <T> watched(flow: StateFlow<T>): StateFlow<T> = flow.also { scope.launch { it.collect {} } }

    /** Settings → Add a sign-in, with a token for the inkwell account, then its blog. */
    private fun addInkwell() {
        val vm = ConnectViewModel(c)
        vm.setToken("inkwell-token")
        vm.checkToken()
        idleUntil { vm.state.value.repos != null }
        assertEquals(listOf("inkwell/night-notes"), vm.state.value.repos!!.map { it.fullName }.filter { it.endsWith("night-notes") })
        vm.choose(vm.state.value.repos!!.first { it.fullName == "inkwell/night-notes" })
        idleUntil(10_000) { vm.state.value.done }
    }

    private val inkwellBlog = "inkwell/night-notes@main"

    @Test fun aSecondAccountsBlogIsAddedBesideTheFirstAndOpens() {
        val blogs = watched(BlogsViewModel(c).state)
        addInkwell()
        assertEquals(inkwellBlog, runBlocking { c.accounts.currentKey() })
        assertEquals("Night Notes", c.blogs.config.value.title)
        idleUntil { blogs.value.groups.size == 2 && blogs.value.groups.all { g -> g.blogs.all { it.entry.title != null } } }
        val groups = blogs.value.groups
        assertEquals(listOf("sample", "inkwell"), groups.map { it.login })
        assertEquals(listOf("A Sample Notebook" to false, "Night Notes" to true), groups.flatMap { g -> g.blogs.map { it.entry.title to it.open } })
    }

    @Test fun eachAccountIsSentOnlyItsOwnBlogsRequests() = runBlocking {
        addInkwell()
        // Written for the sample blog, and going out while Night Notes is open.
        val id = c.drafts.insert(Draft(blog = SAMPLE_BLOG, title = "For the notebook", body = "x", state = PostState.Queued))
        assertEquals(Publisher.Outcome.Done, c.publisher.publish(id))
        assertTrue(sample.files().keys.any { it.endsWith("-for-the-notebook.md") })
        assertTrue(inkwell.files().keys.none { it.contains("for-the-notebook") })
        assertTrue(inkwell.log.isNotEmpty())
        assertTrue(inkwell.log.none { it.contains("sample") })
        assertTrue(sample.log.none { it.contains("inkwell") || it.contains("night-notes") })

        // Opening the sample blog reads it, and asks nothing of the account whose blog isn't open.
        val asked = inkwell.log.size
        assertTrue(c.open(SAMPLE_BLOG))
        assertEquals("A Sample Notebook", c.blogs.config.value.title)
        assertEquals(asked, inkwell.log.size)
    }

    @Test fun aSignInIsntRemovedWhileAPostWaitsAndSendNowSendsIt() {
        addInkwell()
        val vm = BlogsViewModel(c)
        val blogs = watched(vm.state)
        val id = runBlocking {
            c.drafts.insert(Draft(blog = inkwellBlog, title = "At midnight", state = PostState.Queued, sendAfter = System.currentTimeMillis() + 3_600_000))
        }
        idleUntil { blogs.value.groups.size == 2 }
        vm.askRemove(blogs.value.groups.single { it.login == "inkwell" })
        vm.remove()
        idleUntil { blogs.value.removing?.waiting != null }
        assertEquals(listOf(id), blogs.value.removing!!.waiting!!.map { it.id })
        assertEquals(2, blogs.value.groups.size)

        vm.sendWaitingNow()
        idleUntil { id in app.sentNow }
        assertNull(blogs.value.removing)
        assertNull(runBlocking { c.drafts.get(id) }!!.sendAfter)
    }

    @Test fun removingTheOpenBlogsSignInOpensTheOtherAndTheLastSignsOut() {
        addInkwell()
        val vm = BlogsViewModel(c)
        val blogs = watched(vm.state)
        idleUntil { blogs.value.groups.size == 2 }
        vm.askRemove(blogs.value.groups.single { it.login == "inkwell" })
        vm.remove()
        idleUntil { blogs.value.groups.size == 1 && runBlocking { c.accounts.currentKey() } == SAMPLE_BLOG }
        idleUntil { c.blogs.config.value.title == "A Sample Notebook" }
        assertTrue(!blogs.value.signedOut)

        vm.askRemove(blogs.value.groups.single())
        vm.remove()
        idleUntil { blogs.value.signedOut }
        assertNull(runBlocking { c.accounts.current() })
    }

    @Test fun addingABlogToASignInRemovedMeanwhileSignsInAfresh() {
        addInkwell()
        val gone = runBlocking { c.database.accounts().blog(inkwellBlog)!!.signInId }
        runBlocking { c.remove(gone) }
        // As after the app was restored on Add a blog for a sign-in removed since.
        val vm = ConnectViewModel(c)
        vm.addBlogTo(gone)
        idleUntil { !vm.state.value.busy }
        assertNull(vm.addingTo)
        vm.setToken("inkwell-token")
        vm.checkToken()
        idleUntil { vm.state.value.repos != null }
        vm.choose(vm.state.value.repos!!.first { it.fullName == "inkwell/night-notes" })
        idleUntil(10_000) { vm.state.value.done }
        assertEquals("inkwell", runBlocking { c.accounts.current() }!!.login)
    }

    @Test fun aBuildNoLongerWatchedOnceItsSignInIsRemovedSaysSo() = runBlocking {
        addInkwell()
        val id = c.drafts.insert(Draft(blog = inkwellBlog, title = "Just out", state = PostState.Published, commitSha = "abc", buildState = BuildState.Building))
        assertTrue(c.remove(c.database.accounts().blog(inkwellBlog)!!.signInId) is Removal.Removed)
        assertTrue(c.buildWatcher.check(id, giveUp = false))
        assertEquals(BuildState.Unknown, c.drafts.get(id)!!.buildState)
    }

    @Test fun aPostThatDidntGoIsNamedOnItsBlogsRow() {
        addInkwell()
        runBlocking { c.drafts.insert(Draft(blog = SAMPLE_BLOG, title = "Rejected", state = PostState.Failed, error = "Sign in again")) }
        val blogs = watched(BlogsViewModel(c).state)
        idleUntil { blogs.value.groups.size == 2 }
        val row = blogs.value.groups.flatMap { it.blogs }.single { it.entry.key == SAMPLE_BLOG }
        assertEquals("sample/sample-blog · 1 didn't go", row.status)
    }

    @Test fun theEditorNamesTheBlogAPostGoesToOnlyWhenThereAreSeveral() {
        val id = runBlocking { c.drafts.insert(Draft(blog = SAMPLE_BLOG, title = "Where to?")) }
        val alone = watched(EditorViewModel(c, id).state)
        idleUntil { alone.value.draft != null }
        assertNull(alone.value.to)

        addInkwell()
        val editor = watched(EditorViewModel(c, id).state)
        idleUntil { editor.value.to != null }
        // The post's own blog, not the one open now.
        assertEquals(SAMPLE_BLOG, editor.value.to!!.key)
        assertEquals("sample", editor.value.to!!.login)
    }
}
