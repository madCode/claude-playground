package com.app.carry

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Invented weights and movements; the repository is public. What is real is
 * the SHAPE — a handful of sessions, almost no repeated movements, and
 * therefore almost nothing the coach can say.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SinceScreenTest {
    @get:Rule val compose = createComposeRule()
    private val out = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    private fun lift(date: String, name: String, family: String, weight: Double) =
        Growth.Lift(date, name, family, weight, 10)

    private val since = Growth.Since(
        sessions = 5, sets = 20, from = "2026-09-13", to = "2026-10-11",
        peaks = listOf(
            Growth.Peak("legs", lift("2026-09-27", "sumo squat", "legs", 50.0), listOf(40.0, 30.0, 50.0)),
            Growth.Peak("pull", lift("2026-09-20", "low row", "pull", 30.0), listOf(15.0, 30.0, 30.0)),
        ),
        nearBump = listOf(Growth.Bump("single-arm row", "pull", 40.0, 2, 3)),
        days = listOf(
            Growth.Day("2026-10-11", "3G", 4, lift("2026-10-11", "deadlift", "combo", 30.0)),
            Growth.Day("2026-09-27", "3G", 6, lift("2026-09-27", "sumo squat", "legs", 50.0)),
            Growth.Day("2026-09-13", "2G", 0, null),
        ),
        movements = 19, repeated = 1,
    )

    @Test
    fun `the whole folder, read back`() {
        compose.setContent { SinceScreen(since) }
        compose.onNodeWithText("5 sessions").assertIsDisplayed()
        // The one actionable thing in the data leads.
        compose.onNodeWithText("single-arm row").assertIsDisplayed()
        // And the question only this screen can answer.
        compose.onNodeWithText("Sun 27 Sep", substring = true).assertIsDisplayed()
        compose.onNodeWithText("6 lifts").assertIsDisplayed()
        shoot("17-since")
    }

    @Test
    fun `an empty folder says what will fill it, rather than nothing`() {
        // A screen that is blank because there is no data looks exactly
        // like a screen that is blank because it is broken.
        val empty = since.copy(
            sessions = 0, sets = 0, from = null, to = null,
            peaks = emptyList(), nearBump = emptyList(), days = emptyList(),
            movements = null, repeated = null,
        )
        compose.setContent { SinceScreen(empty) }
        compose.onNodeWithText("Nothing logged yet", substring = true).assertIsDisplayed()
        // No shot: this is one of the simple centred screens that
        // `captureToImage` returns as a flat colour (see the backlog). The
        // assertion was always the real test; claiming a picture that is
        // blank is how seven of twelve screenshots lied in cycle 11.
    }

    @Test
    fun `a count the files never justified is not shown at all`() {
        // Files from before the runner said what counts as the same
        // exercise. A guess here would be a confident wrong number on the
        // one band whose whole job is to explain why the numbers are thin.
        compose.setContent { SinceScreen(since.copy(movements = null, repeated = null)) }
        compose.onNodeWithText("Why the coach is often quiet".uppercase()).assertDoesNotExist()
        compose.onNodeWithText("Heaviest, by what it trains".uppercase()).assertIsDisplayed()
    }

    private fun shoot(name: String) {
        compose.waitForIdle()
        val shot = compose.onRoot().captureToImage().asAndroidBitmap()
        check(!shot.isFlat()) { "$name captured a blank image, so it proves nothing" }
        File(out, "$name.png").outputStream().use {
            shot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
