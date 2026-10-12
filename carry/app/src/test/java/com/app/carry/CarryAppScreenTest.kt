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
        File(out, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun `the first run explains what the folder is for`() {
        compose.setContent { CarryApp(CarryState.NeedsFolder(), onChooseFolder = {}) }
        compose.onNodeWithText("Pick your folder").assertIsDisplayed()
        compose.onNodeWithText("Choose folder").assertIsDisplayed()
        shoot("04-first-run")
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
        shoot("06-no-session")
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
        shoot("07-loading")
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
        titleFor(CarryState.Unreadable("session-2026-10-17-2G.json", wrongSchema = true), "08-wrong-schema")
        titleFor(CarryState.Unreadable("session-2026-10-17-2G.json", wrongSchema = false))
        titleFor(CarryState.NoSession)
        titleFor(CarryState.Stalled, "09-stalled")
        assertEquals("each state needs its own words", 4, titles.size)
    }

    @Test
    fun `a ready session shows the sheet`() {
        compose.setContent { CarryApp(CarryState.Ready(SampleSession.today), onChooseFolder = {}) }
        compose.onNodeWithText("Carry these in").assertIsDisplayed()
    }
}
