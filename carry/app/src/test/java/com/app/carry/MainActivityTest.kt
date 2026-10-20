package com.app.carry

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.job
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Activity's own wiring: the folder picker's answer, and the one
 * permission the app asks for. Robolectric shadows the grant, so this
 * proves what Carry does with the picker's answer, not that Android keeps
 * the grant -- that is an emulator check (docs/BACKLOG.md).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class MainActivityTest {
    /**
     * Before the Activity starts: a WorkManager that runs nothing, so the
     * hourly check scheduled in onCreate can't touch the folder mid-test,
     * and an update check already done, so a CI build (which has a build
     * number) doesn't ask GitHub from a unit test.
     */
    @get:Rule(order = 0) val quiet = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Context>()
            WorkManagerTestInitHelper.initializeTestWorkManager(
                app, Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
            )
            app.getSharedPreferences("debug-updates", Context.MODE_PRIVATE).edit()
                .putLong("checked", System.currentTimeMillis()).apply()
        }
    }

    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val folder = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ASync%2Fgym")
    private val wholePhone = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A")

    private val grants get() = context.contentResolver.persistedUriPermissions.map { it.uri }

    @Before
    fun noFolderYet() {
        // Robolectric keeps one Application per test, but say it: every
        // test here starts from a first run.
        assertTrue(grants.isEmpty())
    }

    /** Taps "Choose folder" and answers the system picker with [tree]. */
    private fun pick(tree: Uri) {
        compose.onNodeWithText("Choose folder").performClick()
        compose.waitForIdle()
        val activity = compose.activity
        val asked = shadowOf(activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, asked.intent.action)
        shadowOf(activity).receiveResult(asked.intent, Activity.RESULT_OK, Intent().setData(tree))
        // On a device the picker covered Carry, so it resumes after the answer.
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    }

    private fun waitFor(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    /** Until the resume's folder read has landed: it runs on IO, which the
     *  Compose clock doesn't wait for, so a screen seen before it proves
     *  nothing about what the read leaves there. */
    private fun readsLanded() {
        val model = ViewModelProvider(compose.activity)[CarryViewModel::class.java]
        compose.waitUntil(10_000) { model.viewModelScope.coroutineContext.job.children.none { it.isActive } }
        compose.waitForIdle()
    }

    @Test
    fun `a whole volume picked by mistake is refused, said, and not held`() {
        compose.onNodeWithText("Pick your folder").assertExists()
        pick(wholePhone)
        readsLanded()
        compose.onNodeWithText("That's your whole phone").assertExists()
        assertTrue("a whole-phone grant must not be taken: $grants", grants.isEmpty())
        assertNull(SessionFolder(context).uri)
    }

    @Test
    fun `a folder picked is held and named, and the app moves past the picker`() {
        pick(folder)
        // An empty folder: the coach has not written today's session yet,
        // which is not a lost grant.
        waitFor("Nothing to carry")
        compose.onNodeWithText("Holding: Sync/gym").assertExists()
        assertEquals(listOf(folder), grants)

        compose.onNodeWithText("Forget this folder").performClick()
        waitFor("Pick your folder")
        assertTrue("forgetting hands the grant back: $grants", grants.isEmpty())
    }

    @Test
    fun `notifications are not asked for before there is a folder to watch`() {
        compose.onNodeWithText("Pick your folder").assertExists()
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
    }

    @Test
    fun `notifications are asked for once a folder is held, and only once`() {
        pick(folder)
        waitFor("Nothing to carry")
        val request = shadowOf(compose.activity).lastRequestedPermission
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), request?.requestedPermissions?.toList())
        // Not marked until the owner answers, so a launch that throws
        // doesn't burn the only ask the app makes.
        assertFalse(SessionWatchWorker.asked(context))

        @Suppress("DEPRECATION")
        compose.activity.onRequestPermissionsResult(
            request!!.requestCode, request.requestedPermissions, intArrayOf(PackageManager.PERMISSION_DENIED),
        )
        assertTrue(SessionWatchWorker.asked(context))

        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        // Robolectric keeps the last request, so a new ask is a new object.
        assertSame("asked again after an answer", request, shadowOf(compose.activity).lastRequestedPermission)
    }
}
