package com.app.carry

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws Today with real graphics, leaves a PNG in build/screenshots, and
 * asserts the rules the design rests on. The assertions matter more than the
 * picture: a screenshot of the wrong moment looks exactly like a correct
 * one, so nothing here is judged by eye.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TodayScreenTest {
    @get:Rule val compose = createComposeRule()

    private val out = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    private fun shoot(name: String) {
        compose.waitForIdle()
        File(out, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun `today shows the change, the held weights and the tail`() {
        compose.setContent { TodayScreen(SampleSession.today) }

        // Asserting the PROPERTY, not the string. Building the expectation
        // from formatDate would move with the bug it is meant to catch --
        // if the formatter regressed to returning its input, both sides
        // would agree and the test would pass.
        compose.onNodeWithText("2G · 2026-10-17").assertDoesNotExist()
        compose.onNodeWithText("2G · ", substring = true).assertIsDisplayed()
        // The one number you would get wrong from memory, named rather than
        // left to be subtracted.
        compose.onNodeWithText("+5").assertIsDisplayed()
        compose.onNodeWithText("45 lb").assertIsDisplayed()
        compose.onNodeWithText("15 lb").assertIsDisplayed()

        // Everything with no number is one tail line, not six rows.
        compose.onNodeWithText("By feel: lateral raise, tricep extension · bench").assertIsDisplayed()
        compose.onNodeWithText("No weight: squat, sit-up").assertIsDisplayed()

        shoot("01-today")
    }

    @Test
    fun `a borrowed floor block says so before any number`() {
        // A substitute mistaken for the real thing is worse than no sheet:
        // 78% of the real history was logged this way.
        val borrowed = SampleSession.today.copy(classTag = "3G", borrowedFrom = "2G")
        compose.setContent { TodayScreen(borrowed) }
        compose.onNodeWithText(
            "⚠ No 3G intel. This is the 2G floor block — check the board."
        ).assertIsDisplayed()
        shoot("02-borrowed")
    }

    @Test
    fun `every day but today is marked, and today is not`() {
        // All four branches. Two of them shipped un-rendered by any test
        // last time, and nothing asserted that TODAY stays quiet -- a
        // warning that always shows is the cry-wolf failure, and this
        // line borrows the borrowed-block warning's voice, so devaluing
        // it costs more than itself.
        val session = SampleSession.today     // dated 2026-10-17
        val warning = hasText("\u26a0 ", substring = true)

        compose.setContent { TodayScreen(session, java.time.LocalDate.of(2026, 10, 17)) }
        compose.onNode(warning).assertDoesNotExist()
    }

    @Test
    fun `an older session is marked above the numbers`() {
        compose.setContent { TodayScreen(SampleSession.today, java.time.LocalDate.of(2026, 10, 19)) }
        compose.onNodeWithText(
            "\u26a0 This is an older session, not today's. Your folder may not have synced."
        ).assertIsDisplayed()
        shoot("10-stale")
    }

    @Test
    fun `a session dated ahead, and one with an unreadable date, each say so`() {
        compose.setContent { TodayScreen(SampleSession.today, java.time.LocalDate.of(2026, 10, 1)) }
        compose.onNodeWithText("\u26a0 This session is dated ahead of today.").assertIsDisplayed()
    }

    @Test
    fun `a date the app cannot read is admitted on screen`() {
        val odd = SampleSession.today.copy(date = "whenever")
        compose.setContent { TodayScreen(odd, java.time.LocalDate.of(2026, 10, 17)) }
        compose.onNodeWithText("\u26a0 Carry can't tell what day this session is for.").assertIsDisplayed()
    }

    @Test
    fun `a session with nothing to carry still renders`() {
        // The common case on a 3G day, and the one most likely to crash:
        // no weighted rows at all.
        val empty = SampleSession.today.copy(sets = emptyList())
        compose.setContent { TodayScreen(empty) }
        compose.onNodeWithText("Carry these in").assertIsDisplayed()
        shoot("03-empty")
    }

    @Test
    fun `a row whose family is unknown still draws`() {
        // The knowledge base is still missing exercises, so an unrecognised
        // family must mean no colour rather than a wrong one.
        val unknown = SampleSession.today.copy(
            sets = SampleSession.today.sets.map { it.copy(family = "nonsense") }
        )
        compose.setContent { TodayScreen(unknown) }
        compose.onNodeWithText("45 lb").assertIsDisplayed()
        assertTrue(Look.family("nonsense") == null)
    }
}
