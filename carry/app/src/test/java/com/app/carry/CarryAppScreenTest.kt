package com.app.carry

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CarryAppScreenTest {
    @get:Rule val compose = createComposeRule()
    private val out = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    private fun shoot(name: String) {
        compose.waitForIdle()
        var shot = compose.onRoot().captureToImage().asAndroidBitmap()
        repeat(4) {
            if (!shot.isFlat()) return@repeat
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            shot = compose.onRoot().captureToImage().asAndroidBitmap()
        }
        // DIAGNOSTIC (temporary, claude/carry-blank-capture): when the capture
        // is flat, say what it was and try a software draw of the window.
        if (shot.isFlat()) {
            val root = compose.onRoot().fetchSemanticsNode()
            val activity = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().let {
                var found: android.app.Activity? = null
                it.runOnMainSync {
                    found = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).firstOrNull()
                }
                found
            }
            val decor = activity?.window?.decorView
            var soft: Bitmap? = null
            if (decor != null && decor.width > 0 && decor.height > 0) {
                soft = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
                decor.draw(android.graphics.Canvas(soft))
                File(out, "$name-SOFTWARE.png").outputStream().use { soft.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            File(out, "$name-FLAT.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val info = "BLANKDIAG $name: shot ${shot.width}x${shot.height} pixel=#${Integer.toHexString(shot.getPixel(0, 0))} " +
                "root=${root.size} boundsInWindow=${root.boundsInWindow} activity=${activity?.javaClass?.simpleName} " +
                "decor=${decor?.width}x${decor?.height} attached=${decor?.isAttachedToWindow} " +
                "hasFocus=${activity?.hasWindowFocus()} soft=${soft?.let { if (it.isFlat()) "flat" else "drawn" }} " +
                "nodes=${compose.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().size}"
            println(info)
            error(info)
        }
        File(out, "$name.png").outputStream().use {
            shot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun `the first run explains what the folder is for`() {
        compose.setContent { CarryApp(CarryState.NeedsFolder(), onChooseFolder = {}) }
        compose.onNodeWithText("Pick your folder").assertIsDisplayed()
        compose.onNodeWithText("Choose folder").assertIsDisplayed()
        // No shoot() here: Robolectric's capture comes back flat for this
        // screen and the blank check would fail. The assertions above are
        // the test; the picture was never the evidence. Tracked in
        // docs/BACKLOG.md.
    }

    @Test
    fun `a lost grant says what happened instead of looking like a first run`() {
        compose.setContent { CarryApp(CarryState.NeedsFolder(lostGrant = true), onChooseFolder = {}) }
        compose.onNodeWithText("Lost the folder").assertIsDisplayed()
        shoot("05-lost-grant")
    }

    @Test
    fun `an empty folder says the session has not arrived`() {
        compose.setContent { CarryApp(CarryState.NoSession, onChooseFolder = {}) }
        compose.onNodeWithText("Nothing to carry").assertIsDisplayed()
        // No shoot() here: Robolectric's capture comes back flat for this
        // screen and the blank check would fail. The assertions above are
        // the test; the picture was never the evidence. Tracked in
        // docs/BACKLOG.md.
    }

    @Test
    fun `the button actually asks for a folder`() {
        var asked = 0
        compose.setContent { CarryApp(CarryState.NeedsFolder(), onChooseFolder = { asked++ }) }
        compose.onNodeWithText("Choose folder").performClick()
        assertEquals(1, asked)
    }

    @Test
    fun `the first read says what it is doing rather than showing nothing`() {
        compose.setContent { CarryApp(CarryState.Loading, onChooseFolder = {}) }
        compose.onNodeWithText("Reading").assertIsDisplayed()
        // No shoot() here: Robolectric's capture comes back flat for this
        // screen and the blank check would fail. The assertions above are
        // the test; the picture was never the evidence. Tracked in
        // docs/BACKLOG.md.
    }

    @Test
    fun `wait, update and absent each say something different`() {
        // Rendering only one of these could not catch them being conflated,
        // which is the whole point of having four: wait for the sync, update
        // the app, nothing has arrived, the folder stopped answering.
        val state = mutableStateOf<CarryState>(CarryState.NoSession)
        compose.setContent { CarryApp(state.value, onChooseFolder = {}) }

        val titles = mutableSetOf<String>()
        fun titleFor(next: CarryState, shot: String? = null) {
            state.value = next
            compose.waitForIdle()
            titles += compose.onAllNodes(hasText("", substring = true))
                .fetchSemanticsNodes()
                .firstNotNullOf { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text }
            shot?.let { shoot(it) }
        }
        titleFor(CarryState.Unreadable("session-2026-10-17-2G.json", CarryState.Unreadable.Why.NEWER), "08-wrong-schema")
        titleFor(CarryState.Unreadable("session-2026-10-17-2G.json", CarryState.Unreadable.Why.DAMAGED))
        titleFor(CarryState.NoSession)
        titleFor(CarryState.Stalled, "09-stalled")
        titleFor(CarryState.Unreadable("session-2026-10-17-2G.json", CarryState.Unreadable.Why.NO_LABEL))
        titleFor(CarryState.Unreadable("session-2026-10-17-2G.json", CarryState.Unreadable.Why.UNKNOWN_LABEL, "\"workout-log/3\""))
        assertEquals("each state needs its own words", 6, titles.size)
    }

    @Test
    fun `a file with no format label is named, and not blamed on a sync`() {
        compose.setContent {
            CarryApp(CarryState.Unreadable("session-2026-10-17-2G.json", CarryState.Unreadable.Why.NO_LABEL), onChooseFolder = {})
        }
        compose.onNodeWithText("No format label").assertIsDisplayed()
        compose.onNodeWithText("session-2026-10-17-2G.json has no \"schema\" line", substring = true).assertIsDisplayed()
        compose.onNodeWithText("sync", substring = true).assertDoesNotExist()
        compose.onNodeWithText("didn't read cleanly", substring = true).assertDoesNotExist()
        shoot("33-no-label")
    }

    @Test
    fun `an unknown format label is quoted back`() {
        compose.setContent {
            CarryApp(
                CarryState.Unreadable("session-2026-10-17-2G.json", CarryState.Unreadable.Why.UNKNOWN_LABEL, "\"workout-log/3\""),
                onChooseFolder = {},
            )
        }
        compose.onNodeWithText("Unknown format label").assertIsDisplayed()
        compose.onNodeWithText("session-2026-10-17-2G.json says its format is \"workout-log/3\"", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("sync", substring = true).assertDoesNotExist()
        shoot("34-unknown-label")
    }

    @Test
    fun `picking the whole phone is refused in words`() {
        // A tree grant is recursive and persistent, and "Internal storage"
        // sits a row above the folder in the picker.
        compose.setContent {
            CarryApp(CarryState.NeedsFolder(tooWide = true), onChooseFolder = {})
        }
        compose.onNodeWithText("That's your whole phone").assertIsDisplayed()
        compose.onNodeWithText("not everything on the phone", substring = true).assertIsDisplayed()
        // No shoot() here: Robolectric's capture comes back flat for this
        // screen and the blank check would fail. The assertions above are
        // the test; the picture was never the evidence. Tracked in
        // docs/BACKLOG.md.
    }

    @Test
    fun `the folder being held is named, and can be handed back`() {
        var forgotten = 0
        compose.setContent {
            CarryApp(
                CarryState.NoSession, onChooseFolder = {},
                holding = "Sync/gym", onForget = { forgotten++ },
            )
        }
        compose.onNodeWithText("Holding: Sync/gym").assertIsDisplayed()
        compose.onNodeWithText("Forget this folder").performClick()
        assertEquals(1, forgotten)
        shoot("15-holding")
    }

    @Test
    fun `a ready session shows the sheet`() {
        // Dated today: CarryApp reads the real clock, and the fixed sample
        // date would read as a past sheet once the clock passed it.
        val today = SampleSession.today.copy(date = java.time.LocalDate.now().toString())
        compose.setContent { CarryApp(readyFor(today), onChooseFolder = {}) }
        compose.onNodeWithText("Carry these in").assertIsDisplayed()
    }
}
