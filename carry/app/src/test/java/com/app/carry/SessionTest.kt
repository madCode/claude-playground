package com.app.carry

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {

    /** The runner's file, with a variant shape Carry has no business
     *  understanding and fields a newer runner might add. */
    private val fromRunner = """
        {
          "schema": "gym-session/1",
          "sheet_id": "2026-10-17:2G",
          "date": "2026-10-17",
          "class": "3G",
          "borrowed_from": "2G",
          "sets": [
            {
              "ref": 0,
              "line": "6-12 x bench press",
              "canonical": "bench press",
              "components": null,
              "variant": {"stance": "both", "implement": "barbell", "reps": "6-12"},
              "prescribed_reps": 8,
              "suggested": "try 45 lb (up from 40, held 3x)",
              "suggested_weight": 45,
              "previous_weight": 40,
              "family": "push",
              "weight": null,
              "reps": null,
              "something_a_newer_runner_added": 7
            }
          ],
          "added": []
        }
    """.trimIndent()

    @Test
    fun `a variant survives a round trip untouched`() {
        // The one rule: Carry never invents an exercise's identity. It cannot
        // reconstruct a variant it does not model, so it must hand back
        // exactly what it was given -- enforced by the type, not by care.
        val session = Session.json.decodeFromString<Session>(fromRunner)
        val filled = session.copy(
            sets = session.sets.map { it.copy(weight = 45.0, reps = 8, easy = true) }
        )
        val written = Session.json.encodeToString(filled)

        fun variantOf(doc: String) =
            Json.parseToJsonElement(doc).jsonObject["sets"]!!.jsonArray[0].jsonObject["variant"]
        assertEquals(variantOf(fromRunner), variantOf(written))
    }

    @Test
    fun `an unknown field from a newer runner does not break the read`() {
        val session = Session.json.decodeFromString<Session>(fromRunner)
        assertEquals("bench press", session.sets.single().canonical)
    }

    @Test
    fun `a blank row is not answered, and skipping is a deliberate answer`() {
        // Never silently "skipped": a wrong number in a training history is
        // worse than a missing one, because nothing downstream can tell.
        val blank = SampleSession.today.sets.first()
        assertFalse(blank.answered)
        assertTrue(blank.copy(skipped = true).answered)
        assertTrue(blank.copy(reps = 8).answered)
    }

    @Test
    fun `a change is only a change when there is something to compare`() {
        val set = SampleSession.today.sets.first { it.canonical == "bench press" }
        assertEquals(5.0, set.change!!, 0.001)
        assertNull(set.copy(previousWeight = null).change)
        assertNull(set.copy(previousWeight = 45.0).change)  // same weight is not a change
    }

    @Test
    fun `weights read as people write them`() {
        assertEquals("45", 45.0.asWeight())
        assertEquals("12.5", 12.5.asWeight())
        assertEquals("—", null.asWeight())
    }
}
