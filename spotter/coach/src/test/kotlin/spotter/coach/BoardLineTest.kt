package spotter.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import spotter.coach.Scheme.Ladder
import spotter.coach.Scheme.Reps
import spotter.coach.Scheme.Timed

// Lines are from public r/orangetheory boards (carry/brain-fixtures/boards.jsonl), as posted.
class BoardLineTest {
    private fun read(line: String) = BoardLine.read(line)

    @Test
    fun aPlainCountARangeAndASideCount() {
        assertEquals(Reps(12, 12, Count.PLAIN), read("12 x squat").scheme)
        assertEquals(Reps(6, 10, Count.EACH), read("6 - 10 each x bench bird dog low row, rest").scheme)
        assertEquals(Reps(6, 10, Count.PLAIN), read("6-10 x high row, rest").scheme)
        assertEquals(Reps(5, 5, Count.EACH), read("5 each arm cross body YB shoulder press").scheme)
        assertEquals(Reps(4, 4, Count.EACH), read("4 each side: snatch").scheme)
        assertEquals(Reps(8, 8, Count.PLAIN), read("8 lateral lunge, rest").scheme)
    }

    // Trap 7: "12 total" is 6 a side, and "6 each" is 6 a side.
    @Test
    fun totalAndEachComeToTheSameRepsASide() {
        val total = read("12 total x goblet alt forward lunge with torso rotation").scheme as Reps
        val each = read("6 each x split stance reverse fly").scheme as Reps
        assertEquals(Count.TOTAL, total.count)
        assertEquals(6..6, total.perSide())
        assertEquals(6..6, each.perSide())
        assertEquals(3..5, (read("6 - 10 total x high plank alt dumbbell tap, rest").scheme as Reps).perSide())
    }

    // Trap 8: a ladder's weight is for the round picked up first.
    @Test
    fun aLadderStartsAtItsFirstRound() {
        val down = read("10 / 8 / 6 x high pull").scheme as Ladder
        assertEquals(listOf(10, 8, 6), down.rounds)
        assertEquals(10, down.firstRound)
        assertEquals(Count.TOTAL, (read("10 / 8 / 6 total x alt lateral lunge").scheme as Ladder).count)
        assertEquals(10, (read("10/12/14 squats with overhead band").scheme as Ladder).firstRound)
    }

    @Test
    fun timedLinesAreSecondsNotReps() {
        assertEquals(Timed(20), read("~20 sec of pulsing half squat, rest").scheme)
        assertEquals(Timed(60), read("1 min of clean to neutral grip push press, count &amp; remember reps").scheme)
        assertEquals(Timed(10), read("\\~10 sec close grip chest press hold").scheme)
        assertEquals(Scheme.AsManyAsPossible, read("incline close grip chest press (lighter) AMRepsAP,rest").scheme)
    }

    // A number that belongs to the exercise is not a rep count.
    @Test
    fun aNumberInTheExerciseStaysInIt() {
        val bridge = read("12 x 2 dumbbell bench bridge")
        assertEquals(Reps(12, 12, Count.PLAIN), bridge.scheme)
        assertEquals("2 dumbbell bench bridge", bridge.words)
        assertNull(read("alt 90 degree pop squat").scheme)
        assertNull(read("2 dumbbell bench bridge hold").scheme)
        assertNull(read("90 degree pop squat").scheme)
        assertEquals("alt 90 degree pop squat", read("alt 90 degree pop squat").words)
    }

    @Test
    fun theWordsLeftAreTheExerciseAndNothingElse() {
        val line = read("**Finisher**: 30 sec of bosu high plank alt knee drive (feet on bosu)")
        assertEquals("finisher", line.label)
        assertEquals(Timed(30), line.scheme)
        assertEquals("bosu high plank alt knee drive feet on bosu", line.words)
        assertEquals("bodyweight double crunch", read("Bonus: bodyweight double crunch (rest as needed) until time is called").words)
        assertEquals("finisher", read("Repeat until time for **finisher**: 30 sec of squat to press").label)
    }

    @Test
    fun aSideAndARestAreTakenOff() {
        val right = read("10 x single arm single leg lateral step-up with shift (R)")
        assertEquals("R", right.side)
        assertEquals("single arm single leg lateral step-up with shift", right.words)
        assertEquals("L", read("goblet split squat RFE (lighter) L").side)
        assertEquals("L", read("8 single dumbbell split squats on left").side)
        assertTrue(read("6-10 x high row, rest").rest)
        assertEquals("reverse lunges", read("**Reverse lunges** - work &amp; rest").words)
    }

    // Trap 5: a greeting or an instruction is not an exercise, even when it holds one's name.
    @Test
    fun proseAndHeadingsAreNotExercises() {
        assertEquals(LineKind.INSTRUCTION, read("Good morning, all!").kind)
        assertEquals(LineKind.EXERCISE, read("8 x good morning").kind)
        assertEquals(LineKind.INSTRUCTION, read("Great class today!").kind)
        assertEquals(LineKind.INSTRUCTION, read("Anyone else find the floor brutal?").kind)
        assertEquals(LineKind.INSTRUCTION, read("Goal: match or beat your reps in the push-ups from block 1").kind)
        assertEquals(LineKind.INSTRUCTION, read("Repeat until time is called, increase reps of the reverse lunge by 2 each round").kind)
        assertEquals(LineKind.HEADING, read("Push Press Challenge:").kind)
        assertEquals(LineKind.HEADING, read("Floor Block 2 - ultimate burpee construct**").kind)
    }

    @Test
    fun boardTextsInvisibleCharactersDontHideTheScheme() {
        val line = read("⁠1 min of alt lateral shift to windmill")
        assertEquals(Timed(60), line.scheme)
        assertEquals("alt lateral shift to windmill", line.words)
        assertEquals(Reps(6, 10, Count.PLAIN), read("6 – 10 x high row").scheme)
    }

    // From the review, on corpus lines unless marked otherwise.

    @Test
    fun aTrailingTotalCountsLikeALeadingOne() {
        val line = read("8 x alternating halos and reverse lunges with y-bell (total)")
        assertEquals(Reps(8, 8, Count.TOTAL), line.scheme)
        assertEquals(4..4, (line.scheme as Reps).perSide())
        assertEquals("alternating halos and reverse lunges with y-bell", line.words)
    }

    // A sentence to the reader is commentary even when it starts with a number (trap 5).
    @Test
    fun aRecapThatStartsWithANumberIsNotAnExercise() {
        assertEquals(LineKind.INSTRUCTION, read("2 blocks on the floor of work and rest style sets. They call it rotate and resist so you are twisting a lot").kind)
        assertEquals(LineKind.INSTRUCTION, read("4 x 3 minute pushes on the treadmill and 3 x 2 minute pushes on the rower. Our coach was telling us to push").kind)
        assertEquals(LineKind.INSTRUCTION, read("Your choice of squat or lunge").kind)
        // A full stop inside a workout line doesn't make it commentary.
        assertEquals(Timed(30), read("30 sec of clean to front squat. count &amp; check reps").scheme)
        assertEquals(LineKind.EXERCISE, read("12x Y-bell knee lift with alt. torso rotation").kind)
    }

    @Test
    fun aTimeToDoRepsKeepsTheReps() {
        val line = read("45 sec to do 8 x split squat (L) (explosive)")
        assertEquals(Reps(8, 8, Count.PLAIN), line.scheme)
        assertEquals("L", line.side)
        assertEquals("split squat explosive", line.words)
    }

    // "row" is a movement's name; a rower or tread line must not become a dumbbell row.
    @Test
    fun treadAndRowerLinesAreNotLifts() {
        assertEquals(LineKind.INSTRUCTION, read("600 m row").kind)
        assertEquals(LineKind.INSTRUCTION, read("1 min All Out row").kind)
        assertEquals(LineKind.INSTRUCTION, read("1 min base @ 6% (PW 7.0)").kind)
        assertEquals(LineKind.EXERCISE, read("12 x TRX row").kind)
    }

    @Test
    fun bulletsAndIndentsComeOff() {
        assertEquals(Reps(10, 10, Count.PLAIN), read("    * 10 x single arm chest press (L)").scheme)
        assertEquals(LineKind.INSTRUCTION, read("* Goal: accumulate distance in the buy-in").kind)
        assertEquals(LineKind.INSTRUCTION, read("Bonus: complete exercises at 6 reps until").kind)
    }

    @Test
    fun everySpellingOfASideIsTakenOff() {
        val restTimed = read("8 x RFE split squat (R), ~10 sec rest")
        assertEquals("R", restTimed.side)
        assertTrue(restTimed.rest)
        assertEquals("rfe split squat", restTimed.words)
        assertEquals("L", read("6 x single arm split squat (slow) - left").side)
        assertEquals("R", read("1 min dumbbell overhead single arm march in place (right)").side)
        assertEquals("full lateral step-up heavy", read("4 x full lateral step-up (heavy) - L").words)
        assertEquals("high plank alt low row", read("8 total x high plank alt low row, rest,").words)
        assertEquals("high plank pull through", read("12 total x high plank pull through, rest **OR**").words)
    }

    // Not seen on a board yet: numbers that belong to the exercise, and clock times.
    @Test
    fun numbersThatAreNotACount() {
        assertNull(read("1/2 kneeling shoulder press").scheme)
        assertNull(read("4 point single arm low row").scheme)
        assertEquals(Timed(60), read("1:00 plank").scheme)
    }

    // Board text is from the internet: a hostile or broken line must not crash or stall.
    @Test
    fun hostileLinesAreRefusedQuickly() {
        val started = System.nanoTime()
        assertEquals(LineKind.INSTRUCTION, read("1/".repeat(1000) + " x squat").kind)
        assertEquals(LineKind.INSTRUCTION, read("a" + " ,".repeat(50_000) + "x").kind)
        assertTrue((System.nanoTime() - started) < 2_000_000_000L)
        assertNull(read("2147483648 x squat").scheme)
        assertNull(read(" ,".repeat(140) + " rest").scheme)
    }

    // A buy-out is a label like a buy-in, and a round's "recover when done" is not the exercise.
    @Test
    fun aBuyOutAndARecoveryComeOff() {
        val out = read("Buy-out: 1 min of low plank")
        assertEquals("buy-out", out.label)
        assertEquals(Timed(60), out.scheme)
        val round = read("20 sec to complete 8 x bench tap squat, recover when done until next round begins")
        assertEquals(Reps(8, 8, Count.PLAIN), round.scheme)
        assertEquals("bench tap squat", round.words)
        assertNull(read("buy-outs: 10 x squat").label)
    }

    // From a real board through the sheet: the post's title, the time between blocks and a
    // lone backslash are not exercises.
    @Test
    fun titlesRecoveriesAndBlankLinesAreNotExercises() {
        assertEquals(LineKind.HEADING, read("# **2G intel**").kind)
        assertEquals(LineKind.INSTRUCTION, read("**90 sec recovery**").kind)
        assertEquals(LineKind.INSTRUCTION, read("* 1 min to transition to the rower").kind)
        assertEquals(LineKind.INSTRUCTION, read("**90 sec WR, get to base when ready**").kind)
        assertEquals(LineKind.INSTRUCTION, read("\\").kind)
        assertEquals(LineKind.INSTRUCTION, read("* 45 sec base").kind)
        assertEquals(LineKind.INSTRUCTION, read("* 30 sec rest").kind)
        assertEquals(LineKind.INSTRUCTION, read("* row (20 - 24 strokes / minute)").kind)
        assertEquals(LineKind.INSTRUCTION, read("* Check &amp; remember distance").kind)
        assertEquals(LineKind.INSTRUCTION, read("Member choice - lift more OR row more:").kind)
        assertEquals(LineKind.HEADING, read("**Row Block - 2.5 minutes**").kind)
        assertEquals(LineKind.INSTRUCTION, read("* WR until ready to begin next round").kind)
        assertEquals(LineKind.INSTRUCTION, read("Nice template - endurance and power on the treads and the opportunity to lifting or rowing on the floor.").kind)
        // Only a whole line of recovery: an exercise done as the recovery is still one.
        assertEquals(LineKind.EXERCISE, read("30 sec of active recovery: bird dog").kind)
        assertEquals(LineKind.EXERCISE, read("8 x reverse lunge (transition)").kind)
    }

    // From a real week of boards and the corpus: what a tread or rower block says to do isn't
    // an exercise row. Before, each was a "not sure" row on the sheet, and a bare "row" after
    // a time was read as the dumbbell row.
    @Test
    fun treadAndRowerInstructionsAreNotRows() {
        val instructions = listOf(
            "* Clear screen", "* Set monitor to 14 minute count down", "* Collapse (member's choice)", "* Remember incline",
            "* Begin at last exercise completed", "* Count &amp; remember rounds", "* When done - WR until next round begins",
            "* Red goal: complete 2 rounds", "* Green goal - complete 30 reps", "Reposting content courtesy of /u/someone.",
            "* 1 min push row", "* 30 sec recovery row", "* 45 sec base row", "* 1 min row (1:3 tempo)", "1 min row", "* 2.5 min base",
            "* 30 sec speed surge", "* 90 sec incline surge", "* 2 min push", "* 2 min push (PW @ 5+)", "* 30 sec transition",
            "1 min walking recovery", "* Bonus: row until time is called", "* Row for time until finisher",
        )
        instructions.forEach { assertEquals(it, LineKind.INSTRUCTION, read(it).kind) }
        // What those rules mustn't take: a push-up, a push press, a named dumbbell row, a
        // counted row, and a surge of nothing.
        val exercises = listOf(
            "* 30 sec push-up", "* 1 min push press", "* 30 sec of low row", "* 1 min of single arm low row", "* 8 x row",
            "* 30 sec of bicep curl, rest", "* 8 x clear the bar press", "* 12 x remembered squat",
        )
        exercises.forEach { assertEquals(it, LineKind.EXERCISE, read(it).kind) }
        // A block shared by two stations is a heading like any other, and "When done:" still
        // names what follows it.
        listOf("**Floor / Row Block 1 - 3 minutes**", "**Row / Tread Block 2 - 6.5 minutes**", "* When done:").forEach {
            assertEquals(it, LineKind.HEADING, read(it).kind)
        }
    }
}
