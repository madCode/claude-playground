package com.app.carry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SessionWatcherTest {

    private val today = LocalDateTime.parse("2026-10-17T09:00")

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
    fun `tomorrow's sheet, written tonight, is announced tomorrow and not lost`() {
        // The case the whole feature exists for: "a session file appears in
        // the synced folder hours before the class". The runner writes
        // tomorrow's sheet this evening. Storing it on sight would mean
        // that when its day arrives it is already seen, and the owner would
        // never be told about the one sheet they are going to walk in with.
        val tomorrow = listOf("session-2026-10-18-3G.json")
        val folder = FakeFolder(chosen = true, names = tomorrow)
        val seen = FakeSeen(stored = emptySet())

        val tonight = SessionWatcher.watchOnce(folder, seen, today) {}
        assertTrue("not today's news yet: $tonight", tonight.isEmpty())
        assertTrue(
            "a file dated ahead must not be recorded: ${seen.names()}",
            seen.names()?.contains("session-2026-10-18-3G.json") != true,
        )

        val onTheDay = SessionWatcher.watchOnce(folder, seen, today.plusDays(1)) {}
        assertEquals(listOf("3G"), onTheDay.map { it.classTag })
    }

    @Test
    fun `a sheet found in the small hours waits for the morning`() {
        // The runner writes tomorrow's sheet in the evening, so the first
        // run after local midnight is the one that finds it new and dated
        // today. Posting there is a sound at 00-something for a class many
        // hours away -- and a notice dismissed or buried by morning is
        // never posted again, on what is now the only path that tells the
        // owner their sheet arrived.
        val folder = FakeFolder(chosen = true, names = listOf("session-2026-10-17-3G.json"))
        val seen = FakeSeen(stored = emptySet())
        var posted = false

        val night = SessionWatcher.watchOnce(
            folder, seen, LocalDateTime.parse("2026-10-17T00:30"),
        ) { posted = true }
        assertTrue("nothing at half past midnight: $night", night.isEmpty())
        assertTrue("and nothing posted", !posted)
        assertTrue(
            "and NOT recorded, or the morning run would have nothing to say",
            seen.names()?.isEmpty() != false,
        )

        val morning = SessionWatcher.watchOnce(
            folder, seen, LocalDateTime.parse("2026-10-17T06:05"),
        ) { posted = true }
        assertEquals(listOf("3G"), morning.map { it.classTag })
        assertTrue("posted in the morning", posted)
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
    }

    @Test
    fun `a folder that lists as empty does not wipe the record`() {
        // `SessionFolder.list()` answers an empty list when the query
        // throws, the provider is restarting, or the volume is not mounted
        // yet -- indistinguishable from an empty folder. Replacing the
        // record on that answer loses the memory, and the next successful
        // run announces today's sheet a second time.
        val seen = FakeSeen(stored = setOf("session-2026-10-17-3G.json"))
        SessionWatcher.watchOnce(FakeFolder(chosen = true), seen, today) {}
        assertEquals(setOf("session-2026-10-17-3G.json"), seen.names())

        // Provider answers again: still nothing to say.
        val back = FakeFolder(chosen = true, names = listOf("session-2026-10-17-3G.json"))
        val found = SessionWatcher.watchOnce(back, seen, today) {}
        assertTrue("must not re-announce: $found", found.isEmpty())
    }

    @Test
    fun `a lost grant does not wipe the record either`() {
        val seen = FakeSeen(stored = setOf("session-2026-10-11-3G.json"))
        SessionWatcher.watchOnce(FakeFolder(chosen = false, grantLost = true), seen, today) {}
        assertEquals(0, seen.writes)
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

        val later = FakeFolder(
            chosen = true,
            names = folder.list().map { it.second } + "session-2026-10-17-1G.json",
        )
        assertEquals(listOf("1G"), SessionWatcher.watchOnce(later, seen, today) {}.map { it.classTag })
    }

    @Test
    fun `the record is kept even when the announcement goes nowhere`() {
        // Notifications off, or a post that throws: the sheet is still on
        // the Before tab, and holding the name back would mean the same
        // sheet is announced again every hour for the rest of the day.
        val folder = FakeFolder(chosen = true, names = listOf("session-2026-10-17-3G.json"))
        val seen = FakeSeen(stored = emptySet())

        // The throw is NOT swallowed here -- the worker's runCatching owns
        // that -- so catching it is part of what this asserts. What matters
        // is that the record survived a post that failed, because recording
        // happens first.
        runCatching {
            SessionWatcher.watchOnce(folder, seen, today) { error("post failed") }
        }
        assertEquals(setOf("session-2026-10-17-3G.json"), seen.names())
        assertTrue(
            "and so the same sheet is not announced again",
            SessionWatcher.watchOnce(folder, seen, today) {}.isEmpty(),
        )
    }

    @Test
    fun `the record keeps the recent names and drops the stale ones`() {
        // The first version of this test could not fail. It built 40 names
        // from `1 + it % 9`, which collapse to NINE distinct strings, then
        // asserted `kept.size < old.size` -- 10 < 40, true with the
        // pruning deleted. Distinct dates and an exact assertion now.
        val day = today.toLocalDate()
        val stale = (1..30).map { "session-${day.minusDays(60L + it)}-3G.json" }
        val fresh = (0..5).map { "session-${day.minusDays(it.toLong())}-3G.json" }
        val seen = FakeSeen(stored = (stale + fresh).toSet())

        SessionWatcher.watchOnce(FakeFolder(chosen = true), seen, today) {}

        assertEquals("every fresh name kept, every stale one dropped", fresh.toSet(), seen.names())
    }
}
