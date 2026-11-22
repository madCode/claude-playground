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
}
