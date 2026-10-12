package com.app.carry

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
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
    fun `a ready session shows the sheet`() {
        compose.setContent { CarryApp(CarryState.Ready(SampleSession.today), onChooseFolder = {}) }
        compose.onNodeWithText("Carry these in").assertIsDisplayed()
    }
}
