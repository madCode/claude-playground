package com.app.carry

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
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
     *  produce for this owner today: most lines on a sheet written now
     *  would arrive at 1 of 3. */
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
        // And not "how close each one is" either: "each one" read as
        // every movement trained, when the band is five exclusions deep.
        // The heading claims only what every row in it is.
        compose.onNodeWithText("Part way to a bump".uppercase()).assertIsDisplayed()
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
        // And it reads out as what it is. The pips are bare Boxes with no
        // semantics, so without a row label the band's entire content --
        // the streak, which is what its heading asks about -- is inaudible.
        compose.onNodeWithContentDescription("movement 1, 1 of 3 clean sets, next at 21 pounds")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("and 6 more, not shown").assertIsDisplayed()
        shoot("23-progress-band")
    }

    @Test
    fun `a band that fits says nothing about more`() {
        compose.setContent { SinceScreen(since.copy(nearBump = allAtOne(3))) }
        compose.onNodeWithText("movement 3").assertIsDisplayed()
        // Anchored, not a bare "more": the quiet band says "done more than
        // once at the same variant" for every `repeated` but 1, so a loose
        // substring here would fail on a fixture change with nothing wrong
        // in the code.
        compose.onNodeWithText("and ", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a row with no family still renders, and an odd streak still counts`() {
        // Three shapes the real path produces and this band's fixture
        // never did: a row whose family the runner did not say (no colour
        // bar, and the name column starts further left), a `needed` other
        // than 3, and one past MAX_PIPS -- which would draw a pip per set
        // across the whole row if it were not clamped.
        val odd = listOf(
            Growth.Bump("no family", null, 20.0, 1, 2),
            Growth.Bump("long haul", "legs", 25.0, 3, 40),
        )
        compose.setContent { SinceScreen(since.copy(nearBump = odd)) }
        compose.onNodeWithText("no family").assertIsDisplayed()
        compose.onNodeWithContentDescription("no family, 1 of 2 clean sets, next at 20 pounds")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("long haul, 3 of 40 clean sets, next at 25 pounds")
            .assertIsDisplayed()
        // And it is drawn as the numbers, not as pips. Clamping `needed`
        // to ten without rescaling `done` made 3 of 40 read as 3 of 10 --
        // seven sets away instead of thirty-seven -- and 12 of 40 read as
        // a bump already earned (found by the review, 2026-10-16).
        compose.onNodeWithText("3 of 40").assertIsDisplayed()
        // And a 40-set streak does not draw 40 pips. They are unlabelled
        // Boxes, so nothing in the semantics tree counts them -- what can
        // be checked is the consequence. The row keeps its width either
        // way; what the pips eat is the NAME, which has the flexible
        // column. Unclamped it collapses to about one character wide,
        // which is the movement the owner is trying to read.
        val row = compose.onNodeWithContentDescription(
            "long haul, 3 of 40 clean sets, next at 25 pounds"
        ).getUnclippedBoundsInRoot()
        val name = compose.onNodeWithText("long haul", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "the pips squeezed the name: $name in $row",
            (name.right - name.left) >= (row.right - row.left) / 2,
        )
    }

    @Test
    fun `the label does not say the clean sets were at that weight`() {
        // The two numbers are about different things. The pips count
        // clean sets; the weight is what the runner says to use today.
        // On its converted-streak rule they differ -- two sets held at 30
        // for 8 reps earn "2 of 3" beside an estimate of 25 for 12, and
        // nothing was ever held at 25 (confirmed against the runner,
        // 2026-10-16). The sighted row never claimed otherwise; this
        // label did, because it joined them into one sentence.
        compose.setContent {
            SinceScreen(since.copy(nearBump = listOf(Growth.Bump("squat", "legs", 25.0, 2, 3))))
        }
        compose.onNodeWithContentDescription("squat, 2 of 3 clean sets, next at 25 pounds")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("squat, 2 of 3 clean sets, 25 pounds")
            .assertDoesNotExist()
    }

    @Test
    fun `the cap keeps the head of the list it is given`() {
        // Deliberately NOT sorting the fixture: that is `Growth.of`'s job
        // and `GrowthTest` holds it (the first version of this test sorted
        // its own input and then asserted the screen showed element zero,
        // which could not fail). What the screen owes is to take the FRONT
        // of the list, so a row the runner put first is never cut.
        val mixed = listOf(Growth.Bump("nearly there", "legs", 40.0, 2, 3)) + allAtOne(8)
        compose.setContent { SinceScreen(since.copy(nearBump = mixed)) }
        compose.onNodeWithText("nearly there").assertIsDisplayed()
        compose.onNodeWithText("movement 4").assertIsDisplayed()
        compose.onNodeWithText("movement 5").assertDoesNotExist()
        compose.onNodeWithText("and 4 more").assertIsDisplayed()
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
