package com.app.jekyllposter.ui.blogs

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.testutil.SAMPLE_BLOG
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.idleUntil
import com.app.jekyllposter.ui.connect.ConnectViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Signing in to an account the app already has, to add another of its blogs. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class SameAccountTwiceTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container
    private val sample = app.github
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before fun setUp() {
        sample.addRepository("garden", mapOf("_config.yml" to "title: The Garden\n".toByteArray()))
    }

    @After fun close() {
        scope.cancel()
        sample.close()
    }

    /** What Settings → Your blogs lists: each sign-in's login and its blogs. */
    private fun yourBlogs(): List<Pair<String, List<String>>> {
        val state = BlogsViewModel(c).state.also { scope.launch { it.collect {} } }
        idleUntil { state.value.groups.isNotEmpty() }
        return state.value.groups.map { g -> g.login to g.blogs.map { it.entry.key } }
    }

    /** Add a sign-in, then [repo], the way the sign-in screen does. */
    private fun addSignIn(repo: String, signIn: ConnectViewModel.() -> Unit) {
        val vm = ConnectViewModel(c)
        vm.signIn()
        // The device flow polls GitHub on a delay, which the looper runs only as its clock moves.
        idleUntil(10_000) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)); vm.state.value.repos != null }
        vm.choose(vm.state.value.repos!!.first { it.fullName == repo })
        idleUntil(10_000) { vm.state.value.done }
    }

    private fun withGitHub(vm: ConnectViewModel) {
        sample.deviceApproved = true
        vm.signInWithGitHub()
    }

    private fun withToken(token: String): ConnectViewModel.() -> Unit = {
        setToken(token)
        checkToken()
    }

    @Test fun signingInWithGitHubAgainKeepsOneSignInWithBothBlogs() {
        addSignIn("sample/sample-blog") { withGitHub(this) }
        addSignIn("sample/garden") { withGitHub(this) }
        assertEquals(listOf("sample" to listOf("sample/garden@main", SAMPLE_BLOG)), yourBlogs())
    }

    @Test fun pastingTheSameTokenAgainKeepsOneSignIn() {
        runBlocking { app.signIn() }
        addSignIn("sample/garden", withToken("good-token"))
        assertEquals(listOf("sample" to listOf("sample/garden@main", SAMPLE_BLOG)), yourBlogs())
    }

    @Test fun anotherTokenForTheSameAccountIsASignInOfItsOwn() {
        // A second fine-grained token for the same account, made for another repository only.
        sample.addAccount("sample", "notes-token", "notes", mapOf("_config.yml" to "title: Notes\n".toByteArray()))
        runBlocking { app.signIn() }
        addSignIn("sample/notes", withToken("notes-token"))
        assertEquals(listOf("sample" to listOf(SAMPLE_BLOG), "sample" to listOf("sample/notes@main")), yourBlogs())
        // The first blog still reads with its own token, which the second can't stand in for.
        assertEquals(true, runBlocking { c.open(SAMPLE_BLOG) })
        assertEquals("A Sample Notebook", c.blogs.config.value.title)
    }
}
