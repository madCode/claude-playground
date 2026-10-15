package com.app.carry

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The session file is written on another machine and replicated by a sync
 * service, so it is untrusted input. These are the two shapes that turned
 * the app into a crash loop on every launch — unfixable from the phone,
 * because `onResume` re-reads the same file — and they were both valid
 * JSON of the right schema.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HostileFileTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 17)

    @Test
    fun `an absurd streak does not emit two billion nodes`() {
        // Sixty bytes of valid JSON: {"streak_needed": 2000000000}. It used
        // to be rendered literally, during the first composition of the
        // default tab.
        val session = SampleSession.today.copy(
            sets = SampleSession.today.sets.map { it.copy(streakNeeded = Int.MAX_VALUE, streakDone = Int.MAX_VALUE) }
        )
        compose.setContent { TodayScreen(session, today) }
        compose.onNodeWithText("45 lb").assertIsDisplayed()
        // And it does not draw ten lit pips either. Clamping `needed` to
        // ten while `done` ran past it was the picture of a bump already
        // earned, for a streak that has not been (found 2026-10-16). Past
        // ten the row says the numbers instead.
        // Every row in the fixture carries it, so there are several.
        compose.onAllNodesWithText("${Int.MAX_VALUE} of ${Int.MAX_VALUE}", substring = true)[0]
            .assertIsDisplayed()
    }

    @Test
    fun `a streak longer than the pips says the numbers`() {
        // Twelve of forty used to draw ten lit pips and no unlit ones.
        // Under a heading that says part way, that is the one thing this
        // app must not do: a number on screen that is not the number.
        val session = SampleSession.today.copy(
            sets = SampleSession.today.sets.map { it.copy(streakNeeded = 40, streakDone = 12) }
        )
        compose.setContent { TodayScreen(session, today) }
        compose.onAllNodesWithText("12 of 40", substring = true)[0].assertIsDisplayed()
    }

    @Test
    fun `a negative streak does not throw`() {
        val session = SampleSession.today.copy(
            sets = SampleSession.today.sets.map { it.copy(streakNeeded = -5, streakDone = -9) }
        )
        compose.setContent { TodayScreen(session, today) }
        compose.onNodeWithText("45 lb").assertIsDisplayed()
    }

    @Test
    fun `two rows sharing an id render instead of crashing the screen`() {
        // SessionPatch refuses such a file by name at save time -- but this
        // screen renders FIRST, so a duplicate key threw before the refusal
        // could ever be shown.
        val duplicated = SampleSession.today.copy(
            sets = SampleSession.today.sets.map { it.copy(ref = 0) }
        )
        compose.setContent { TodayScreen(duplicated, today) }
        compose.onNodeWithText("Carry these in").assertIsDisplayed()
    }

    @Test
    fun `a name cannot reverse the line it sits on`() {
        // A right-to-left override in an exercise name arrives by accident
        // -- these come from Reddit comments by way of another machine --
        // and renders the rest of the text backwards. In an app whose
        // stated worst outcome is a wrong number in someone's head, text
        // that reads in an order other than the one stored is the same
        // kind of failure.
        val sneaky = ExerciseSet(ref = 0, line = "x", canonical = "dead\u202elift\u200b")
        assertEquals("deadlift", sneaky.displayName)
    }

    @Test
    fun `a session whose date is nonsense still renders`() {
        val odd = SampleSession.today.copy(date = "‮".repeat(20))
        compose.setContent { TodayScreen(odd, today) }
        compose.onNodeWithText("Carry these in").assertIsDisplayed()
    }
}
