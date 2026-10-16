package com.app.carry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules here are mostly about NOT notifying, and those are the ones
 * worth a test: a notification that should not have fired is the failure
 * the owner feels, and the ways to produce one are more numerous than the
 * single way to get it right.
 */
class NewSessionTest {

    private val today = "2026-10-17"

    @Test
    fun `a new file for today is worth telling them about`() {
        val found = NewSession.pick(
            seen = setOf("session-2026-10-11-3G.json"),
            now = listOf("session-2026-10-11-3G.json", "session-2026-10-17-2G.json"),
            today = today,
        )
        assertEquals(listOf("session-2026-10-17-2G.json"), found.map { it.name })
        assertEquals("2G", found[0].classTag)
        assertEquals("2026-10-17", found[0].date)
    }

    @Test
    fun `a file already seen is not announced again`() {
        val now = listOf("session-2026-10-17-2G.json")
        assertTrue(NewSession.pick(seen = now.toSet(), now = now, today = today).isEmpty())
    }

    @Test
    fun `a new file for another day is not today's news`() {
        // The runner backfills old days. That is not a sheet to walk in with.
        val found = NewSession.pick(
            seen = emptySet(),
            now = listOf("session-2026-09-20-3G.json"),
            today = today,
        )
        assertTrue(found.toString(), found.isEmpty())
    }

    @Test
    fun `nothing recorded yet announces nothing`() {
        // A first run, or a reinstall that lost the set. Six notifications
        // for sessions logged weeks ago is the failure the owner would
        // actually feel, so the design prefers re-notifying nothing.
        val found = NewSession.pick(
            seen = null,
            now = listOf("session-2026-10-17-2G.json", "session-2026-10-17-3G.json"),
            today = today,
        )
        assertTrue(found.toString(), found.isEmpty())
    }

    @Test
    fun `a borrowed day brings two files at once and both are named`() {
        // Real shape: 2026-10-11 in the owner's folder has a 2G and a 3G
        // file for the one date.
        val found = NewSession.pick(
            seen = emptySet(),
            now = listOf("session-2026-10-17-3G.json", "session-2026-10-17-2G.json"),
            today = today,
        )
        assertEquals(listOf("2G", "3G"), found.map { it.classTag })
        assertEquals("2G and 3G sheets are ready", NewSession.text(found))
    }

    @Test
    fun `anything that is not a session file name is ignored`() {
        val junk = listOf(
            "OVERNIGHT.md",
            "recovered-2026-09-20.jsonl",
            "session-2026-10-17.json",              // no class
            "session-17-10-2026-2G.json",           // not ISO
            "prefix-session-2026-10-17-2G.json",    // not anchored at the start
            "session-2026-10-17-2G.json.bak",       // not anchored at the end
            "session-2026-10-17-${"x".repeat(40)}.json",  // tag unbounded
        )
        val found = NewSession.pick(seen = emptySet(), now = junk, today = today)
        assertTrue(found.toString(), found.isEmpty())
        junk.forEach { assertNull(it, NewSession.parse(it)) }
    }

    @Test
    fun `the text carries the class and the date and nothing else`() {
        // This is the function that decides what reaches a lock screen.
        // A weight or a rep count appearing here is the privacy failure.
        val found = NewSession.pick(
            seen = emptySet(),
            now = listOf("session-2026-10-17-3G.json"),
            today = today,
        )
        assertEquals("3G sheet is ready", NewSession.text(found))
        assertNull(NewSession.text(emptyList()))
    }
}
