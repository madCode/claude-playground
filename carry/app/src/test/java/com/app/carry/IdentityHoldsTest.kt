package com.app.carry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ref` is only an index. This is the check that stops a runner re-run
 * from turning an edit meant for one exercise into one against another,
 * so it needs tests in both directions: it must not refuse a benign
 * rewrite, and it must not pass a renumbering.
 */
class IdentityHoldsTest {

    private fun sheet(vararg sets: String) = """
        {"schema":"gym-session/1","sheet_id":"x","date":"2026-10-17","sets":[${sets.joinToString(",")}]}
    """.trimIndent()

    private val deadlift = """{"ref":0,"line":"10 x deadlift","canonical":"deadlift",
        "variant":{"implement":"2 DB","stance":"both"},"prescribed_reps":10}"""
    private val bench = """{"ref":1,"line":"8 x bench press","canonical":"bench press",
        "variant":{"implement":"barbell"},"prescribed_reps":8}"""

    @Test
    fun `an unchanged file holds`() {
        assertTrue(SessionPatch.identityHolds(sheet(deadlift, bench), sheet(deadlift, bench), setOf(0, 1)))
    }

    @Test
    fun `a rewrite that only reorders the variant's keys still holds`() {
        // variant is an opaque object the runner may emit in any order.
        // Comparing its printed form refused saves for a file that had not
        // meaningfully changed -- and since a refusal is sticky, that would
        // have been unrecoverable.
        val reordered = deadlift.replace(
            """{"implement":"2 DB","stance":"both"}""",
            """{"stance":"both","implement":"2 DB"}""",
        )
        assertTrue(SessionPatch.identityHolds(sheet(deadlift, bench), sheet(reordered, bench), setOf(0)))
    }

    @Test
    fun `a rewrite that changes only fields nobody edits still holds`() {
        val resuggested = deadlift.replace("\"prescribed_reps\":10", "\"prescribed_reps\":10,\"suggested_weight\":30")
        assertTrue(SessionPatch.identityHolds(sheet(deadlift, bench), sheet(resuggested, bench), setOf(0)))
    }

    @Test
    fun `swapped rows do not hold`() {
        val swapped = sheet(deadlift.replace("\"ref\":0", "\"ref\":1"), bench.replace("\"ref\":1", "\"ref\":0"))
        assertFalse(SessionPatch.identityHolds(sheet(deadlift, bench), swapped, setOf(0)))
    }

    @Test
    fun `two rows with the same line are told apart by their prescribed reps`() {
        // The same movement in two blocks of one workout is normal here. On
        // `line` alone these compared equal, so a renumbering that swapped
        // exactly those two slipped through.
        val blockOne = """{"ref":0,"line":"10 x squat","prescribed_reps":10}"""
        val blockTwo = """{"ref":1,"line":"10 x squat","prescribed_reps":6}"""
        val swapped = sheet(blockOne.replace("\"ref\":0", "\"ref\":1"), blockTwo.replace("\"ref\":1", "\"ref\":0"))
        assertFalse(SessionPatch.identityHolds(sheet(blockOne, blockTwo), swapped, setOf(0)))
    }

    @Test
    fun `a row that has gone does not hold`() {
        assertFalse(SessionPatch.identityHolds(sheet(deadlift, bench), sheet(bench), setOf(0)))
    }

    @Test
    fun `unreadable text does not hold`() {
        assertFalse(SessionPatch.identityHolds(sheet(deadlift), "{ truncated", setOf(0)))
        assertFalse(SessionPatch.identityHolds("{ truncated", sheet(deadlift), setOf(0)))
    }
}
