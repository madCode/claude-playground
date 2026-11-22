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
}
