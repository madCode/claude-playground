package com.app.carry

import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The folder is the history, so this is the only thing that decides what
 * "since you started" means. Two properties matter more than the shape of
 * the screen: it counts only what actually happened, and it never works
 * out coaching the runner didn't state.
 */
class GrowthTest {

    private fun file(
        date: String, cls: String = "3G", backfilled: Boolean = false, sets: String,
    ) = """
        {"schema":"gym-session/1","sheet_id":"$date:$cls","date":"$date","class":"$cls",
         ${if (backfilled) "\"backfilled\":true," else ""}
         "sets":[$sets],"added":[]}
    """.trimIndent()

    private fun set(
        ref: Int, name: String, family: String? = null, weight: String = "null",
        reps: String = "null", skipped: Boolean = false, variant: String = "null",
        done: String = "null", needed: String = "null", suggested: String = "null",
        key: String? = name, hard: Boolean = false,
    ) = """{"ref":$ref,"line":"10 x $name","canonical":"$name",
            ${key?.let { "\"key\":\"$it\"," } ?: ""}
            "family":${family?.let { "\"$it\"" } ?: "null"},"variant":$variant,
            "streak_done":$done,"streak_needed":$needed,"suggested_weight":$suggested,
            "weight":$weight,"reps":$reps,"skipped":$skipped,"hard":$hard}"""


    /** A sheet printed for the other block, left blank, with the real
     *  session typed into the added rows. */
    private fun borrowedDay(date: String, added: String) = """
        {"schema":"gym-session/1","sheet_id":"$date:2G","date":"$date","class":"3G",
         "borrowed_from":"2G",
         "sets":[${set(0, "bench press")},${set(1, "chest fly")}],
         "added":[$added]}
    """.trimIndent()

    @Test
    fun `a day logged entirely by hand is a session`() {
        // The owner's call, 2026-10-16: "Yes if the 2G was
        // incomplete/completely off or I left a class early after only
        // doing the row session, it should still count." The printed
        // sheet was for the other block and they ignored it; what they
        // did is in the rows they typed. It used to reach the history as
        // nothing at all.
        val day = borrowedDay("2026-10-11",
            """{"id":"a","name":"ybell deadlift","weight":26,"reps":12},
               {"id":"b","name":"ybell overhead press","weight":26,"reps":12}""")
        val since = Growth.of(listOf(day))

        assertEquals("the day must count as a session", 1, since.sessions)
        assertEquals(1, since.days.size)
        // ...and it must not read "0 lifts" over a list with weights in it.
        assertEquals(2, since.days.first().lifts)
        assertEquals(26.0, since.days.first().best?.weight)
        assertEquals("ybell deadlift", since.days.first().best?.name)
        assertEquals(2, since.sets)
    }

    @Test
    fun `an added row with only a name still says the movement happened`() {
        // Typing "ybell deadlift" and no numbers is a record that it
        // happened -- the sheet never listed it, so the name IS the reply.
        val day = borrowedDay("2026-10-11", """{"id":"a","name":"ybell deadlift"}""")
        val since = Growth.of(listOf(day))
        assertEquals("a named added row is an answer", 1, since.sessions)
        // No weight, so nothing to call heaviest, and that is not a session
        // of zero lifts being hidden -- it is a day of bodyweight work.
        assertEquals(0, since.days.first().lifts)
        assertNull(since.days.first().best)
    }

    @Test
    fun `a blank sheet row is still blank`() {
        // The gate on the rule above. A sheet row always has a name --
        // it is the prescription, not a reply -- so without the `added`
        // gate every untouched sheet would count as a logged session.
        val untouched = borrowedDay("2026-10-11", "")
        assertEquals("an untouched sheet is not a session", 0, Growth.of(listOf(untouched)).sessions)
    }

    @Test
    fun `a hand-typed weight does not withhold the movements count`() {
        // `movements`/`repeated` group by the runner's own key, and an
        // added row has none by design. Feeding them in would trip the
        // withholding guard and hide the count on any day with one
        // hand-typed weight -- a worse answer than a count that means
        // "among the movements the runner named".
        val sheet = file("2026-09-13", sets = set(0, "low row", "pull", weight = "30", reps = "10"))
        val byHand = borrowedDay("2026-10-11", """{"id":"a","name":"ybell deadlift","weight":26,"reps":12}""")
        val since = Growth.of(listOf(sheet, byHand))
        assertEquals(2, since.sessions)
        assertNotNull("the count must survive a keyless hand-typed lift", since.movements)
        assertEquals(1, since.movements)
        // And the band's denominator comes from the same population as its
        // numerator. `sets` counts the hand-typed lift; `movementSets`
        // must not, or the band reads "1 different movements in 2 sets".
        assertEquals(2, since.sets)
        assertEquals(1, since.movementSets)
    }

    @Test
    fun `a weighted row with no name does not become the day's heaviest`() {
        // The Log editor shows a nameless weighted row on purpose -- the
        // save refuses it, and an invisible refusal cannot be acted on.
        // Counting it here would DISPLACE the named lift that describes
        // the day, leaving the screen reading "heaviest , 99 pounds".
        val day = borrowedDay(
            "2026-10-11",
            """{"id":"a","name":"ybell deadlift","weight":26,"reps":12},{"id":"b","weight":99,"reps":5}""",
        )
        val since = Growth.of(listOf(day))
        assertEquals(1, since.sessions)
        assertEquals("the nameless row is not a lift", 1, since.sets)
        assertEquals("ybell deadlift", since.days.single().best?.name)
        assertEquals(26.0, since.days.single().best?.weight)
    }

    @Test
    fun `every session is listed, newest first, named by its heaviest lift`() {
        // "What did I do on the 27th" is a question about a DAY, and every
        // other band on that screen is cut across days.
        val since = Growth.of(
            listOf(
                file("2026-09-13", cls = "2G", sets = listOf(
                    set(0, "hip hinge swing", "legs", weight = "40"),
                    set(1, "bicep curl", "pull", weight = "15"),
                ).joinToString(",")),
                file("2026-09-27", sets = set(0, "sumo squat", "legs", weight = "50")),
            )
        )
        assertEquals(listOf("2026-09-27", "2026-09-13"), since.days.map { it.date })
        assertEquals("sumo squat", since.days[0].best?.name)
        assertEquals(2, since.days[1].lifts)
        assertEquals("hip hinge swing", since.days[1].best?.name)
        assertEquals("2G", since.days[1].classTag)
    }

    @Test
    fun `a day of nothing but bodyweight is listed with no weight against it`() {
        val since = Growth.of(
            listOf(
                file("2026-09-27", sets = set(0, "plank", "core", reps = "60")),
                file("2026-09-28", sets = set(0, "squat", "legs", weight = "25")),
            )
        )
        val bodyweight = since.days.single { it.date == "2026-09-27" }
        assertEquals(0, bodyweight.lifts)
        assertEquals(null, bodyweight.best)
    }

    @Test
    fun `the quiet band explains itself only while the quiet needs explaining`() {
        // Almost all distinct: almost nothing repeats, the streak rule
        // almost never fires, and the sheets look empty for a reason worth
        // saying out loud.
        fun since(n: Int, repeats: Int) = Growth.of(
            listOf(file("2026-09-27", sets = (0 until n).map {
                set(it, "m$it", "legs", weight = "20", key = "k${it % (n - repeats)}")
            }.joinToString(",")))
        )
        assertTrue("sparse history still needs the explanation", since(20, 1).explainsTheQuiet)

        // Once a quarter of them have come round again the coach is not
        // mostly quiet any more, and a band still saying so would be
        // telling the owner off for variety the studio chose for them.
        assertFalse("repeats now common", since(20, 8).explainsTheQuiet)

        // And with barely any training there is nothing to say about its
        // shape either way.
        assertFalse("too little to read", since(4, 1).explainsTheQuiet)
    }

    @Test
    fun `an empty folder adds up to nothing, and says so`() {
        val since = Growth.of(emptyList())
        assertTrue(since.empty)
        assertEquals(0, since.sessions)
        assertEquals(null, since.from)
    }

    @Test
    fun `one unreadable file does not cost the whole picture`() {
        // A half-synced file is normal in a shared folder, not exceptional.
        val since = Growth.of(
            listOf(
                "{ this is not json",
                file("2026-09-27", sets = set(0, "deadlift", "legs", weight = "40", reps = "8")),
            )
        )
        assertEquals(1, since.sessions)
        assertEquals(1, since.sets)
    }

    @Test
    fun `only a set with a real weight counts as a lift`() {
        // A blank row is not a zero, and `weight: 0` in the oldest records
        // meant both "bodyweight" and "skipped". Neither is a lift.
        val since = Growth.of(
            listOf(
                file(
                    "2026-09-27",
                    sets = listOf(
                        set(0, "deadlift", "legs", weight = "40", reps = "8"),
                        set(1, "plank", "core", weight = "0"),
                        set(2, "sit-up", "core"),
                        set(3, "squat", "legs", weight = "25", skipped = true),
                    ).joinToString(","),
                )
            )
        )
        assertEquals(1, since.sets)
        assertEquals("deadlift", since.peaks.single().best.name)
    }

    @Test
    fun `the heaviest in each family, heaviest family first`() {
        val since = Growth.of(
            listOf(
                file("2026-09-13", sets = listOf(
                    set(0, "hip hinge swing", "legs", weight = "40"),
                    set(1, "bicep curl", "pull", weight = "15"),
                ).joinToString(",")),
                file("2026-09-27", sets = listOf(
                    set(0, "goblet squat", "legs", weight = "25"),
                    set(1, "row", "pull", weight = "30"),
                ).joinToString(",")),
            )
        )
        assertEquals(listOf("legs", "pull"), since.peaks.map { it.family })
        assertEquals(40.0, since.peaks[0].best.weight, 0.0)
        assertEquals("row", since.peaks[1].best.name)
    }

    @Test
    fun `the band is every movement, each from the newest sheet that lists it`() {
        // The owner's call, 2026-10-16: "'Since' should be a history ...
        // Anything where if I happened to do that exercise today at that
        // level, I would achieve a bump." The newest sheet's own rows lead
        // and carry no date; an older sheet's rows follow and say theirs.
        val since = Growth.of(
            listOf(
                file("2026-09-13", sets = set(0, "deadlift", "legs", done = "2", needed = "3", suggested = "25")),
                file("2026-10-11", sets = set(0, "row", "pull", done = "1", needed = "3", suggested = "30")),
            )
        )
        assertEquals(listOf("deadlift", "row"), since.nearBump.map { it.name })
        assertEquals(listOf("2026-09-13", null), since.nearBump.map { it.asOf })
    }

    @Test
    fun `a newest sheet with no streak rows does not blank the band`() {
        // The bug this replaced: the band read only the newest sheet, so a
        // day whose sheet had nothing part way showed nothing at all.
        val since = Growth.of(
            listOf(
                file("2026-10-01", sets = set(0, "goblet squat", "legs", done = "1", needed = "3", suggested = "25")),
                file("2026-10-11", sets = set(0, "plank", "core")),
            )
        )
        assertEquals(listOf("goblet squat"), since.nearBump.map { it.name })
        assertEquals("2026-10-01", since.nearBump.single().asOf)
    }

    @Test
    fun `the newest listing of a movement decides, even when it leaves the band`() {
        // An older count is from before the newer set. When the newer row
        // is out of the band -- marked hard, the streak finished, or no
        // streak written -- the older count must not stand in for it.
        val older = file("2026-10-01", sets = listOf(
            set(0, "squat", "legs", done = "2", needed = "3", suggested = "25"),
            set(1, "row", "pull", done = "2", needed = "3", suggested = "30"),
            set(2, "press", "push", done = "2", needed = "3", suggested = "20"),
            set(3, "lunge", "legs", done = "1", needed = "3", suggested = "20"),
        ).joinToString(","))
        val newer = file("2026-10-11", sets = listOf(
            set(0, "squat", "legs", done = "1", needed = "3", suggested = "25", hard = true),
            set(1, "row", "pull", done = "3", needed = "3", suggested = "35"),
            set(2, "press", "push"),
            set(3, "lunge", "legs", done = "2", needed = "3", suggested = "20"),
            set(4, "curl", "pull", done = "2", needed = "3", suggested = "15", skipped = true),
            set(5, "fly", "push", weight = "10", done = "2", needed = "3", suggested = "15"),
        ).joinToString(","))
        val olderToo = older.replaceFirst("]", ",${set(4, "curl", "pull", done = "1", needed = "3", suggested = "15")}," +
            "${set(5, "fly", "push", done = "1", needed = "3", suggested = "15")}]")
        val since = Growth.of(listOf(olderToo, newer))
        assertEquals(listOf("lunge"), since.nearBump.map { it.name })
        assertEquals("the newer count, not the older", 2, since.nearBump.single().done)
        assertNull(since.nearBump.single().asOf)
    }

    @Test
    fun `a newer row that didn't decode still supersedes the older count`() {
        // The newest sheet lists squat, but its row won't decode, so the
        // parsed sheet has no squat. Its key is still on the raw row.
        val older = file("2026-10-01", sets = set(0, "squat", "legs", done = "2", needed = "3", suggested = "25"))
        val newer = file("2026-10-11", sets = set(0, "squat", "legs", done = "1", needed = "3", suggested = "25")
            .replace("\"weight\":null", "\"weight\":\"heavy\""))
        assertEquals("the fixture's row must not decode", 0, SessionFiles.parse(newer)!!.sets.size)
        assertEquals(emptyList<Growth.Bump>(), Growth.of(listOf(older, newer)).nearBump)
    }

    @Test
    fun `a sheet that won't read stops the band there, and says so`() {
        // It may list any movement with a newer count, so nothing older
        // can be shown as current.
        val oldest = file("2026-09-27", sets = set(0, "row", "pull", done = "2", needed = "3", suggested = "30"))
        val broken = """{"schema":"gym-session/1","sheet_id":"x","date":"2026-10-0"""
        val newest = file("2026-10-11", sets = set(0, "squat", "legs", done = "1", needed = "3", suggested = "25"))
        val since = Growth.of(
            listOf(oldest, broken, newest),
            byName = listOf("2026-10-11" to newest, "2026-10-04" to broken, "2026-09-27" to oldest),
        )
        assertEquals(listOf("squat"), since.nearBump.map { it.name })
        assertTrue(since.bandCut)
        assertFalse(
            Growth.of(listOf(oldest, newest), byName = listOf("2026-10-11" to newest, "2026-09-27" to oldest)).bandCut
        )
        // The other class of the broken sheet's own day is not cut: both
        // sheets were written from the same history.
        val sameDay = file("2026-10-04", cls = "2G", sets = set(0, "lunge", "legs", done = "1", needed = "3", suggested = "20"))
        val twoClass = Growth.of(
            listOf(oldest, broken, sameDay, newest),
            byName = listOf("2026-10-11" to newest, "2026-10-04" to broken, "2026-10-04" to sameDay, "2026-09-27" to oldest),
        )
        assertEquals(listOf("squat", "lunge"), twoClass.nearBump.map { it.name })
        assertTrue(twoClass.bandCut)
    }

    @Test
    fun `a movement listed twice on one sheet is not hidden by its skipped copy`() {
        val since = Growth.of(listOf(file("2026-10-11", sets = listOf(
            set(0, "squat", "legs", done = "2", needed = "3", suggested = "25", skipped = true),
            set(1, "squat", "legs", done = "2", needed = "3", suggested = "25"),
        ).joinToString(","))))
        assertEquals(listOf("squat"), since.nearBump.map { it.name })
    }

    @Test
    fun `an older sheet whose date won't read is dated as an older sheet`() {
        val since = Growth.of(
            listOf(
                file("2026-10-11", sets = set(0, "plank", "core")),
                file("", sets = set(0, "row", "pull", done = "2", needed = "3", suggested = "30")),
            ),
            byName = listOf(
                "2026-10-11" to file("2026-10-11", sets = set(0, "plank", "core")),
                "2026-10-04" to file("", sets = set(0, "row", "pull", done = "2", needed = "3", suggested = "30")),
            ),
        )
        assertEquals(listOf(Growth.OLDER_SHEET), since.nearBump.map { it.asOf })

        // Both dates blank: still not the newest sheet, so still dated.
        val blank = Growth.of(
            emptyList(),
            byName = listOf(
                "2026-10-11" to file("", sets = set(0, "plank", "core")),
                "2026-10-04" to file("", sets = set(0, "row", "pull", done = "2", needed = "3", suggested = "30")),
            ),
        )
        assertEquals(listOf(Growth.OLDER_SHEET), blank.nearBump.map { it.asOf })

        // An older file carrying the newest sheet's date inside it.
        val copied = Growth.of(
            emptyList(),
            byName = listOf(
                "2026-10-11" to file("2026-10-11", sets = set(0, "plank", "core")),
                "2026-10-04" to file("2026-10-11", sets = set(0, "row", "pull", done = "2", needed = "3", suggested = "30")),
            ),
        )
        assertEquals(listOf(Growth.OLDER_SHEET), copied.nearBump.map { it.asOf })
    }

    @Test
    fun `an older row with no key is left out, since nothing says whether it was superseded`() {
        val since = Growth.of(
            listOf(
                file("2026-10-01", sets = set(0, "row", "pull", done = "2", needed = "3", suggested = "30", key = null)),
                file("2026-10-11", sets = set(0, "squat", "legs", done = "1", needed = "3", suggested = "25", key = null)),
            )
        )
        assertEquals(listOf("squat"), since.nearBump.map { it.name })
    }

    @Test
    fun `a streak that is finished or not started is not close to anything`() {
        val since = Growth.of(
            listOf(
                file("2026-10-11", sets = listOf(
                    set(0, "a", "legs", done = "0", needed = "3", suggested = "20"),
                    set(1, "b", "legs", done = "3", needed = "3", suggested = "20"),
                    set(2, "c", "legs", done = "2", needed = "3", suggested = "20"),
                ).joinToString(",")),
            )
        )
        assertEquals(listOf("c"), since.nearBump.map { it.name })
    }

    @Test
    fun `rows the same distance away keep the sheet's own order`() {
        // The case the docs call usual: every row on the same rung, so
        // neither rule in the comparator separates them. What decides
        // which five of them the screen shows is then the board's order,
        // and nothing held that -- the ordering tests above all use rows
        // that one rule or the other can tell apart.
        val since = Growth.of(
            listOf(
                file("2026-10-11", sets = listOf(
                    set(0, "first on the board", "legs", done = "1", needed = "3", suggested = "20"),
                    set(1, "second", "legs", done = "1", needed = "3", suggested = "25"),
                    set(2, "third", "legs", done = "1", needed = "3", suggested = "30"),
                ).joinToString(",")),
            )
        )
        assertEquals(
            listOf("first on the board", "second", "third"),
            since.nearBump.map { it.name },
        )
    }

    @Test
    fun `a row their own answer contradicts leaves the band`() {
        // The count was written before the class. `hard` is the runner's
        // own signal that the next bump is blocked, and a lighter set is
        // not a set at that weight -- so "part way to a bump" is no
        // longer true of either row, and the band is capped, so each
        // would be taking a slot from one that is.
        val since = Growth.of(
            listOf(
                file("2026-10-11", sets = listOf(
                    set(0, "went lighter", "legs", done = "2", needed = "3",
                        suggested = "40", weight = "35", reps = "8"),
                    set(1, "found it hard", "legs", done = "2", needed = "3",
                        suggested = "40", weight = "40", reps = "8", hard = true),
                    set(2, "did it as asked", "legs", done = "2", needed = "3",
                        suggested = "40", weight = "40", reps = "8"),
                ).joinToString(",")),
            )
        )
        // The third stays: that count is stale by one and understates,
        // which is the safe direction, and re-deriving it here is the one
        // thing this file must not do.
        assertEquals(listOf("did it as asked"), since.nearBump.map { it.name })
    }

    @Test
    fun `a heavier set than asked for does not leave the band`() {
        // Only a LIGHTER one breaks it. Going up unprompted is the thing
        // this app exists to notice, not a reason to stop counting.
        val since = Growth.of(
            listOf(
                file("2026-10-11", sets = listOf(
                    set(0, "went heavier", "legs", done = "2", needed = "3",
                        suggested = "40", weight = "45", reps = "8"),
                ).joinToString(",")),
            )
        )
        assertEquals(listOf("went heavier"), since.nearBump.map { it.name })
    }

    @Test
    fun `a movement they skipped does not take a slot in the band`() {
        // The band is capped on screen, so a row they said they did not do
        // would push out one they worked on. The same exclusion the lift
        // counts in this file already make.
        val since = Growth.of(
            listOf(
                file("2026-10-11", sets = listOf(
                    set(0, "skipped one", "legs", done = "2", needed = "3",
                        suggested = "20", skipped = true),
                    set(1, "did it", "legs", done = "1", needed = "3", suggested = "20"),
                ).joinToString(",")),
            )
        )
        assertEquals(listOf("did it"), since.nearBump.map { it.name })
    }

    @Test
    fun `the rows nearest a bump come first`() {
        // The screen caps this band, so the order decides which rows the
        // owner sees at all. Nothing held it until 2026-10-16: the one
        // ordering test above asserted a ONE-element list, and the screen
        // test sorted its own fixture before handing it over -- so deleting
        // the sort here left every test in the project green while the
        // screen silently showed an arbitrary five.
        val since = Growth.of(
            listOf(
                file("2026-10-11", sets = listOf(
                    set(0, "a", "legs", done = "1", needed = "3", suggested = "20"),
                    set(1, "b", "legs", done = "1", needed = "2", suggested = "20"),
                    set(2, "c", "legs", done = "2", needed = "3", suggested = "20"),
                ).joinToString(",")),
            )
        )
        // One set away, one set away, two away -- and between the first
        // two, the one further along. A fraction would put "a" (one of
        // three) last either way, so the case that tells the two rules
        // apart is the next test.
        assertEquals(listOf("c", "b", "a"), since.nearBump.map { it.name })
    }

    @Test
    fun `one set from a bump outranks two sets from a nearer-looking one`() {
        // The band is capped on screen, so the order decides what the
        // owner sees. Sorted by how far ALONG, 8 of 10 (two sets away)
        // beats 2 of 3 (one set away) -- and the one movement a single
        // clean set would bump is the one that gets cut. The row's own
        // line is "another clean set moves it", so that is what it sorts
        // by. Not reachable while every `needed` is 3; the runner's rule
        // is free to differ per movement.
        val since = Growth.of(
            listOf(
                file("2026-10-11", sets = listOf(
                    set(0, "two away", "legs", done = "8", needed = "10", suggested = "20"),
                    set(1, "one away", "legs", done = "2", needed = "3", suggested = "20"),
                ).joinToString(",")),
            )
        )
        assertEquals(listOf("one away", "two away"), since.nearBump.map { it.name })
    }

    @Test
    fun `nothing is invented when the runner said nothing`() {
        // A back-filled day has no sheet behind it, so it has no streaks.
        // Working them out here would be a second implementation of a rule
        // that lives in the runner, and the two would drift.
        val since = Growth.of(
            listOf(file("2026-09-27", backfilled = true, sets = listOf(
                set(0, "goblet squat", "legs", weight = "25", reps = "10"),
                set(1, "goblet squat", "legs", weight = "25", reps = "10"),
            ).joinToString(",")))
        )
        assertEquals(emptyList<Growth.Bump>(), since.nearBump)
        assertEquals(2, since.sets)
    }

    @Test
    fun `two rows are the same movement only if the file says they are`() {
        // By the runner's key, not by anything worked out here. The same
        // movement at two implements is two movements; the same key twice
        // is one done twice -- and Carry is told which, never deciding.
        val since = Growth.of(
            listOf(
                file("2026-09-13", sets = listOf(
                    set(0, "goblet squat", "legs", weight = "25", key = "squat-1db"),
                    set(1, "goblet squat", "legs", weight = "25", key = "squat-2db"),
                ).joinToString(",")),
                file("2026-09-27", sets =
                    set(0, "goblet squat", "legs", weight = "30", key = "squat-1db")),
            )
        )
        // Three sets, two distinct identities, one of them done twice --
        // which is the fact that explains why the coach is usually quiet.
        assertEquals(3, since.sets)
        assertEquals(2, since.movements)
        assertEquals(1, since.repeated)
    }

    @Test
    fun `a count is withheld when the file never said what the same movement is`() {
        // Files written before the runner carried a key. Guessing an
        // identity here would put a confident wrong number on the one
        // band whose job is to explain why the numbers are thin.
        val since = Growth.of(
            listOf(file("2026-09-13", sets = listOf(
                set(0, "goblet squat", "legs", weight = "25", key = null),
                set(1, "deadlift", "legs", weight = "40", key = null),
            ).joinToString(",")))
        )
        assertEquals(2, since.sets)
        assertEquals(null, since.movements)
        assertEquals(null, since.repeated)
    }

    @Test
    fun `the span runs from the first session to the last`() {
        val since = Growth.of(
            listOf(
                file("2026-09-27", sets = set(0, "a", "legs", weight = "10")),
                file("2026-09-13", sets = set(0, "b", "legs", weight = "10")),
                file("2026-10-11", sets = set(0, "c", "legs", weight = "10")),
            )
        )
        assertEquals("2026-09-13", since.from)
        assertEquals("2026-10-11", since.to)
        assertEquals(3, since.sessions)
    }

    @Test
    fun `a day carries its own rows, blanks included, in sheet order`() {
        // The aggregates cannot answer "what did I lift on the 27th": a
        // row with nothing on it is absent from every count on that
        // screen, and which lines were left empty is half the answer.
        val since = Growth.of(
            listOf(file("2026-09-27", sets = listOf(
                set(0, "sumo squat", "legs", weight = "50", reps = "10"),
                set(1, "lateral raise", "push"),
                set(2, "low plank", "core", skipped = true),
            ).joinToString(",")))
        )
        val day = since.days.single()
        assertEquals(listOf("sumo squat", "lateral raise", "low plank"), day.rows.map { it.name })
        assertEquals(listOf(50.0, null, null), day.rows.map { it.weight })
        assertEquals(listOf(true, false, true), day.rows.map { it.answered })
        // One lift, three rows: the row count is not the lift count, and
        // the screen says both.
        assertEquals(1, day.lifts)
    }

    @Test
    fun `a hold the owner only ticked off reads back as a hold they did`() {
        // The whole reason `done` exists is that this row used to come
        // back as "blank" -- the screen telling them they skipped the one
        // thing they did say. There is no weight and no rep count to show
        // for a 45-second plank, so the mark IS the record.
        val since = Growth.of(
            listOf(file("2026-09-27", sets =
                """{"ref":0,"line":"45 sec of low plank","canonical":"low plank",
                    "key":"low plank","family":"core","variant":null,
                    "prescribed_duration_sec":45,
                    "weight":null,"reps":null,"done":true,"skipped":false}"""))
        )
        val row = since.days.single().rows.single()
        assertTrue("a row they ticked off is not blank", row.answered)
        assertEquals(listOf("done"), row.marks)
        // Still not a lift and still not a number: nothing here invents
        // one, which is the other half of the point.
        assertEquals(null, row.weight)
        assertEquals(0, since.days.single().lifts)
    }

    @Test
    fun `two classes in one day are counted apart, not added together`() {
        // Each row in the list, and now the header of the session it
        // opens, states a count and a heaviest lift ALONGSIDE that one
        // session's rows. Summing across the date puts the other class's
        // 50 on this class's day, with no 50 in the rows underneath it.
        val since = Growth.of(
            listOf(
                file("2026-09-27", cls = "2G", sets = set(0, "a", "legs", weight = "50")),
                file("2026-09-27", cls = "3G", sets = set(0, "b", "legs", weight = "25")),
            )
        )
        val own = since.days.associateBy { it.sheetId }
        assertEquals(1, own.getValue("2026-09-27:2G").lifts)
        assertEquals(50.0, own.getValue("2026-09-27:2G").best?.weight)
        assertEquals(1, own.getValue("2026-09-27:3G").lifts)
        assertEquals(25.0, own.getValue("2026-09-27:3G").best?.weight)
        // Both still count towards the totals, which are about the folder
        // rather than about one day.
        assertEquals(2, since.sets)
    }

    @Test
    fun `a day is opened by the session's own id, not by where it sits`() {
        // The list reorders as files arrive, and two classes in one day
        // share a date. The sheet id is the only stable handle.
        val since = Growth.of(
            listOf(
                file("2026-09-27", cls = "2G", sets = set(0, "a", "legs", weight = "10")),
                file("2026-09-27", cls = "3G", sets = set(0, "b", "legs", weight = "10")),
            )
        )
        assertEquals(setOf("2026-09-27:2G", "2026-09-27:3G"), since.days.map { it.sheetId }.toSet())
    }

    @Test
    fun `the rows the owner added are part of the day`() {
        // They are not in the Session model at all -- `added` is read out
        // of the raw JSON, because decoding it as part of the session made
        // one odd row cost the whole day. On a borrowed day they are the
        // entire session, so a day built from the model alone would read
        // back as empty.
        val text = file("2026-09-27", sets = set(0, "sumo squat", "legs", weight = "50"))
            .replace(""""added":[]""", """"added":[{"id":"a1","name":"farmer carry","weight":35,"reps":20}]""")
        val day = Growth.of(listOf(text)).days.single()
        assertEquals(listOf("sumo squat", "farmer carry"), day.rows.map { it.name })
        assertEquals(listOf(false, true), day.rows.map { it.added })
        // Added rows DO count toward the day's lifts, as of 2026-10-16.
        // This assertion used to be 1, with a note that changing it would
        // move the numbers on every band of the Since screen -- which was
        // true, and why it was the owner's call rather than a bug fix.
        // They made it: a day logged entirely by hand is a day they
        // trained, so a carry at 35 lb is a lift whoever typed it.
        assertEquals(2, day.lifts)
        assertEquals(50.0, day.best?.weight)
    }

    @Test
    fun `an added name that would render backwards is stripped, like a sheet row's`() {
        // Typed by hand on another machine. A bidi override makes the line
        // render in an order other than the one stored, which in this app
        // is the same kind of failure as a wrong number.
        val text = file("2026-09-27", sets = set(0, "sumo squat", "legs", weight = "50"))
            .replace(""""added":[]""", """"added":[{"id":"a1","name":"\u202ecarry","weight":35}]""")
        val day = Growth.of(listOf(text)).days.single()
        assertEquals("carry", day.rows.last().name)
    }

    @Test
    fun `an odd value in the backfilled flag does not cost the whole session`() {
        // That field is written by whatever back-filled the day, not by the
        // runner and not by Carry. Reading it through the Session model
        // made a non-boolean throw during deserialization -- which the
        // loader turns into "Can't read that session", taking the sheet on
        // the Before tab with it. Exactly the failure `added` is kept out
        // of the model to avoid.
        for (odd in listOf("null", "1", "\"true\"")) {
            val text = file("2026-09-27", sets = set(0, "a", "legs", weight = "10"))
                .replace(""""sets":[""", """"backfilled":$odd,"sets":[""")
            val since = Growth.of(listOf(text))
            assertEquals("a $odd backfilled flag cost the session", 1, since.days.size)
            assertFalse(since.days.single().backfilled)
        }
    }

    @Test
    fun `a backfilled day says so, so it is never offered as a form`() {
        val since = Growth.of(
            listOf(
                file("2026-09-27", backfilled = true, sets = set(0, "a", "legs", weight = "10")),
                file("2026-10-11", sets = set(0, "b", "legs", weight = "10")),
            )
        )
        assertEquals(listOf(false, true), since.days.map { it.backfilled })
    }
}
