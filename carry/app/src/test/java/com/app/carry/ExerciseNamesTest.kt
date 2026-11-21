package com.app.carry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The matching behind the After tab's name suggestions, on invented names and on the bundled list. */
class ExerciseNamesTest {
    private val known = listOf(
        KnownName("goblet squat", listOf("squat goblet")),
        KnownName("squat"),
        KnownName("sumo squat"),
        KnownName("halo", listOf("alt halo", "halos")),
        KnownName("push-up", listOf("push ups")),
    )

    @Test
    fun `a name that starts with what is typed comes first, then any with each word`() {
        // "squat goblet" is an alias that starts with it, so goblet squat beats sumo squat.
        assertEquals(listOf("squat", "goblet squat", "sumo squat"), ExerciseNames.suggest("squat", known))
        assertEquals(listOf("goblet squat"), ExerciseNames.suggest("gob sq", known))
    }

    @Test
    fun `an alias finds its movement, and offers the coach's name for it`() {
        assertEquals(listOf("halo"), ExerciseNames.suggest("alt ha", known))
        assertEquals(listOf("push-up"), ExerciseNames.suggest("Push Ups", known))
    }

    @Test
    fun `nothing once the typed name is one of them, or too short to mean much`() {
        assertEquals(emptyList<String>(), ExerciseNames.suggest("Goblet  Squat", known))
        assertEquals(emptyList<String>(), ExerciseNames.suggest("s", known))
        assertEquals(emptyList<String>(), ExerciseNames.suggest("   ", known))
    }

    @Test
    fun `at most four`() {
        val many = (1..10).map { KnownName("row $it") }
        assertEquals(4, ExerciseNames.suggest("row", many).size)
    }

    @Test
    fun `the bundled list is the coach's, and reads`() {
        // What the build copies into the app; a fixtures format change that
        // breaks the read would otherwise only show as no suggestions.
        val names = ExerciseNames.parse(File("../brain-fixtures/exercises.json").readText())
        assertTrue(names.size > 100)
        assertTrue(names.any { it.name == "goblet squat" })
    }
}
