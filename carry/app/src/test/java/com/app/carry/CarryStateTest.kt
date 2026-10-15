package com.app.carry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun `whatever the save refuses, the read refuses, and the other way round`() {
        // Asserted from BOTH sides, because keeping two lists in step by
        // hand is what went wrong twice: `added` of the wrong shape and a
        // duplicate `ref` each read as a perfectly good sheet and then
        // refused every save -- after a whole borrowed day had been typed
        // in, with a message about a list shown as empty. They share one
        // function now; this is what pins that they still do.
        fun file(body: String) =
            """{"schema":"gym-session/1","sheet_id":"s","date":"2026-10-17",$body}"""
        val damaged = listOf(
            """"sets":{"0":{"ref":0,"line":"x"}}""",
            """"sets":"none"""",
            """"sets":7""",
            """"sets":[{"ref":0,"line":"10 x squat"},null]""",
            """"sets":[{"ref":0,"line":"10 x squat"},"junk"]""",
            // Both lists, and both reasons.
            """"sets":[],"added":"oops"""",
            """"sets":[],"added":{"0":{"name":"x"}}""",
            """"sets":[{"ref":3,"line":"10 x squat"},{"ref":3,"line":"10 x row"}]""",
        )
        for (body in damaged) {
            assertNull("the read accepted $body", SessionFiles.parse(file(body)))
            assertTrue(
                "the save accepted $body",
                SessionPatch.apply(file(body), emptyMap()) is SessionPatch.Outcome.Refused,
            )
        }
        // ...and the shapes that are NOT damage are taken by both.
        for (body in listOf(""""sets":null""", """"sets":[]""", """"added":null""",
                """"sets":[{"ref":0,"line":"10 x squat"}]""")) {
            assertNotNull("the read refused $body", SessionFiles.parse(file(body)))
            assertTrue(
                "the save refused $body",
                SessionPatch.apply(file(body), emptyMap()) is SessionPatch.Outcome.Patched,
            )
        }
    }

    @Test
    fun `damage that is not about one row still costs the file`() {
        // The line between "one bad row" and "a bad file" is drawn in
        // exactly one place: whatever the SAVE refuses, the read must
        // refuse too. `SessionPatch.apply` refuses a `sets` that is not a
        // list and an element in it that is not an object, so reading
        // either as "a sheet with no rows" would open a form, let the
        // owner type a whole borrowed day into it, and then refuse the
        // save with a message about a list shown to them as empty.
        fun file(sets: String) =
            """{"schema":"gym-session/1","sheet_id":"s","date":"2026-10-17","sets":$sets}"""
        for (shape in listOf("""{"0":{"ref":0,"line":"x"}}""", "\"none\"", "7", "true")) {
            assertNull("a sets of $shape was read", SessionFiles.parse(file(shape)))
        }
        // An element that is not an object, beside one that is.
        assertNull(SessionFiles.parse(file("""[{"ref":0,"line":"10 x squat"},null]""")))
        assertNull(SessionFiles.parse(file("""[{"ref":0,"line":"10 x squat"},"junk"]""")))
        // ...and the two that are NOT damage: both mean no rows, and the
        // save takes both.
        assertEquals(0, SessionFiles.parse(file("null"))?.sets?.size)
        assertEquals(0, SessionFiles.parse(file("[]"))?.sets?.size)
    }

    @Test
    fun `a day that read short is left out of the history`() {
        // The Before and After tabs gain from showing what they can and
        // saying what they could not. This screen does not: its figures
        // are claims, and "heaviest 40" over a day whose 60 lb row did not
        // decode is a confident wrong number that nothing downstream could
        // tell from a right one.
        val short = """{"schema":"gym-session/1","sheet_id":"a","date":"2026-10-15","sets":[
            {"ref":0,"line":"10 x squat","canonical":"squat","key":"squat","family":"legs","weight":40,"reps":10},
            {"ref":1,"line":"10 x row","canonical":"row","key":"row","family":"pull","weight":"60 lb","reps":8}
        ]}"""
        val whole = short.replace(""""weight":"60 lb"""", """"weight":60""")
        assertEquals("the premise: one row of two reads", 1, SessionFiles.parse(short)!!.sets.size)

        assertEquals("a short day must not reach the history", 0, Growth.of(listOf(short)).sessions)
        assertEquals("the same file undamaged does", 1, Growth.of(listOf(whole)).sessions)
        assertEquals(2, Growth.of(listOf(whole)).sets)
    }

    @Test
    fun `a day left out of the history is not left out silently`() {
        // This is the screen that explains why the numbers are thin, so a
        // day missing from it without a word is the same failure as a
        // confident wrong total, one step quieter.
        fun day(date: String, extra: String = "") =
            """{"schema":"gym-session/1","sheet_id":"$date","date":"$date","sets":[
              {"ref":0,"line":"10 x squat","canonical":"squat","key":"squat","family":"legs",
               "weight":45,"reps":10}$extra
            ]}"""
        val whole = day("2026-10-01")
        val short = day("2026-10-14", """,{"ref":1,"line":"10 x row","weight":"60 lb"}""")
        // The premise, asserted rather than assumed.
        val parsedShort = SessionFiles.parse(short)
        assertEquals("the short file does not read at all", 1, parsedShort?.sets?.size)
        assertEquals("the short file is not short", 1, SessionFiles.unreadableRows(short, parsedShort!!))

        val since = Growth.of(listOf(whole, short))
        assertEquals("the short day must stay out of the totals", 1, since.sessions)
        assertEquals("and must be counted as left out", 1, since.short)
        assertEquals("1 day isn't counted here: part of its sheet didn't read.", shortNote(since.short))

        // Nothing to say when every file read whole.
        assertEquals(0, Growth.of(listOf(whole)).short)
        assertNull(shortNote(0))
    }

    @Test
    fun `a short newest sheet does not hand the band to yesterday`() {
        // The one figure that must NOT come from the filtered history.
        // "Part way to a bump" is a statement about where the owner stands
        // today; leaving a short newest sheet out does not leave a gap, it
        // promotes yesterday's sheet into the answer -- a wrong number
        // rather than a missing one, and the exact substitution the
        // comment on `newest` exists to prevent.
        fun day(date: String, done: Int, extra: String = "") =
            """{"schema":"gym-session/1","sheet_id":"$date","date":"$date","sets":[
              {"ref":0,"line":"10 x squat","canonical":"squat","key":"squat","family":"legs",
               "suggested_weight":45,"streak_done":$done,"streak_needed":3,"weight":45,"reps":10}$extra
            ]}"""
        val older = day("2026-10-01", 1)
        val newestShort = day("2026-10-14", 2, """,{"ref":1,"line":"10 x row","weight":"60 lb"}""")

        // The premise, asserted rather than assumed: a stray quote in this
        // fixture made the newest file unparseable instead of SHORT, and
        // the test then failed for the right-looking wrong reason.
        val parsedNewest = SessionFiles.parse(newestShort)
        assertEquals("the newest file does not read at all", 1, parsedNewest?.sets?.size)
        assertEquals("the newest file is not short", 1, SessionFiles.unreadableRows(newestShort, parsedNewest!!))

        val band = Growth.of(listOf(older, newestShort)).nearBump
        assertEquals("the band came from the wrong day", 2, band.single().done)
        // ...and the short day is still out of the history itself.
        assertEquals(1, Growth.of(listOf(older, newestShort)).sessions)
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
