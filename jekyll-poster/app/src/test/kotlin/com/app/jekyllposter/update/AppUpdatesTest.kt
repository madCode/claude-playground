package com.app.jekyllposter.update

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.testutil.idleUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdatesTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private fun prefs(name: String) = app.getSharedPreferences(name, Context.MODE_PRIVATE).apply { edit().clear().commit() }

    private var step = SelfUpdater.Step.Install
    private var vpnMissing = false
    private var publishing = false

    /** What each install call was told to wait for, at the moment it asked. */
    private val asked = mutableListOf<Hold?>()
    private val answers = ArrayDeque<UpdateProblem?>()

    private val updater = object : SelfUpdater {
        override val installing = MutableStateFlow(false)
        override fun next(build: Int, publishing: Boolean, canInstall: Boolean) = step
        override fun start() {}
        override fun newerBuild(build: Int) {}
        override fun canInstall() = true
        override suspend fun install(build: Int, hold: () -> Hold?): UpdateProblem? {
            asked += hold()
            return answers.removeFirstOrNull()
        }
        override fun resumed(activity: Activity) {}
        override fun paused() {}
    }

    private val holds = UpdateHolds(prefs("holds-test"))

    private val updates = AppUpdates(
        DebugUpdates(prefs("app-updates-test"), 130, fetch = { null }),
        updater,
        publishing = { publishing },
        holds = holds,
        waitingForVpn = { vpnMissing },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private fun tap() = runBlocking { updates.tap(140) }

    @Test fun theBrowserIsNeverOfferedAroundTheVpnTheWriterAskedFor() {
        step = SelfUpdater.Step.Browser
        assertEquals(AppUpdates.Tap.OpenDownload, tap())
        vpnMissing = true
        assertEquals(AppUpdates.Tap.Say(SelfUpdater.WAIT_FOR_VPN), tap())
        assertEquals(AppUpdates.Tap.Say(SelfUpdater.WAIT_FOR_VPN), runBlocking { updates.openDownload() })
    }

    @Test fun anInstallWaitsForAPostGoingOutOrAPostOpen() {
        // Each install runs on its own; it asks just before handing the build over.
        tap()
        idleUntil { asked.size == 1 }
        publishing = true
        tap()
        idleUntil { asked.size == 2 }
        publishing = false
        holds.hold()
        tap()
        idleUntil { asked.size == 3 }
        assertEquals(listOf(null, Hold.Post, Hold.Screen), asked)
    }

    @Test fun anInstallHeldByThePostGoesOnceThePostIsLeft() {
        holds.hold()
        answers += UpdateProblem(SelfUpdater.WAIT_FOR_SCREEN, offerLink = false, resumes = true)
        tap()
        idleUntil { updates.problem.value != null }
        assertEquals(listOf<Hold?>(Hold.Screen), asked)
        assertEquals("not while the post is open", 1, asked.size)
        holds.release()
        idleUntil { asked.size == 2 }
        assertEquals(listOf(Hold.Screen, null), asked)
    }
}

@RunWith(AndroidJUnit4::class)
class UpdateHoldsTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("holds", Context.MODE_PRIVATE).apply { edit().clear().commit() }

    @Test fun aScreenLeftOpenWhenTheProcessDiedStillHoldsTheBackgroundUntilAFreshStart() {
        val before = UpdateHolds(prefs)
        before.hold()
        assertTrue(before.held)
        // The process dies with the editor open; a worker starts a new one.
        val after = UpdateHolds(prefs)
        assertFalse("nothing open in this process", after.held)
        assertTrue(after.heldOrLeftOpen)
        after.freshStart()
        assertFalse(after.heldOrLeftOpen)
    }

    @Test fun twoScreensHoldUntilBothAreLeft() {
        val holds = UpdateHolds(prefs)
        holds.hold()
        holds.hold()
        holds.release()
        assertTrue(holds.heldOrLeftOpen)
        holds.release()
        assertFalse(holds.heldOrLeftOpen)
    }
}
