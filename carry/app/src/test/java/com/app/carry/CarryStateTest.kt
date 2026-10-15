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
        assertEquals("1 session isn't in these totals: its sheet didn't read cleanly. A sync part way through can do that.", shortNote(since.short))

        // Nothing to say when every file read whole.
        assertEquals(0, Growth.of(listOf(whole)).short)
        assertNull(shortNote(0))
    }

    @Test
    fun `an unreadable newest sheet withholds the band rather than showing yesterday's`() {
        // "Part way to a bump" is the one figure on that screen that is a
        // claim about TODAY. A newest file that will not parse is not in
        // the list the band is taken from, so the band quietly became the
        // previous session's -- a perfectly well-formed number under a
        // heading that says it is about now.
        fun day(date: String, done: Int) =
            """{"schema":"gym-session/1","sheet_id":"$date","date":"$date","sets":[
              {"ref":0,"line":"10 x squat","canonical":"squat","key":"squat","family":"legs",
               "suggested_weight":45,"streak_done":$done,"streak_needed":3,"weight":45,"reps":10}
            ]}"""
        val older = day("2026-10-01", 1)
        val newest = day("2026-10-14", 2)

        // The premise: with both readable the band is TODAY's count, and
        // the older day's is a different number -- so a fallback is
        // detectable rather than invisible.
        assertEquals(2, Growth.of(listOf(older, newest)).nearBump.single().done)
        assertEquals(1, Growth.of(listOf(older)).nearBump.single().done)

        val withheld = Growth.of(listOf(older), newestRead = Growth.NewestRead.DAMAGED)
        assertTrue("the band must be withheld, not inherited", withheld.nearBump.isEmpty())
        assertEquals(Growth.NewestRead.DAMAGED, withheld.newestRead)
        // Everything else still reads: only the band is about today.
        assertEquals(1, withheld.sessions)
    }

    @Test
    fun `the folder decides whether the newest sheet read, not Growth`() {
        // The wiring, which no test of `Growth.of` can cover: it sees text
        // and nothing else, so it cannot tell a newest sheet that failed
        // to parse from a folder that simply ends earlier. Only the names
        // answer that, and they are dropped one layer up.
        val older = """{"schema":"gym-session/1","sheet_id":"a","date":"2026-10-01","sets":[
            {"ref":0,"line":"10 x squat","canonical":"squat","key":"squat","family":"legs",
             "suggested_weight":45,"streak_done":1,"streak_needed":3,"weight":45,"reps":10}
        ]}"""
        val broken = """{"schema":"gym-session/1","sheet_id":"b","date":"2026-10-14","""

        val newestBroken = growthOf(FakeFolder(
            files = listOf("d1" to "session-2026-10-01-2G.json", "d2" to "session-2026-10-14-3G.json"),
            contents = mapOf("d1" to older, "d2" to broken),
        ))
        assertTrue("the band came from the older day", newestBroken.nearBump.isEmpty())
        assertEquals(Growth.NewestRead.DAMAGED, newestBroken.newestRead)

        // ...and an OLDER broken file must not cost the band, because the
        // newest sheet read fine and the band is only ever about that one.
        val olderBroken = growthOf(FakeFolder(
            files = listOf("d1" to "session-2026-10-01-2G.json", "d2" to "session-2026-10-14-3G.json"),
            contents = mapOf("d1" to broken, "d2" to older.replace("2026-10-01", "2026-10-14")),
        ))
        assertEquals(1, olderBroken.nearBump.size)
        assertEquals(Growth.NewestRead.FINE, olderBroken.newestRead)
    }

    @Test
    fun `one file at the newest date reading is enough to keep the band`() {
        // Two classes on one day are two files -- it is what `sheet_id`
        // exists for -- and if either of them read, that day has a sheet
        // the band can legitimately come from. The gate asks about the
        // newest DATE for this reason: asking about the newest named FILE
        // withheld a band the other file supplied perfectly well, because
        // `SessionFiles.newest` breaks a date tie on the name and
        // `Growth.of` keeps the first session at the newest date.
        val good = """{"schema":"gym-session/1","sheet_id":"2G","date":"2026-10-14","sets":[
            {"ref":0,"line":"10 x squat","canonical":"squat","key":"squat","family":"legs",
             "suggested_weight":45,"streak_done":2,"streak_needed":3,"weight":45,"reps":10}
        ]}"""
        val broken = """{"schema":"gym-session/1","sheet_id":"3G","date":"2026-10-14","""

        val since = growthOf(FakeFolder(
            files = listOf("d1" to "session-2026-10-14-2G.json", "d2" to "session-2026-10-14-3G.json"),
            contents = mapOf("d1" to good, "d2" to broken),
        ))
        assertEquals(Growth.NewestRead.FINE, since.newestRead)
        assertEquals("the readable class of that day must still supply the band", 2,
            since.nearBump.single().done)
    }

    @Test
    fun `a newer schema withholds the band without calling the file damaged`() {
        // The Before tab says "update Carry" about this same file. Two
        // screens describing one intact file differently is worse than
        // either message alone -- and this is the case that does not
        // settle on its own, so it is the one the owner meets.
        val older = """{"schema":"gym-session/1","sheet_id":"a","date":"2026-10-01","sets":[
            {"ref":0,"line":"10 x squat","canonical":"squat","key":"squat","family":"legs",
             "suggested_weight":45,"streak_done":1,"streak_needed":3,"weight":45,"reps":10}
        ]}"""
        val newer = """{"schema":"gym-session/2","sheet_id":"b","date":"2026-10-14","sets":[]}"""
        val since = growthOf(FakeFolder(
            files = listOf("d1" to "session-2026-10-01-2G.json", "d2" to "session-2026-10-14-3G.json"),
            contents = mapOf("d1" to older, "d2" to newer),
        ))
        assertEquals(Growth.NewestRead.NEWER_SCHEMA, since.newestRead)
        assertTrue("the band must still be withheld", since.nearBump.isEmpty())
    }

    @Test
    fun `a mixed failure at the newest date is damage, not an out-of-date app`() {
        // `all`, not `any`. If one file at the newest date is merely a
        // newer schema and the other is genuinely broken, "update Carry"
        // is the wrong instruction -- updating would not rescue the
        // truncated one. Only when EVERY file there is a newer schema is
        // the app the thing that is behind.
        val newer = """{"schema":"gym-session/2","sheet_id":"a","date":"2026-10-14","sets":[]}"""
        val truncated = """{"schema":"gym-session/1","sheet_id":"b","date":"2026-10-14","""
        val mixed = growthOf(FakeFolder(
            files = listOf("d1" to "session-2026-10-14-2G.json", "d2" to "session-2026-10-14-3G.json"),
            contents = mapOf("d1" to newer, "d2" to truncated),
        ))
        assertEquals(Growth.NewestRead.DAMAGED, mixed.newestRead)

        // ...and with BOTH a newer schema it is the app, not the files.
        val bothNewer = growthOf(FakeFolder(
            files = listOf("d1" to "session-2026-10-14-2G.json", "d2" to "session-2026-10-14-3G.json"),
            contents = mapOf("d1" to newer, "d2" to newer.replace(""""a"""", """"b"""")),
        ))
        assertEquals(Growth.NewestRead.NEWER_SCHEMA, bothNewer.newestRead)
    }

    @Test
    fun `the newest file is read once, not twice`() {
        // The second read opened a window a sync can land in -- and the
        // file a sync is replacing is by definition the newest one. The
        // two answers then disagree in both directions: a truncated first
        // read and a whole second one puts the older day's band back with
        // no marker at all.
        val whole = """{"schema":"gym-session/1","sheet_id":"a","date":"2026-10-14","sets":[
            {"ref":0,"line":"10 x squat","canonical":"squat"}
        ]}"""
        val reads = mutableListOf<String>()
        val folder = object : Folder {
            override val label = "f"
            override val chosen = true
            override val grantLost = false
            override fun list() = listOf("d1" to "session-2026-10-14-2G.json")
            override fun write(documentId: String, text: String) = false
            override fun read(documentId: String): String {
                reads += documentId
                return whole
            }
        }
        growthOf(folder)
        assertEquals("the newest file was read more than once", listOf("d1"), reads)
    }

    @Test
    fun `a sheet where no row read is counted, not treated as having nothing in it`() {
        // The case the whole rewording was about, and the first version
        // did not reach it. Every row failing still PARSES, so the file
        // was neither "did not parse" nor "read short with evidence" --
        // it fell between the two halves of the count and said nothing.
        // A folder of these rendered "Nothing logged yet" over a folder
        // of well-formed session files whose dates the app can read.
        //
        // `wasDone` guards against a BLANK sheet, and a blank sheet is
        // eleven rows that read fine. Zero rows read is not evidence of
        // nothing; it is the absence of evidence.
        val none = """{"schema":"gym-session/1","sheet_id":"s","date":"2026-10-14","sets":[
            {"ref":0,"line":"10 x squat","weight":"45 lb"},
            {"ref":1,"line":"10 x row","done":"yes"}
        ]}"""
        val parsed = SessionFiles.parse(none)
        assertEquals("the premise: it parses with no rows", 0, parsed?.sets?.size)
        assertEquals("the premise: both rows failed", 2, SessionFiles.unreadableRows(none, parsed!!))

        val since = Growth.of(listOf(none))
        assertEquals(1, since.short)
        assertTrue("a file that parsed is not a schema problem", since.newerSchema == 0)
    }

    @Test
    fun `a newer schema is told apart from damage, because the other tab tells them to update`() {
        // One screen calling an intact file broken while the other says
        // "Carry is out of date -- update Carry" is worse than either
        // message alone. And this is the one case in the group that does
        // NOT settle on its own.
        val newer = """{"schema":"gym-session/2","sheet_id":"c","date":"2026-10-03","sets":[]}"""
        val truncated = """{"schema":"gym-session/1","sheet_id":"b","date":"2026-10-02","""
        val since = Growth.of(listOf(newer, truncated))
        assertEquals("a newer schema is not damage", 1, since.short)
        assertEquals(1, since.newerSchema)
        assertEquals(
            "1 session was written by a newer coach than this app understands. Update Carry.",
            newerSchemaNote(since.newerSchema),
        )
        assertNull(newerSchemaNote(0))
    }

    @Test
    fun `the damage line says a sync can cause it, as the other tab does`() {
        // A file being replaced by a sync reads as zero bytes for a
        // moment, and the history is re-read on every resume -- so a red
        // warning asserting permanent loss can land on a file that will
        // be fine by the next glance.
        assertTrue(
            shortNote(1)!!.endsWith("A sync part way through can do that."),
        )
    }

    @Test
    fun `a short sheet nobody filled in is not reported as a loss`() {
        // The difference between reporting a loss and inventing one. A
        // sheet of blank rows was never going to count -- eleven of them
        // sat in the real folder once -- so damage to it costs the history
        // nothing, and claiming a day was lost asserts something the data
        // cannot support. On the one screen whose job is explaining thin
        // numbers, that is a manufactured loss.
        fun sheet(rows: String) =
            """{"schema":"gym-session/1","sheet_id":"s","date":"2026-10-14","sets":[$rows]}"""
        val answered = """{"ref":0,"line":"10 x squat","canonical":"squat","key":"squat","family":"legs","weight":45,"reps":10}"""
        val blank = """{"ref":0,"line":"10 x squat","canonical":"squat"}"""
        val broken = """{"ref":9,"line":"10 x row","weight":"60 lb"}"""

        // Blank + damaged: identical history with or without the damage.
        val blankShort = sheet("$blank,$broken")
        assertEquals("the premise: it reads short", 1,
            SessionFiles.unreadableRows(blankShort, SessionFiles.parse(blankShort)!!))
        assertEquals(0, Growth.of(listOf(blankShort)).short)
        assertEquals(0, Growth.of(listOf(sheet(blank))).sessions)

        // Answered + damaged: a real loss, and counted.
        val realShort = sheet("$answered,$broken")
        assertEquals(1, Growth.of(listOf(realShort)).short)
        assertEquals("the damaged day must stay out of the totals", 0,
            Growth.of(listOf(realShort)).sessions)
    }

    @Test
    fun `a file that did not parse at all is counted too, not dropped in silence`() {
        // The louder half of the same silence. `Growth.of` only ever saw
        // the files that parsed, so a truncated one, a newer schema and a
        // file with duplicate refs all vanished without a word -- and a
        // folder of nothing but those said "Nothing logged yet" over a
        // folder full of sessions.
        val whole = """{"schema":"gym-session/1","sheet_id":"a","date":"2026-10-01","sets":[
            {"ref":0,"line":"10 x squat","canonical":"squat","key":"squat","family":"legs","weight":45,"reps":10}
        ]}"""
        val truncated = """{"schema":"gym-session/1","sheet_id":"b","date":"2026-10-02","""
        val newer = """{"schema":"gym-session/2","sheet_id":"c","date":"2026-10-03","sets":[]}"""
        val since = Growth.of(listOf(whole, truncated, newer))
        assertEquals(1, since.sessions)
        // The newer schema is counted apart: it is not damage, and the
        // Before tab says "update Carry" about the same file.
        assertEquals(1, since.short)
        assertEquals(1, since.newerSchema)
        assertEquals(
            "1 session isn't in these totals: its sheet didn't read cleanly. A sync part way through can do that.",
            shortNote(since.short),
        )
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
