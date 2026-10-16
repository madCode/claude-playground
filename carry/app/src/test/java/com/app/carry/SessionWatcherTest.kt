package com.app.carry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionWatcherTest {

    private val today = "2026-10-17"

    private class FakeFolder(
        override val chosen: Boolean,
        override val grantLost: Boolean = false,
        private val names: List<String> = emptyList(),
    ) : Folder {
        override val label: String? = "Sync/gym"
        override fun list(): List<Pair<String, String>> = names.map { "id-$it" to it }
        override fun read(documentId: String): String? = null
        override fun write(documentId: String, text: String): Boolean = false
    }

    private class FakeSeen(private var stored: Set<String>? = null) : Seen {
        var writes = 0
            private set
        override fun names(): Set<String>? = stored
        override fun remember(names: Set<String>) {
            stored = names
            writes++
        }
    }

    @Test
    fun `a new file today is announced once, and not again`() {
        val folder = FakeFolder(chosen = true, names = listOf("session-2026-10-17-3G.json"))
        val seen = FakeSeen(stored = emptySet())
        val posted = mutableListOf<List<NewSession.Found>>()

        val first = SessionWatcher.watchOnce(folder, seen, today) { posted += it }
        assertEquals(listOf("3G"), first.map { it.classTag })

        val second = SessionWatcher.watchOnce(folder, seen, today) { posted += it }
        assertTrue(second.toString(), second.isEmpty())
        assertEquals("announced exactly once", 1, posted.size)
    }

    @Test
    fun `a folder nobody picked is left entirely alone`() {
        val seen = FakeSeen(stored = setOf("session-2026-10-11-3G.json"))
        var posted = false
        val found = SessionWatcher.watchOnce(
            FakeFolder(chosen = false), seen, today,
        ) { posted = true }

        assertTrue(found.isEmpty())
        assertTrue("nothing announced", !posted)
        assertEquals("the record must not be touched", 0, seen.writes)
        assertEquals(setOf("session-2026-10-11-3G.json"), seen.names())
    }

    @Test
    fun `a lost grant does not wipe the record and re-announce everything later`() {
        // This is the dangerous one. `list()` returns empty for a revoked
        // grant AND for an empty folder, so recording that empty list would
        // erase what was seen -- and every file the owner already has would
        // look new once the grant came back.
        val seen = FakeSeen(stored = setOf("session-2026-10-11-3G.json"))
        SessionWatcher.watchOnce(FakeFolder(chosen = false, grantLost = true), seen, today) {}
        assertEquals(0, seen.writes)

        // Grant restored, the same files are there: still nothing to say.
        val back = FakeFolder(chosen = true, names = listOf("session-2026-10-11-3G.json"))
        val found = SessionWatcher.watchOnce(back, seen, today) {}
        assertTrue(found.toString(), found.isEmpty())
    }

    @Test
    fun `the first run ever records what it saw and announces nothing`() {
        val folder = FakeFolder(
            chosen = true,
            names = listOf("session-2026-10-17-2G.json", "session-2026-10-17-3G.json"),
        )
        val seen = FakeSeen(stored = null)
        var posted = false

        val found = SessionWatcher.watchOnce(folder, seen, today) { posted = true }
        assertTrue(found.toString(), found.isEmpty())
        assertTrue("a fresh install must not announce a backlog", !posted)
        assertEquals(2, seen.names()?.size)

        // And the next new file announces normally.
        val later = FakeFolder(
            chosen = true,
            names = folder.list().map { it.second } + "session-2026-10-17-1G.json",
        )
        val next = SessionWatcher.watchOnce(later, seen, today) {}
        assertEquals(listOf("1G"), next.map { it.classTag })
    }

    @Test
    fun `an empty folder with a live grant is recorded, not treated as a loss`() {
        val seen = FakeSeen(stored = setOf("session-2026-10-11-3G.json"))
        SessionWatcher.watchOnce(FakeFolder(chosen = true), seen, today) {}
        assertEquals(1, seen.writes)
        assertEquals(emptySet<String>(), seen.names())
    }

    @Test
    fun `the record is kept even when the announcement goes nowhere`() {
        // Notifications off: the sheet is still on the Before tab, and
        // holding the name back would mean turning the permission on later
        // produces a burst about sessions already logged.
        val folder = FakeFolder(chosen = true, names = listOf("session-2026-10-17-3G.json"))
        val seen = FakeSeen(stored = emptySet())
        SessionWatcher.watchOnce(folder, seen, today) { /* dropped on the floor */ }
        assertEquals(setOf("session-2026-10-17-3G.json"), seen.names())
        assertNull(NewSession.text(SessionWatcher.watchOnce(folder, seen, today) {}))
    }
}
