package com.app.jekyllposter.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.waitUntilLooped
import com.app.jekyllposter.ui.theme.PosterTheme
import com.app.jekyllposter.update.AppUpdates
import com.app.jekyllposter.update.DebugUpdates
import com.app.jekyllposter.update.SelfUpdater
import com.app.jekyllposter.update.UpdateProblem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** The line on Home that says a newer debug build is out, and what a tap on it does. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class UpdateNoticeTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val app get() = compose.activity.application as TestApp

    @After fun close() = app.github.close()

    private var step = SelfUpdater.Step.Install
    private val installs = mutableListOf<Int>()
    private val installing = MutableStateFlow(false)
    private var problem: UpdateProblem? = null

    private val updater = object : SelfUpdater {
        override val installing = this@UpdateNoticeTest.installing
        override fun next(build: Int, publishing: Boolean, canInstall: Boolean) = step
        override fun start() {}
        override fun newerBuild(build: Int) {}
        override fun canInstall() = true
        override suspend fun install(build: Int, publishing: () -> Boolean): UpdateProblem? { installs += build; return problem }
        override fun resumed(activity: Activity) {}
        override fun paused() {}
    }

    private fun updates(latest: Int?): AppUpdates {
        val prefs = app.getSharedPreferences("notice-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
        val check = DebugUpdates(prefs, 130, fetch = { latest?.let { """{"name":"jekyll-poster-debug-$it.apk"}""" } })
        runBlocking { check.checkIfDue() }
        return AppUpdates(check, updater, publishing = { false }, scope = CoroutineScope(Dispatchers.Main))
    }

    private fun show(updates: AppUpdates) = compose.setContent {
        PosterTheme {
            UpdateNotice(updates)
            UpdateProblems(updates)
        }
    }

    private fun started(): Intent? = shadowOf(compose.activity).nextStartedActivity

    @Test fun nothingIsShownWithoutANewerBuild() {
        show(updates(latest = 130))
        compose.onNodeWithText("is out", substring = true).assertDoesNotExist()
    }

    @Test fun aTapInstallsTheNewerBuild() {
        show(updates(latest = 140))
        compose.onNodeWithText("Build 140 is out").assertHasClickAction().performClick()
        compose.waitUntilLooped { installs.isNotEmpty() }
        assertEquals(listOf(140), installs)
    }

    @Test fun whileInstallingItSaysSoAndCantBeTappedAgain() {
        installing.value = true
        show(updates(latest = 140))
        compose.onNodeWithText("Getting build 140…").assertIsNotEnabled()
    }

    @Test fun withoutPermissionItOpensAndroidsSwitchForThisApp() {
        step = SelfUpdater.Step.AllowInstalls
        show(updates(latest = 140))
        compose.onNodeWithText("Build 140 is out").performClick()
        val opened = started()!!
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, opened.action)
        assertEquals("package:${app.packageName}", opened.dataString)
        assertEquals("Allow Jekyll Poster to install updates, then tap the line again.", ShadowToast.getTextOfLatestToast())
        assertEquals(emptyList<Int>(), installs)
    }

    @Test fun aPostGoingOutIsLeftToFinish() {
        step = SelfUpdater.Step.WaitForPost
        show(updates(latest = 140))
        compose.onNodeWithText("Build 140 is out").performClick()
        assertEquals(SelfUpdater.WAIT_FOR_POST, ShadowToast.getTextOfLatestToast())
        assertEquals(emptyList<Int>(), installs)
        assertNull(started())
    }

    @Test fun aBuildGivenUpOnOpensTheDownloadInABrowser() {
        step = SelfUpdater.Step.Browser
        show(updates(latest = 140))
        compose.onNodeWithText("Build 140 is out").performClick()
        assertEquals(DebugUpdates.DOWNLOAD.toString(), started()!!.dataString)
    }

    @Test fun aFailedInstallIsSaidOnceWithTheLinkWhenItHelps() {
        problem = UpdateProblem("Couldn't download the update: the download answered 404.", offerLink = true)
        val updates = updates(latest = 140)
        show(updates)
        compose.onNodeWithText("Build 140 is out").performClick()
        compose.waitUntilLooped { updates.problem.value == null && installs.isNotEmpty() }
        assertEquals("Couldn't download the update: the download answered 404.", ShadowToast.getTextOfLatestToast())
        assertEquals(DebugUpdates.DOWNLOAD.toString(), started()!!.dataString)
        assertNull("said once", started())
    }
}
