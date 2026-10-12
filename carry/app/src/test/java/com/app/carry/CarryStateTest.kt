package com.app.carry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeFolder(
    override val label: String? = "Sync/gym",
    override val chosen: Boolean = true,
    override val grantLost: Boolean = false,
    val files: List<Pair<String, String>> = emptyList(),
    val contents: Map<String, String> = emptyMap(),
) : Folder {
    override fun list() = files
    override fun write(documentId: String, text: String) = false
    override fun read(documentId: String) = contents[documentId]
}

private val VALID = """
    {"schema":"gym-session/1","sheet_id":"2026-10-17:2G","date":"2026-10-17",
     "class":"2G","sets":[{"ref":0,"line":"10 x deadlift","canonical":"deadlift"}]}
""".trimIndent()

class GrowthFromFolderTest {

    @Test
    fun `the folder's whole history is read, through a real listing`() {
        // The listing gives (document id, display name) and reading those
        // the other way round is silent: the first version filtered the
        // IDS against the filename pattern, matched nothing, and showed
        // "0 sessions" over a folder holding nine. Every unit test passed,
        // because they all handed file TEXT straight to Growth and never
        // went through a folder — so this one does.
        val lifted = """
            {"schema":"gym-session/1","sheet_id":"2026-09-27:3G","date":"2026-09-27","class":"3G",
             "sets":[{"ref":0,"line":"10 x squat","canonical":"squat","family":"legs",
                      "key":"squat","weight":40,"reps":8}]}
        """.trimIndent()
        val folder = FakeFolder(
            files = listOf(
                "doc-a" to "session-2026-09-27-3G.json",
                // Things a shared folder really holds, and must be ignored.
                "doc-b" to "notes.txt",
                "doc-c" to ".stfolder",
            ),
            contents = mapOf("doc-a" to lifted, "doc-b" to "not json at all"),
        )
        val since = growthOf(folder)
        assertEquals(1, since.sessions)
        assertEquals(1, since.sets)
        assertEquals("squat", since.peaks.single().best.name)
    }

    @Test
    fun `a folder with nothing in it is not an error`() {
        assertEquals(0, growthOf(FakeFolder()).sessions)
    }

    @Test
    fun `a file the provider will not hand over is skipped, not fatal`() {
        val folder = FakeFolder(
            files = listOf("gone" to "session-2026-09-27-3G.json"),
            contents = emptyMap(),
        )
        assertEquals(0, growthOf(folder).sessions)
    }
}

class CarryStateTest {

    @Test
    fun `the newest session is chosen by the date in its name, not by arrival`() {
        // A sync copies a file whenever it feels like it, so mtime says when
        // it landed rather than which workout it is.
        val names = listOf(
            "session-2026-10-10-2G.json",
            "session-2026-10-17-3G.json",
            "session-2026-10-03-2G.json",
            "notes.txt",
            "session-bad.json",
        )
        assertEquals("session-2026-10-17-3G.json", SessionFiles.newest(names))
    }

    @Test
    fun `a folder with no session files is not an error`() {
        // Shared folders have other things in them.
        assertNull(SessionFiles.newest(listOf("photo.jpg", "README.md")))
    }

    @Test
    fun `unreadable content is null rather than a crash`() {
        assertNull(SessionFiles.parse("not json"))
        assertNull(SessionFiles.parse(""))
        // A half-synced file is normal, not exceptional.
        assertNull(SessionFiles.parse("""{"schema":"gym-session/1","sheet_id":"x"""))
        // And a file from some other tool that happens to be JSON.
        assertNull(SessionFiles.parse("""{"schema":"something-else/1","sheet_id":"a","date":"b","sets":[]}"""))
    }

    @Test
    fun `no folder means the picker`() {
        assertEquals(CarryState.NeedsFolder(), carryState(FakeFolder(chosen = false)))
    }

    @Test
    fun `an empty folder is not a lost grant`() {
        // The review caught this: the likeliest reason a folder is empty is
        // that the coach has not written today's session yet. Telling the
        // owner they lost a folder they just picked sends them round the
        // picker for nothing.
        assertEquals(CarryState.NoSession, carryState(FakeFolder(files = emptyList())))
    }

    @Test
    fun `a revoked grant is reported from the system's own record`() {
        val state = carryState(FakeFolder(chosen = false, grantLost = true))
        // The folder is named: a recursive grant nobody can see is one
        // nobody can audit.
        assertEquals(CarryState.NeedsFolder(lostGrant = true, holding = "Sync/gym"), state)
    }

    @Test
    fun `a file written by a newer coach says update, not wait`() {
        val folder = FakeFolder(
            files = listOf("d" to "session-2026-10-17-2G.json"),
            contents = mapOf("d" to """{"schema":"gym-session/2","sheet_id":"x","date":"y","sets":[]}"""),
        )
        val state = carryState(folder)
        assertTrue(state is CarryState.Unreadable && state.wrongSchema)
    }

    @Test
    fun `a sheet on screen survives a refresh that comes back empty`() {
        // Syncthing replaces a file rather than editing it. Resuming inside
        // that window used to discard a sheet that was fine a second ago.
        val good = readyFor()
        assertEquals(good, keepLastGood(good, CarryState.NoSession))
        assertEquals(good, keepLastGood(good, CarryState.Unreadable("x.json", false)))
        // ...but a folder that is genuinely gone must still be reported.
        assertEquals(
            CarryState.NeedsFolder(lostGrant = true),
            keepLastGood(good, CarryState.NeedsFolder(lostGrant = true)),
        )
    }

    @Test
    fun `a folder with no session yet says so`() {
        val state = carryState(FakeFolder(files = listOf("1" to "photo.jpg")))
        assertEquals(CarryState.NoSession, state)
    }

    @Test
    fun `a valid session is loaded`() {
        val folder = FakeFolder(
            files = listOf("doc1" to "session-2026-10-17-2G.json"),
            contents = mapOf("doc1" to VALID),
        )
        val state = carryState(folder)
        assertTrue(state is CarryState.Ready)
        assertEquals("2026-10-17:2G", (state as CarryState.Ready).session.sheetId)
    }

    @Test
    fun `a half-synced file says so rather than claiming nothing arrived`() {
        val folder = FakeFolder(
            files = listOf("doc1" to "session-2026-10-17-2G.json"),
            contents = mapOf("doc1" to "{ truncated"),
        )
        val state = carryState(folder)
        assertTrue(state is CarryState.Unreadable && !state.wrongSchema)
    }
}
