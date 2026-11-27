package spotter.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// An invented lifter's classes.
class PastTest {
    private val vocabulary = Vocabulary.starting()
    private val identity = Identity(vocabulary)
    private fun known(line: String) = identity.read(BoardLine.read(line).words) as Reading.Known
    private fun day(n: Int) = LocalDate.of(2026, 11, n)

    private var ids = 0L
    private fun entry(day: Int, cls: String, line: String, lb: Int, reps: List<Int>, count: Int? = 2, hurt: Boolean = false, easy: Boolean = false) =
        Journal.logged(1L, day(day), cls, "r${ids + 1}", line, known(line), Load(lb, count), 8..8, null, reps, hurt = hurt, easy = easy).copy(id = ++ids)

    private fun file(day: Int, cls: String?, line: String, lb: Int) =
        Imported(Logged(day(day), known(line), Load(lb, 2), 8..8, listOf(8)), cls, line)

    private val own = Journal.read(
        listOf(
            entry(16, "2G", "8 x deadlift", 40, listOf(8, 8)),
            entry(21, "2G", "8 x deadlift", 45, listOf(7), hurt = true),
            entry(21, "2G", "8 x close grip chest press (explosive)", 30, listOf(8), easy = true),
            entry(21, "3G", "8 total x alt dumbbell deadlift", 40, listOf(8)),
        ),
        vocabulary,
    )
    private val imported = listOf(
        file(10, "2G", "8 x deadlift", 40), file(10, "2G", "8 x squat", 30), file(12, null, "8 x low row", 25),
        // The same day as a class logged here: the file's copy isn't shown beside it.
        file(16, "2G", "8 x deadlift", 40),
    )

    // Newest first, the log's classes and the file's together, a day logged here once.
    @Test
    fun classesAreListedNewestFirst() {
        val classes = Past.classes(own, imported)
        assertEquals(listOf("2026-11-21 2G", "2026-11-21 3G", "2026-11-16 2G", "2026-11-12 class", "2026-11-10 2G"), classes.map { it.id })
        assertEquals(listOf(false, false, false, true, true), classes.map { it.imported })
        assertEquals(listOf(2, 1, 1, 1, 2), classes.map { it.rows.size })
        assertTrue(Past.classes(emptyList(), emptyList()).isEmpty())
    }

    // A row says what was held, the reps and what was said, in plain words.
    @Test
    fun aRowSaysWhatWasDone() {
        val saturday = Past.classes(own, imported).first().rows
        assertEquals(listOf("deadlift", "close grip explosive chest press"), saturday.map { it.name })
        assertEquals(listOf("7 reps · twinge", "8 reps · felt easy"), saturday.map { it.detail })
        assertEquals(Load(45, 2), saturday.first().logged!!.load)
        val known = known("8 x squat")
        fun row(logged: Logged) = PastRow("squat", logged)
        assertEquals("8, 8, 6 reps", row(Logged(day(1), known, Load(30, 2), 8..8, listOf(8, 8, 6))).reps)
        assertEquals("10 / 8 / 6", row(Logged(day(1), known, Load(30, 2), 10..10, listOf(10, 8, 6), ladder = listOf(10, 8, 6))).reps)
        assertEquals("timed", row(Logged(day(1), known, Load(30, 2), null, emptyList())).detail)
        assertEquals("", row(Logged(day(1), known, Load(30, 2), 8..8, emptyList())).detail)
    }

    // One lift over time is the sessions its number is read from: its other wordings that
    // take the same dumbbells too, and nothing of another lift.
    @Test
    fun aLiftOverTimeIsWhatItsNumberRestsOn() {
        val classes = Past.classes(own, imported)
        val deadlift = Past.lift(known("8 x deadlift").key, classes)
        assertEquals(listOf(day(21), day(21), day(16), day(10)), deadlift.map { it.date })
        assertEquals(listOf(45, 40, 40, 40), deadlift.map { it.row.logged!!.load.lb })
        assertEquals(listOf(false, false, false, true), deadlift.map { it.imported })
        assertEquals(listOf(day(10)), Past.lift(known("8 x squat").key, classes).map { it.date })
        assertTrue(Past.lift("no such lift", classes).isEmpty())
    }

    // A row on the sheet never done in its own words opens the sessions its number is read
    // from, judged from its side, under its own name; a lift never done opens nothing.
    @Test
    fun aSheetRowFindsTheSessionsItsNumberIsReadFrom() {
        fun one(day: Int, line: String, count: Int) = Imported(Logged(day(day), known(line), Load(40, count), 8..8, listOf(8)), "2G", line)
        val classes = Past.classes(emptyList(), listOf(one(10, "8 x dumbbell deadlift", 1), one(12, "8 total x alt dumbbell deadlift", 1), one(16, "8 total x alt dumbbell deadlift", 2)))
        val row = known("8 x deadlift")
        val all = classes.flatMap { c -> c.rows.mapNotNull { it.logged } }
        assertEquals(Progression.own(row, all).map { it.date }.reversed(), Past.lift(row.key, classes, listOf(row)).map { it.date })
        assertEquals(listOf(day(16), day(10)), Past.lift(row.key, classes, listOf(row)).map { it.date })
        assertEquals("deadlift", Past.name(row.key, classes, listOf(row)))
        assertTrue(Past.lift(known("8 x squat").key, classes, listOf(known("8 x squat"))).isEmpty())
    }

    // A day the log has only an unreadable row for is still the log's day: the sheet leaves
    // the file out for it, so looking back does too.
    @Test
    fun aDayTheLogHasIsNotShownFromTheFile() {
        val classes = Past.classes(own, imported, ownDays = setOf(day(16), day(21), day(12)))
        assertEquals(listOf("2026-11-21 2G", "2026-11-21 3G", "2026-11-16 2G", "2026-11-10 2G"), classes.map { it.id })
    }

    // An imported class shows every row the file has for it, in the file's order: the coach
    // leaves a push-up or a skipped row out of its history, but they were part of the class.
    // None of them is a session of any lift.
    @Test
    fun anImportedClassShowsTheRowsTheCoachLeavesOut() {
        val file = listOf(
            """{"date":"2026-11-10","class":"2G","line":"8 x tricep push-up","done":true,"reps":8}""",
            """{"date":"2026-11-10","class":"2G","line":"8 x deadlift","weight":40,"reps":8,"prescribed_reps":8,"variant":{"implement":"double_dumbbell"}}""",
            """{"date":"2026-11-10","class":"2G","line":"8 x squat","skipped":true}""",
            """{"date":"2026-11-10","class":"2G","line":"8 x kettlebell swing","weight":55,"reps":8,"hurt":true}""",
            """{"date":"2026-11-10","class":"2G","line":"8 x zorbed press","weight":20,"reps":8}""",
        ).joinToString("\n")
        val read = HistoryImport.read(file, identity, day(26), everyRow = false)
        assertEquals(1, read.rows.size)
        assertEquals(4, read.others.size)
        val cls = Past.classes(emptyList(), read.rows, others = read.others, identity = identity).single()
        // A row Spotter can't name keeps its own words, without the count.
        assertEquals(listOf("tricep push-up", "deadlift", "squat", "kettlebell swing", "zorbed press"), cls.rows.map { it.name })
        assertEquals(listOf("done", "8 reps", "skipped", "twinge · not a dumbbell", "not read by Spotter"), cls.rows.map { it.detail })
        assertEquals(listOf(null, 40, null, null, null), cls.rows.map { it.logged?.load?.lb })
        assertEquals(listOf(null, null, null, "55 lb", "20 lb"), cls.rows.map { it.apart?.held })
        assertEquals(listOf(day(10)), Past.lift(known("8 x deadlift").key, listOf(cls)).map { it.date })
        assertTrue(Past.lift(known("8 x squat").key, listOf(cls)).isEmpty())
        // A day logged on the phone hides the file's rows for it, these too.
        assertTrue(Past.classes(emptyList(), read.rows, ownDays = setOf(day(10)), others = read.others, identity = identity).isEmpty())
    }

    // What the file says of a left-out row is shown only as far as it can be trusted: a skipped
    // row held nothing, a weight is a plain number of pounds, a name is one short line, and a
    // day of nothing but skipped rows is no class.
    @Test
    fun aLeftOutRowIsShownPlainly() {
        fun one(json: String) = HistoryImport.read(json, identity, day(26), everyRow = false).let { Past.classes(emptyList(), it.rows, others = it.others, identity = identity) }
        fun held(weight: String) = one("""{"date":"2026-11-10","class":"2G","line":"8 x zorbed press","weight":$weight,"reps":8}""").single().rows.single().apart?.held
        assertEquals("22.5 lb", held("22.5"))
        assertEquals("22.5 lb", held("22.499999999"))
        assertEquals("60 lb", held("60.0"))
        assertEquals(null, held("1e12"))
        assertEquals(null, held("0.0001"))
        assertTrue(one("""{"date":"2026-11-10","class":"2G","exercise":"squat","skipped":true,"weight":40,"reps":8}""").isEmpty())
        val mixed = one(
            """{"date":"2026-11-10","class":" 2G ","exercise":"squat","skipped":true,"weight":40,"reps":8}""" + "\n" +
                """{"date":"2026-11-10","class":"2G","line":"8 x zorbed\u202E press\nagain ${"x".repeat(900)}","done":true}""" + "\n" +
                """{"date":"2026-11-10","class":"","line":"8 x tricep push-up","done":true}""",
        )
        assertEquals(listOf("2026-11-10 2G", "2026-11-10 class"), mixed.map { it.id })
        // An emoji at the cut isn't halved; a class name is cleaned as a row's is; an old row
        // with an empty line goes by the name it was filed under.
        val odd = one(
            """{"date":"2026-11-10","class":"2\u202EG\n","line":"8 x ${"y".repeat(79)}\uD83D\uDCAA tail","done":true}""" + "\n" +
                """{"date":"2026-11-10","class":"2 G","line":"","exercise":"squat","done":true}""",
        ).single()
        assertEquals("2 G", odd.className)
        assertEquals(listOf("y".repeat(79), "squat"), odd.rows.map { it.name })
        val rows = mixed.first().rows
        assertEquals(null, rows.first().apart?.held)
        assertTrue(rows.last().name, rows.last().name.length <= 80 && rows.last().name.none { it.isISOControl() || it == '\u202E' })
    }

    // A class logged on the phone shows the rows ticked with nothing held among the weighted
    // ones, in the order they were logged: a dash, the reps, what was said. They open no lift.
    @Test
    fun aRowTickedWithNothingHeldIsPartOfItsClass() {
        val all = listOf(
            entry(21, "2G", "8 x deadlift", 45, listOf(8)),
            entry(21, "2G", "8 x tricep push-up", 10, listOf(8), count = null).copy(lb = 0, hard = true),
            entry(21, "2G", "8 x chest press", 25, listOf(8)),
        )
        val cls = Past.classes(Journal.read(all, vocabulary), emptyList(), bare = Journal.bare(all, vocabulary)).single()
        assertEquals(listOf("deadlift", "tricep push-up", "chest press"), cls.rows.map { it.name })
        assertEquals(listOf("8 reps", "8 reps · felt hard · done", "8 reps"), cls.rows.map { it.detail })
        assertEquals(listOf(45, null, 25), cls.rows.map { it.logged?.load?.lb })
        assertEquals(null, cls.rows[1].apart?.held)
        assertTrue(Past.lift(known("8 x tricep push-up").key, listOf(cls)).isEmpty())
    }
}
