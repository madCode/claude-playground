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
    fun `a null flag costs that flag, not the whole day`() {
        // The file is written on another machine. A `null` where this build
        // expects a plain value used to throw, which `parse` turned into
        // "Can't read that session" -- so one odd key cost a day whose
        // every number was intact, with nothing the owner could do from
        // the phone.
        fun day(row: String) = SessionFiles.parse(
            """{"schema":"gym-session/1","sheet_id":"s","date":"2026-10-17","sets":[$row]}"""
        )

        val nulled = day("""{"ref":0,"line":"10 x squat","done":null,"easy":null,
            "hard":null,"easier":null,"hurt":null,"skipped":null}""")
        assertEquals("the day still reads", 1, nulled?.sets?.size)
        val set = nulled!!.sets.first()
        // And `false` is not a guess: an unset flag means the owner did not
        // say that about the set, which is what each of these records.
        assertEquals("10 x squat", set.line)
        assertTrue("no flag is claimed",
            !set.done && !set.easy && !set.hard && !set.easier && !set.hurt && !set.skipped)

        // A null `sets` now reads the same as an absent one, which it
        // always should have: both mean "no rows", and only one of them
        // used to lose the file.
        assertEquals(
            0,
            SessionFiles.parse(
                """{"schema":"gym-session/1","sheet_id":"s","date":"d","sets":null}"""
            )?.sets?.size,
        )
    }

    @Test
    fun `a row this build cannot read costs that row, not the day`() {
        // The file is written on another machine. Decoding it whole meant
        // one malformed field anywhere took the whole day down and the
        // owner saw "Can't read that session" in a studio, with no way to
        // fix it from the phone.
        val file = """{"schema":"gym-session/1","sheet_id":"s","date":"2026-10-17","class":"2G","sets":[
            {"ref":0,"line":"10 x squat","canonical":"squat","suggested_weight":45},
            {"ref":1,"line":"10 x row","canonical":"row","weight":"45 lb"},
            {"ref":2,"line":"10 x press","canonical":"press","done":"yes"},
            {"ref":null,"line":"10 x curl","canonical":"curl"},
            {"line":"10 x fly","canonical":"fly"}
        ]}"""
        val parsed = SessionFiles.parse(file)
        assertEquals("the readable row survives", 1, parsed?.sets?.size)
        assertEquals("squat", parsed!!.sets.first().canonical)
        // The head too: a bad row must not take the date and the class
        // with it, since those are what the sheet is identified by.
        assertEquals("2026-10-17", parsed.date)
        assertEquals("2G", parsed.classTag)
        // Four kinds of damage, one per row: a string weight, a string
        // flag, a null ref and no ref at all.
        assertEquals(4, SessionFiles.unreadableRows(file, parsed))
    }

    @Test
    fun `the count reaches the screens through a real folder read`() {
        // The wiring, which no amount of testing `parse` covers: stubbing
        // `carryState` to stop counting left every unit test green while
        // the feature was dead in the app. Two repos and 151 green tests
        // once failed to talk to each other for exactly this reason.
        val damaged = """{"schema":"gym-session/1","sheet_id":"s","date":"2026-10-17","sets":[
            {"ref":0,"line":"10 x squat"},{"ref":1,"line":"10 x row","weight":"45 lb"}
        ]}"""
        val state = carryState(
            FakeFolder(
                files = listOf("doc1" to "session-2026-10-17-2G.json"),
                contents = mapOf("doc1" to damaged),
            )
        )
        val ready = state as CarryState.Ready
        assertEquals("the readable row is on screen", 1, ready.session.sets.size)
        assertEquals("and the other one is accounted for", 1, ready.unreadable)
    }

    @Test
    fun `a clean file reports nothing unreadable`() {
        // The count is a subtraction, so an off-by-one here would put a
        // warning on every sheet the owner ever opens.
        val file = """{"schema":"gym-session/1","sheet_id":"s","date":"2026-10-17","sets":[
            {"ref":0,"line":"10 x squat"},{"ref":1,"line":"10 x row"}
        ]}"""
        val parsed = SessionFiles.parse(file)!!
        assertEquals(2, parsed.sets.size)
        assertEquals(0, SessionFiles.unreadableRows(file, parsed))
        assertNull(SessionFiles.unreadableNote(0))
    }

    @Test
    fun `coercion must not vouch for another tool's file`() {
        // The hazard the fix brings with it. `schema` decides whether this
        // file is ours at all; if it were non-nullable with a default,
        // coercion would turn some other tool's `"schema": null` INTO
        // "gym-session/1" and the gate would pass its own default back to
        // itself.
        assertNull(SessionFiles.parse("""{"schema":null,"sheet_id":"a","date":"b","sets":[]}"""))
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
