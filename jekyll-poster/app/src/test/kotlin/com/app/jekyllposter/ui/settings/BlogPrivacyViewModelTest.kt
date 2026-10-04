package com.app.jekyllposter.ui.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.idleUntil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class BlogPrivacyViewModelTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container

    @Before fun signIn() = runBlocking {
        c.accounts.save(Account("sample", "good-token", "sample", "sample-blog", "main"))
        c.blogs.refresh()
        Unit
    }

    @After fun close() = app.github.close()

    @Test fun itShowsTheNoReplyAddressTheVisibilityAndTheSitesZone() {
        val vm = BlogPrivacyViewModel(c) { ZoneId.of("Asia/Tokyo") }
        idleUntil { vm.state.value.noReplyEmail != null && vm.state.value.public != null }
        assertEquals("1001+sample@users.noreply.github.com", vm.state.value.noReplyEmail)
        assertEquals("America/Los_Angeles", vm.state.value.siteZone)
        assertEquals("Asia/Tokyo", vm.state.value.phoneZone)
        // Everything starts as GitHub and Jekyll would have it.
        assertEquals(false, vm.state.value.commitAsNoReply)
        assertEquals(false, vm.state.value.removeTrackingCodes)
    }

    @Test fun usingThePhonesZoneChangesOnlyThatLineOfTheConfig() {
        val before = app.github.text("_config.yml")!!
        val vm = BlogPrivacyViewModel(c) { ZoneId.of("Asia/Tokyo") }
        vm.useThisPhonesZone()
        idleUntil(10_000) { vm.state.value.siteZone == "Asia/Tokyo" && !vm.state.value.settingZone }
        val after = app.github.text("_config.yml")!!
        assertEquals(before.replace("timezone: America/Los_Angeles", "timezone: Asia/Tokyo"), after)
        assertEquals("Set the site's time zone to Asia/Tokyo", app.github.commits.getValue(app.github.head).message)
        assertTrue(vm.state.value.message!!.startsWith("The site's time zone is now Asia/Tokyo"))
    }

    @Test fun aConfigChangedMeanwhileIsNotOverwritten() {
        val vm = BlogPrivacyViewModel(c) { ZoneId.of("Asia/Tokyo") }
        // Edited on a laptop after the phone read it, in the moment before the commit.
        app.github.beforeRefUpdate = { app.github.push("Laptop", mapOf("_config.yml" to "title: Changed\n")) }
        vm.useThisPhonesZone()
        idleUntil(10_000) { vm.state.value.message != null }
        assertEquals("title: Changed\n", app.github.text("_config.yml"))
        assertEquals("_config.yml changed on GitHub just now. Try again.", vm.state.value.message)
    }
}
