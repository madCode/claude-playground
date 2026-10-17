package com.app.jekyllposter.ui.blogs

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.core.testing.FakeGitHub
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.data.Removal
import com.app.jekyllposter.publish.Publisher
import com.app.jekyllposter.testutil.SAMPLE_BLOG
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.idleUntil
import com.app.jekyllposter.ui.connect.ConnectViewModel
import com.app.jekyllposter.ui.settings.BlogPrivacyViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Each blog keeps its own privacy switches, and its own VPN gate, whichever blog is open. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class PrivacyPerBlogTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container
    private val sample = app.github
    private val vpn = app.vpn
    private lateinit var inkwell: FakeGitHub
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val night = "inkwell/night-notes@main"

    @Before fun setUp() = runBlocking {
        inkwell = sample.addAccount(
            "inkwell", "inkwell-token", "night-notes",
            mapOf("_config.yml" to "title: Night Notes\n".toByteArray(), "_posts/2025-02-02-after-dark.md" to "---\ntitle: After dark\n---\nx\n".toByteArray()),
        )
        app.signIn()
        c.accounts.save(Account("inkwell", "inkwell-token", "inkwell", "night-notes", "main"))
        // The sample blog open, as a personal blog would be while a pseudonymous one waits.
        assertTrue(c.open(SAMPLE_BLOG))
        Unit
    }

    @After fun close() {
        scope.cancel()
        sample.close()
    }

    private fun <T> watched(flow: StateFlow<T>): StateFlow<T> = flow.also { scope.launch { it.collect {} } }

    private fun queued(blog: String, title: String) = runBlocking { c.drafts.insert(Draft(blog = blog, title = title, body = "x", state = PostState.Queued)) }

    @Test fun aSwitchOnOneBlogLeavesTheOtherAsItWas() = runBlocking {
        c.privacy.update(night) { it.copy(plainCommitMessages = true) }
        assertEquals(Publisher.Outcome.Done, c.publisher.publish(queued(night, "Under the streetlight")))
        assertEquals(Publisher.Outcome.Done, c.publisher.publish(queued(SAMPLE_BLOG, "Sourdough, round five")))
        assertEquals("Update blog", inkwell.commits.getValue(inkwell.head).message)
        assertTrue(sample.commits.getValue(sample.head).message.contains("Sourdough, round five"))
    }

    @Test fun aVpnOnlyBlogsPostWaitsForTheVpnWhileTheOtherGoesWithoutIt() = runBlocking {
        c.setOnlyThroughVpn(night, true)
        vpn.up.value = false
        val quiet = queued(night, "Quiet")
        val loud = queued(SAMPLE_BLOG, "Loud")
        assertEquals(Publisher.Outcome.Retry, c.publisher.publish(quiet))
        assertTrue(inkwell.files().keys.none { it.contains("quiet") })
        assertFalse(c.awaitVpn(night, kotlin.time.Duration.parse("50ms")))
        // The personal blog asks for no VPN: it goes now, and not through one.
        assertTrue(c.awaitVpn(SAMPLE_BLOG, kotlin.time.Duration.parse("50ms")))
        assertEquals(Publisher.Outcome.Done, c.publisher.publish(loud))
        assertEquals(0, vpn.connections)

        vpn.up.value = true
        assertEquals(Publisher.Outcome.Done, c.publisher.publish(quiet))
        assertTrue(inkwell.files().keys.any { it.endsWith("-quiet.md") })
        assertTrue(vpn.connections > 0)
    }

    @Test fun onlyTheVpnOnlyBlogsPostsStartAgainWhenTheVpnIsBack() {
        app.publishNow = false
        runBlocking { c.setOnlyThroughVpn(night, true) }
        val quiet = queued(night, "Quiet")
        val loud = queued(SAMPLE_BLOG, "Loud")
        // Queued after the other blog's post: once it's retried, a retry of every post would have
        // reached the other blog's too.
        val quieter = queued(night, "Quieter")
        app.container.startVpnRetry()
        vpn.up.value = false
        // Long enough for the app to see the blog is waiting, as VpnTest does.
        Thread.sleep(500)
        vpn.up.value = true
        idleUntil { quiet in app.retried && quieter in app.retried }
        assertFalse(loud in app.retried)
    }

    @Test fun signingInGoesThroughTheVpnWhenAnyBlogAsksForIt() {
        runBlocking { c.setOnlyThroughVpn(night, true) }
        vpn.up.value = false
        sample.log.clear()
        val vm = ConnectViewModel(c)
        vm.setToken("good-token")
        vm.checkToken()
        idleUntil { !vm.state.value.busy }
        assertEquals("No VPN connection, so nothing was sent to GitHub. Turn your VPN on, then try again.", vm.state.value.error)
        assertEquals(emptyList<String>(), sample.log)
    }

    @Test fun blogAndPrivacyShowsAndChangesTheOpenBlogsSwitchesOnly() {
        runBlocking { c.privacy.update(night) { it.copy(datesByDayOnly = true) } }
        val vm = BlogPrivacyViewModel(c)
        idleUntil { vm.state.value.title == "A Sample Notebook" }
        assertFalse(vm.state.value.datesByDayOnly)
        vm.setSendAtRandomTime(true)
        idleUntil { vm.state.value.sendAtRandomTime }
        assertFalse(runBlocking { c.privacy.get(night) }.sendAtRandomTime)

        runBlocking { c.open(night) }
        // Its title and its switches arrive separately: wait for both.
        idleUntil { vm.state.value.title == "Night Notes" && vm.state.value.datesByDayOnly }
        assertFalse(vm.state.value.sendAtRandomTime)
    }

    @Test fun theSwitcherShowsEachBlogsSwitches() {
        runBlocking { c.setOnlyThroughVpn(night, true); c.privacy.update(night) { it.copy(sendAtRandomTime = true) } }
        val blogs = watched(BlogsViewModel(c).state)
        idleUntil { blogs.value.blogCount == 2 && blogs.value.groups.flatMap { it.blogs }.any { it.switches.isNotEmpty() } }
        val rows = blogs.value.groups.flatMap { it.blogs }.associate { it.entry.key to it.switches }
        assertEquals(listOf("VPN only", "random time"), rows[night])
        assertEquals(emptyList<String>(), rows[SAMPLE_BLOG])
    }

    @Test fun aBlogAddedBesideOthersStartsOffAndSaysSoUntilItsFirstPost() {
        val other = sample.addRepository("other-blog", mapOf("_config.yml" to "title: Another Notebook\n".toByteArray()))
        runBlocking { c.privacy.update(SAMPLE_BLOG) { it.copy(datesByDayOnly = true, plainCommitMessages = true) } }
        val vm = ConnectViewModel(c)
        vm.addBlogTo(runBlocking { c.database.accounts().blog(SAMPLE_BLOG)!!.signInId })
        idleUntil { vm.state.value.repos?.any { it.fullName == "sample/other-blog" } == true }
        vm.choose(vm.state.value.repos!!.first { it.fullName == "sample/other-blog" })
        idleUntil(10_000) { vm.state.value.done }
        assertTrue(vm.state.value.openPrivacy)
        val fresh = runBlocking { c.privacy.get("sample/other-blog@main") }
        assertTrue(fresh.isNew)
        // Not copied from the blog it was added beside.
        assertFalse(fresh.datesByDayOnly || fresh.plainCommitMessages)

        runBlocking { c.publisher.publish(queued("sample/other-blog@main", "First")) }
        assertFalse(runBlocking { c.privacy.get("sample/other-blog@main") }.isNew)
        assertTrue(other.files().keys.any { it.endsWith("-first.md") })
    }

    @Test fun aBlogAddedAgainAfterItsSignInWasRemovedStartsOff() = runBlocking {
        c.setOnlyThroughVpn(night, true)
        assertTrue(c.remove(c.database.accounts().blog(night)!!.signInId) is Removal.Removed)
        c.accounts.save(Account("inkwell", "inkwell-token", "inkwell", "night-notes", "main"))
        assertFalse(c.privacy.get(night).onlyThroughVpn)
    }

    @Test fun thePreviewDoesntReuseTheLastBlogsConnectionAfterASwitch() {
        val photo = sample.apiBase.resolve("assets/images/2025/lighthouse.jpg").toString()
        runBlocking { c.setOnlyThroughVpn(night, true) }
        // A connection made for the open blog, which asks for no VPN, and kept for reuse.
        c.previewFetcher.fetch(photo)
        vpn.up.value = false
        runBlocking { assertTrue(c.open(night)) }
        sample.log.clear()
        c.previewFetcher.fetch(photo)
        assertEquals(emptyList<String>(), sample.log)
    }

    @Test fun aBlogAddedBesideAVpnOnlyBlogStartsWithTheVpnOn() {
        sample.addRepository("other-blog", mapOf("_config.yml" to "title: Another Notebook\n".toByteArray()))
        runBlocking { c.setOnlyThroughVpn(night, true) }
        val vm = ConnectViewModel(c)
        vm.addBlogTo(runBlocking { c.database.accounts().blog(SAMPLE_BLOG)!!.signInId })
        idleUntil { vm.state.value.repos?.any { it.fullName == "sample/other-blog" } == true }
        vpn.connections = 0
        vm.choose(vm.state.value.repos!!.first { it.fullName == "sample/other-blog" })
        idleUntil(10_000) { vm.state.value.done }
        val fresh = runBlocking { c.privacy.get("sample/other-blog@main") }
        assertTrue(fresh.isNew && fresh.onlyThroughVpn)
        assertFalse(fresh.datesByDayOnly || fresh.sendAtRandomTime)
        // Its first read went through the VPN.
        assertTrue(vpn.connections > 0)
    }

    @Test fun aBlogAddedAgainOnItsOwnWithDraftsSaysItsSwitchesStartedOff() = runBlocking {
        val inkwellSignIn = c.database.accounts().blog(night)!!.signInId
        assertTrue(c.remove(c.database.accounts().blog(SAMPLE_BLOG)!!.signInId) is Removal.Removed)
        c.drafts.insert(Draft(blog = night, title = "Didn't go", state = PostState.Failed))
        assertTrue(c.remove(inkwellSignIn) is Removal.Removed)
        val vm = ConnectViewModel(c)
        vm.setToken("inkwell-token")
        vm.checkToken()
        idleUntil { vm.state.value.repos != null }
        vm.choose(vm.state.value.repos!!.first { it.fullName == "inkwell/night-notes" })
        idleUntil(10_000) { vm.state.value.done }
        assertTrue(vm.state.value.openPrivacy)
        assertTrue(c.privacy.get(night).isNew)
    }

    @Test fun aSignInsTokenIsRenewedTheWayItsOwnBlogsAsk() = runBlocking {
        // The sample blog signed in with GitHub, its token about to expire; the other blog asks for a VPN.
        c.accounts.save(Account("sample", "good-token", "sample", "sample-blog", "main", refreshToken = "refresh-1", expiresAt = System.currentTimeMillis() + 60_000))
        c.setOnlyThroughVpn(night, true)
        vpn.up.value = false
        val renewed = c.accounts.current()!!
        // Renewed, and not through the VPN the other sign-in's blog asks for.
        assertTrue(renewed.expiresAt!! > System.currentTimeMillis() + 60 * 60_000)
        assertEquals(0, vpn.connections)
    }

    @Test fun blogAndPrivacyReadsTheOpenBlogsAddressAgainAfterASwitch() {
        val vm = BlogPrivacyViewModel(c)
        idleUntil { vm.state.value.noReplyEmail?.contains("+sample@") == true }
        runBlocking { c.open(night) }
        idleUntil { vm.state.value.noReplyEmail?.contains("+inkwell@") == true }
    }
}
