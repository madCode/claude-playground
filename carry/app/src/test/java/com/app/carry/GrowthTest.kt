package com.app.carry

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
        key: String? = name,
    ) = """{"ref":$ref,"line":"10 x $name","canonical":"$name",
            ${key?.let { "\"key\":\"$it\"," } ?: ""}
            "family":${family?.let { "\"$it\"" } ?: "null"},"variant":$variant,
            "streak_done":$done,"streak_needed":$needed,"suggested_weight":$suggested,
            "weight":$weight,"reps":$reps,"skipped":$skipped}"""

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
        // 19 of 20 distinct: almost nothing repeats, the streak rule
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
        // Oldest to newest, so the bar on the right is the latest.
        assertEquals(listOf(40.0, 25.0), since.peaks[0].trail)
    }

    @Test
    fun `near-a-bump comes from the newest sheet and nowhere else`() {
        // A streak is a statement about where the owner stands TODAY; an
        // older file's is a fact about a day that has been and gone.
        val since = Growth.of(
            listOf(
                file("2026-09-13", sets = set(0, "deadlift", "legs", weight = "25", done = "2", needed = "3", suggested = "25")),
                file("2026-10-11", sets = set(0, "row", "pull", weight = "30", done = "1", needed = "3", suggested = "30")),
            )
        )
        assertEquals(1, since.nearBump.size)
        assertEquals("row", since.nearBump.single().name)
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
        // Added rows carry no weight toward the lift count, which comes
        // from the sheet's own sets -- changing that would move the
        // numbers on every band of the Since screen.
        assertEquals(1, day.lifts)
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
