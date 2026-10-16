package com.app.carry

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.JsonNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// Throwaway: renders the After page for the UX audit. Not committed.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1500dp-xxhdpi")
class AfterAuditShots {
    @get:Rule val compose = createComposeRule()
    private val out = File(System.getProperty("screenshotDir") ?: "build/screenshots", "audit").apply { mkdirs() }

    private fun set(
        ref: Int, name: String, family: String?, asked: String? = "10", reps: Int? = 10,
        seconds: Int? = null, suggestedWeight: Double? = null,
    ) = ExerciseSet(
        ref = ref, line = "${asked ?: seconds} x $name", canonical = name, variant = JsonNull,
        prescribedReps = reps, prescribedText = asked, prescribedDurationSec = seconds,
        family = family, suggestedWeight = suggestedWeight,
        suggested = if (suggestedWeight == null) "no weight needed" else null,
    )

    private val weighted = Session(
        sheetId = "2026-10-17:2G", date = "2026-10-17", classTag = "2G",
        sets = listOf(
            set(0, "bench press", "push", suggestedWeight = 45.0),
            set(1, "single-arm row", "pull", suggestedWeight = 40.0),
            set(2, "goblet squat", "legs", asked = "12", reps = 12, suggestedWeight = 30.0),
            set(3, "deadlift", "legs", asked = "10 total", reps = 5, suggestedWeight = 50.0),
            set(4, "lateral raise", "push", asked = "8 each", reps = 8, suggestedWeight = 12.0),
        ),
    )

    private val bodyweight = Session(
        sheetId = "2026-10-18:2G", date = "2026-10-18", classTag = "2G",
        sets = listOf(
            set(0, "air squat", "legs", asked = "20", reps = 20),
            set(1, "push-up", "push", asked = "10", reps = 10),
            set(2, "jump lunge", "legs", asked = "10 total", reps = 5),
            set(3, "mountain climber", "core", asked = "20 total", reps = 10),
            set(4, "low plank", "core", asked = null, reps = null, seconds = 45),
        ),
    )

    private fun show(
        session: Session, entries: Map<Int, SessionPatch.Entry>,
        added: List<AddedSet> = emptyList(), state: SaveState = SaveState.Idle,
    ) {
        val e = mutableStateOf(entries)
        compose.setContent {
            LogScreen(
                session = session, entries = e.value, added = added,
                onEdit = { _, _ -> }, onAdd = {}, onEditAdded = { _, _ -> },
                onSave = {}, saving = state,
            )
        }
    }

    @Test fun weightedDay() {
        show(weighted, mapOf(
            0 to SessionPatch.Entry(weight = 45.0, reps = 10, easy = true),
            1 to SessionPatch.Entry(weight = 40.0, reps = 10),
            2 to SessionPatch.Entry(weight = 30.0, reps = 9, hard = true),
            3 to SessionPatch.Entry(weight = 50.0, reps = 5),
        ), state = SaveState.Saved)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Post what 2G did"))
        compose.onNodeWithText("Post what 2G did").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Send it"))
        shoot("weighted-saved")
    }

    @Test fun weightedBlank() {
        show(weighted, emptyMap())
        shoot("weighted-blank")
    }

    @Test fun bodyweightDay() {
        show(bodyweight, mapOf(
            0 to SessionPatch.Entry(easy = true),
            1 to SessionPatch.Entry(easier = true),
            2 to SessionPatch.Entry(hard = true),
            4 to SessionPatch.Entry(done = true),
        ), state = SaveState.Saved)
        shoot("bodyweight-saved")
    }

    @Test fun bodyweightNoTimed() {
        show(bodyweight.copy(sets = bodyweight.sets.take(4)), mapOf(
            0 to SessionPatch.Entry(easy = true),
            1 to SessionPatch.Entry(easier = true),
            2 to SessionPatch.Entry(hard = true),
            3 to SessionPatch.Entry(easy = true),
        ), state = SaveState.Saved)
        shoot("bodyweight-only-saved")
    }

    @Test fun addedRow() {
        show(weighted, mapOf(0 to SessionPatch.Entry(weight = 45.0, reps = 10)), added = listOf(
            AddedSet(id = "a1", name = "farmer carry", weight = 35.0, reps = 20),
            AddedSet(id = "a2", name = "burpee", easy = true),
        ))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Save"))
        shoot("added-row")
    }

    private fun shoot(name: String) {
        compose.waitForIdle()
        val shot = compose.onRoot().captureToImage().asAndroidBitmap()
        check(!shot.isFlat()) { "$name captured a blank image" }
        File(out, "$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
