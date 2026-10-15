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
            Growth.Day("2026-10-11:3G", "2026-10-11", "3G", 4, lift("2026-10-11", "deadlift", "combo", 30.0)),
            Growth.Day("2026-09-27:3G", "2026-09-27", "3G", 6, lift("2026-09-27", "sumo squat", "legs", 50.0)),
            Growth.Day("2026-09-13:2G", "2026-09-13", "2G", 0, null),
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
    fun `a class with no weighted set in it is still a class you did`() {
        // `sets` counts weighted LIFTS, and the screen used to hide itself
        // entirely when that was zero -- so a bodyweight block, or a
        // 45-second hold ticked off with `done`, got "Nothing logged yet"
        // over a date range saying when it happened. The one screen that
        // can answer "what did I do that day" was unreachable for exactly
        // the days with the least else to show.
        val bodyweight = since.copy(
            sessions = 1, sets = 0, from = "2026-09-27", to = "2026-09-27",
            peaks = emptyList(), nearBump = emptyList(),
            days = listOf(Growth.Day("2026-09-27:3G", "2026-09-27", "3G", 0, null)),
            movements = null, repeated = null,
        )
        compose.setContent { SinceScreen(bodyweight) }
        compose.onNodeWithText("Nothing logged yet", substring = true).assertDoesNotExist()
        // The band that opens a day, which the early return skipped.
        compose.onNodeWithText("Every session".uppercase()).assertIsDisplayed()
        compose.onNodeWithText("3G · Sun 27 Sep", substring = true).assertIsDisplayed()
        // And the header does not print "0 sets" beside that date, which
        // is the one count that reads as "nothing happened".
        compose.onNodeWithText("0 sets", substring = true).assertDoesNotExist()
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

    /** Every setup done once, which is what the runner's streak counts
     *  produce for this owner today: of 32 distinct (setup, rep-count)
     *  lines in their history, most would arrive at 1 of 3. */
    private fun allAtOne(n: Int) = (1..n).map {
        Growth.Bump("movement $it", listOf("legs", "push", "pull", "core")[it % 4], 20.0 + it, 1, 3)
    }

    @Test
    fun `a movement done once is not called close to a bump`() {
        // Rendered with real numbers this band was ELEVEN rows, every one a
        // single lit pip of three, filling the whole screen under a heading
        // saying they were all about to go up. The runner counts a streak
        // from the first clean set and this owner starts nearly every
        // movement fresh, so that is the normal case, not the edge.
        compose.setContent { SinceScreen(since.copy(nearBump = allAtOne(11))) }
        compose.onNodeWithText("Close to a bump".uppercase()).assertDoesNotExist()
        compose.onNodeWithText("How close each one is".uppercase()).assertIsDisplayed()
    }

    @Test
    fun `the progress band cannot take the whole screen`() {
        // Five rows, closest first, and the rest counted rather than
        // silently dropped -- a list that stops at five with no sign of it
        // reads as a folder with five things in it.
        compose.setContent { SinceScreen(since.copy(nearBump = allAtOne(11))) }
        compose.onNodeWithText("movement 1").assertIsDisplayed()
        compose.onNodeWithText("movement 5").assertIsDisplayed()
        compose.onNodeWithText("movement 6").assertDoesNotExist()
        compose.onNodeWithText("and 6 more").assertIsDisplayed()
        // The bands under it are what the screen is otherwise for, and they
        // were below the fold with eleven rows above them.
        compose.onNodeWithText("Heaviest, by what it trains".uppercase()).assertIsDisplayed()
        shoot("23-progress-band")
    }

    @Test
    fun `a band that fits says nothing about more`() {
        compose.setContent { SinceScreen(since.copy(nearBump = allAtOne(3))) }
        compose.onNodeWithText("movement 3").assertIsDisplayed()
        compose.onNodeWithText("more", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the one closest to a bump leads`() {
        // The cap can only be honest if the sort is: five rows chosen from
        // eleven must be the five furthest along, not the first five the
        // sheet happened to list.
        val mixed = allAtOne(6) + listOf(Growth.Bump("nearly there", "legs", 40.0, 2, 3))
        compose.setContent { SinceScreen(since.copy(nearBump = mixed.sortedByDescending { it.done.toDouble() / it.needed })) }
        compose.onNodeWithText("nearly there").assertIsDisplayed()
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
