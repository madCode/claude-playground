package com.app.carry

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The folder picker pauses Carry just as leaving it does. A background install then would end
 * Carry under the picker, and the folder picked would arrive nowhere. Drives the real Activity
 * and the real reasons [LiveUpdates] waits; only Android's installer is faked.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class UpdateWaitsForPickerTest {
    @get:Rule val tmp = TemporaryFolder()
    @get:Rule val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val ours = ApkFacts(app.packageName, 130, setOf("our-key"))
    private val installs = mutableListOf<Int>()
    private val scenarios = mutableListOf<ActivityScenario<MainActivity>>()

    @Before fun setUp() {
        // Workers never run here, and no update check asks GitHub from a unit test.
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor { }.build())
        app.getSharedPreferences("debug-updates", Context.MODE_PRIVATE).edit()
            .putLong("checked", System.currentTimeMillis()).commit()
        LiveUpdates.prefsOf(app).edit().clear().commit()
        UpdateInstaller.finish()
    }

    @After fun tearDown() {
        scenarios.forEach { it.close() }
        UpdateInstaller.finish()
        UpdateInstaller.inFront = false
    }

    /** A new build downloaded and waiting for Carry to close. Busy is decided as on a phone. */
    private val updates by lazy {
        LiveUpdates(
            app, 130, prefs = LiveUpdates.prefsOf(app), dir = tmp.newFolder("updates"),
            download = { it.writeText("apk-140") },
            inspect = { ApkFacts(app.packageName, 140, setOf("our-key")) },
            installed = { ours },
            stage = { 7 }, commit = { _, build, _ -> installs += build }, abandon = {},
            canInstallNow = { true }, work = { WorkManager.getInstance(app) },
        ).also { runBlocking { it.fetch(140) } }
    }

    private fun inBackground() = runBlocking {
        updates.installInBackground().also { if (it == LiveUpdates.Outcome.Started) UpdateInstaller.finish() }
    }

    private fun launch(intent: Intent? = null) =
        (if (intent == null) ActivityScenario.launch(MainActivity::class.java) else ActivityScenario.launch(intent))
            .also { scenarios += it }

    /** Taps "Choose folder"; the picker covers Carry. Returns what was sent for. */
    private fun openPicker(scenario: ActivityScenario<MainActivity>): Intent {
        compose.onNodeWithText("Choose folder").performClick()
        compose.waitForIdle()
        lateinit var asked: Intent
        scenario.onActivity { asked = shadowOf(it).nextStartedActivityForResult.intent }
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, asked.action)
        coveredBy(scenario)
        return asked
    }

    private fun coveredBy(scenario: ActivityScenario<MainActivity>) {
        scenario.moveToState(Lifecycle.State.CREATED)
        // A local build has no updater to keep this; a CI build's does, on pause.
        UpdateInstaller.inFront = false
    }

    @Test
    fun `an install waits while the folder picker is out, turned and all, and goes once it answers`() {
        val scenario = launch()
        val asked = openPicker(scenario)
        assertEquals("the picker is out", LiveUpdates.Outcome.Busy, inBackground())

        // Turned while the picker was up: Carry is made again, still waiting for the answer.
        scenario.recreate()
        assertEquals("the picker is out", LiveUpdates.Outcome.Busy, inBackground())

        scenario.onActivity { shadowOf(it).receiveResult(asked, Activity.RESULT_CANCELED, null) }
        coveredBy(scenario)
        assertEquals("answered, and Carry is closed", LiveUpdates.Outcome.Started, inBackground())
        assertEquals(listOf(140), installs)
    }

    @Test
    fun `another app opened with nothing to answer, like the share sheet, doesn't hold the install`() {
        val scenario = launch()
        scenario.onActivity { it.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/carry-debug.apk"))) }
        coveredBy(scenario)
        assertEquals(LiveUpdates.Outcome.Started, inBackground())
    }

    @Test
    fun `a picker out when Carry went away still holds, until Carry starts afresh from the launcher`() {
        openPicker(launch())
        // Android dropped Carry's process: a later one knows only what's on disk.
        assertEquals(LiveUpdates.Outcome.Busy, inBackground())

        // The session notice reopens Carry onto the task the picker is still part of.
        val notice = Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        coveredBy(launch(notice))
        assertEquals("the task may still bring the picker back", LiveUpdates.Outcome.Busy, inBackground())

        // From the launcher with nothing to restore, no picker can answer any more.
        coveredBy(launch())
        assertEquals(LiveUpdates.Outcome.Started, inBackground())
    }
}
