package spotter.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// An invented lifter's log. Lines are public board lines, read the way the app reads them.
class ProgressionTest {
    private val identity = Identity(Vocabulary.starting())
    private fun exercise(line: String) = identity.read(BoardLine.read(line).words) as Reading.Known

    private val press = exercise("8 x chest press")
    private fun day(n: Int) = LocalDate.of(2026, 3, n)
    private fun logged(
        day: Int, lb: Int, reps: List<Int>, target: IntRange? = 8..8, ex: Reading.Known = press,
        count: Int? = 2, hurt: Boolean = false, stopped: Boolean = false, ladder: List<Int>? = null, easy: Boolean = false,
    ) = Logged(day(day), ex, Load(lb, count), target, reps, hurt = hurt, stopped = stopped, ladder = ladder, easy = easy)

    private fun suggest(log: List<Logged>, ex: Reading.Known = press, target: IntRange? = 8..8) =
        Progression.suggest(ex, target, log)

    private fun weight(log: List<Logged>, ex: Reading.Known = press, target: IntRange? = 8..8) =
        suggest(log, ex, target) as Suggestion.Weight

    // madCode, 2026-11-22: one clean session holds; a second in a row at the weight goes up.
    @Test
    fun twoCleanSessionsInARowGoUpADumbbell() {
        val clean = listOf(8, 8, 8)
        val once = weight(listOf(logged(1, 25, clean)))
        assertEquals(listOf(Load(25, 2), Change.SAME), listOf(once.load, once.change))
        assertTrue(once.reasons.single().contains("up after one more clean session"))
        val twice = weight(listOf(logged(1, 25, clean), logged(3, 25, clean)))
        assertEquals(listOf(Load(30, 2), Change.UP), listOf(twice.load, twice.change))
        assertTrue(twice.reasons.single().contains("up a dumbbell"))
        // A near miss between two clean sessions breaks the run.
        assertEquals(Load(25, 2), weight(listOf(logged(1, 25, clean), logged(3, 25, listOf(8, 7, 6)), logged(5, 25, clean))).load)
        // Another rep count in between neither breaks the run nor counts towards it.
        val tens = logged(3, 20, listOf(10), target = 10..10)
        assertEquals(Load(30, 2), weight(listOf(logged(1, 25, clean), tens, logged(5, 25, clean))).load)
        assertEquals(Load(25, 2), weight(listOf(tens, logged(5, 25, clean))).load)
    }

    // What counts as two sessions in a row, from the cases a review ran through the rule.
    @Test
    fun whatSettlesAWeightAndWhatDoesnt() {
        val clean = listOf(8, 8, 8)
        // The same exercise twice in one class is one session.
        assertEquals(Load(25, 2), weight(listOf(logged(1, 25, clean), logged(1, 25, clean))).load)
        // A twinge between two clean sessions breaks the run, and so does a miss at other reps.
        assertEquals(Load(25, 2), weight(listOf(logged(1, 25, clean), logged(3, 25, clean, hurt = true), logged(5, 25, clean))).load)
        assertEquals(Load(25, 2), weight(listOf(logged(1, 25, clean), logged(3, 20, listOf(5), target = 12..12), logged(5, 25, clean))).load)
        // One dumbbell and two aren't the same weight twice.
        assertEquals(25, weight(listOf(logged(1, 25, clean, count = 1), logged(3, 25, clean))).load.lb)
        // Back at a weight done clean before, after a lighter day: the lighter day isn't new.
        assertEquals(Load(30, 2), weight(listOf(logged(1, 30, clean), logged(3, 25, clean))).load)
        // The promise of a step is made only where one would follow: not at the top of the rack,
        // not before a big jump, not when the reps differ.
        fun why(log: List<Logged>, target: IntRange = 8..8) = weight(log, target = target).reasons.first()
        assertEquals("clean at 2×25 once: up after one more clean session, or one that felt easy", why(listOf(logged(1, 25, clean))))
        assertEquals("clean at 2×50 once: same again", why(listOf(logged(1, 50, clean))))
        assertEquals("clean at 2×12 once: same again", why(listOf(logged(1, 12, clean))))
        assertEquals("clean at 2×25 once: same again", why(listOf(logged(1, 25, clean)), target = 12..12))
        // Nor after a twinge since: the next clean session won't be the second in a row.
        assertEquals("clean at 2×25 once: same again", why(listOf(logged(1, 25, clean), logged(3, 25, clean, hurt = true))))
    }

    // One clean session that felt easy is enough.
    @Test
    fun feltEasyGoesUpAfterOne() {
        val s = weight(listOf(logged(1, 25, listOf(8, 8, 8), easy = true)))
        assertEquals(listOf(Load(30, 2), Change.UP), listOf(s.load, s.change))
        // Easy, but short: the reps decide.
        assertEquals(Load(25, 2), weight(listOf(logged(1, 25, listOf(8, 7, 7), easy = true))).load)
    }

    // A weight lifted above the sheet's number becomes the number, and waits its own two clean
    // sessions before the next step: any settled weight does.
    @Test
    fun aWeightLiftedAboveTheSheetIsTheNewNumber() {
        val clean = listOf(8, 8, 8)
        val log = mutableListOf(logged(1, 25, clean), logged(3, 25, clean), logged(5, 35, clean))
        assertEquals(listOf(Load(35, 2), Change.SAME), weight(log).let { listOf(it.load, it.change) })
        log += logged(7, 35, clean)
        assertEquals(Load(40, 2), weight(log).load)
        // After the step the count starts again: one clean 40 holds.
        log += logged(9, 40, clean)
        assertEquals(Load(40, 2), weight(log).load)
    }

    @Test
    fun aNearMissHoldsAndAMissStepsDown() {
        assertEquals(Load(25, 2), weight(listOf(logged(1, 25, listOf(8, 7, 6)))).load)
        // A miss on the first set, or of three reps, or stopping, is a miss.
        assertEquals(Load(20, 2), weight(listOf(logged(1, 25, listOf(7, 8, 8)))).load)
        assertEquals(Load(20, 2), weight(listOf(logged(1, 25, listOf(8, 8, 5)))).load)
        assertEquals(Load(20, 2), weight(listOf(logged(1, 25, listOf(8), stopped = true))).load)
        assertEquals(Change.DOWN, weight(listOf(logged(1, 25, listOf(7, 8, 8)))).change)
    }

    // One set logged (the Log screen's heaviest) has no first set to miss: one or two short holds,
    // three short steps down.
    @Test
    fun oneLoggedSetHoldsUnlessThreeShort() {
        assertEquals(Load(25, 2), weight(listOf(logged(1, 25, listOf(7)))).load)
        assertEquals(Load(25, 2), weight(listOf(logged(1, 25, listOf(6)))).load)
        assertEquals(Load(20, 2), weight(listOf(logged(1, 25, listOf(5)))).load)
        // After an easy 30, a 35 one short with a twinge caps at 35 rather than a step under it.
        assertEquals(Load(35, 2), weight(listOf(logged(1, 30, listOf(8), easy = true), logged(2, 35, listOf(7), hurt = true))).load)
    }

    // A range goes up only once every set reaches its top.
    @Test
    fun aRangeHoldsUntilTheTop() {
        val inRange = listOf(logged(1, 25, listOf(9, 8, 8), target = 6..10))
        assertEquals(Load(25, 2), weight(inRange, target = 6..10).load)
        val atTop = listOf(logged(1, 25, listOf(10, 10, 10), target = 6..10, easy = true))
        assertEquals(Load(30, 2), weight(atTop, target = 6..10).load)
    }

    // Trap 3: a set that hurt never raises a number, as a source or a floor.
    @Test
    fun aSetThatHurtNeverRaisesANumber() {
        // madCode: a twinge holds the weight and notes it, even as the only thing logged.
        val onlyHurt = weight(listOf(logged(1, 35, listOf(8, 8, 8), hurt = true)))
        assertEquals(Load(35, 2), onlyHurt.load)
        assertEquals("it twinged last time at 2×35", onlyHurt.caution)
        // Short by three as well: a step under.
        assertEquals(Load(30, 2), weight(listOf(logged(1, 35, listOf(5), hurt = true))).load)
        // Clean at 25, then it hurt at 25: hold, not up to 30.
        val capped = weight(listOf(logged(1, 25, listOf(8, 8, 8)), logged(3, 25, listOf(8, 8, 8), hurt = true)))
        assertEquals(Load(25, 2), capped.load)
        // A clean 25 doesn't step up past a 20 that hurt since.
        val lower = weight(listOf(logged(1, 25, listOf(8, 8, 8)), logged(3, 20, listOf(8, 8, 8), hurt = true)))
        assertEquals(Load(20, 2), lower.load)
        // The latest twinge caps, not the lowest: 30 twinged, then 35 done in full with a twinge.
        val latest = weight(listOf(logged(1, 35, listOf(8)), logged(2, 30, listOf(8), hurt = true), logged(3, 35, listOf(8), hurt = true)))
        assertEquals(Load(35, 2), latest.load)
        // An easy 30, then a twinge at 35 done in full: 35 again, and not as a step up.
        val held = weight(listOf(logged(1, 30, listOf(8), easy = true), logged(2, 35, listOf(8), hurt = true)))
        assertEquals(Load(35, 2), held.load)
        assertEquals(Change.SAME, held.change)
        // A weight above the sheet's that twinged isn't taken up: one clean 30 holds at 30.
        val notTaken = weight(listOf(logged(1, 30, listOf(8)), logged(2, 35, listOf(8), hurt = true)))
        assertEquals(listOf(Load(30, 2), Change.DOWN, "it twinged last time at 2×35"), listOf(notTaken.load, notTaken.change, notTaken.caution))
        // Said even when the cap doesn't bite: an easy 30, then a twinge at 40.
        val above = weight(listOf(logged(1, 30, listOf(8), easy = true), logged(2, 40, listOf(8), hurt = true)))
        assertEquals(Load(35, 2), above.load)
        assertTrue(above.reasons.any { it.contains("twinged at 2×40") })
        assertEquals("it twinged last time at 2×40", above.caution)
    }

    // Done, but it felt hard: weak evidence against a step, so it waits a session.
    @Test
    fun feltHardDelaysAStep() {
        val hard = Logged(day(1), press, Load(30, 2), 8..8, listOf(8), hard = true)
        assertEquals(Load(30, 2), weight(listOf(hard)).load)
        assertEquals(Load(35, 2), weight(listOf(hard, logged(3, 30, listOf(8)))).load)
    }

    // A ladder logged with one weight, its heaviest round's, never raises a number: the round it
    // was held for holds, and round one's 8 gets what the 4 converts to, rounded down.
    @Test
    fun aLadderLoggedWithOneWeightHolds() {
        val ladder = Logged(day(1), press, Load(30, 2), null, listOf(8, 6, 4), ladder = listOf(8, 6, 4), heaviestOnly = true)
        assertEquals(Load(30, 2), weight(listOf(ladder), target = 4..4).load)
        val s = weight(listOf(ladder), target = 8..8)
        assertEquals(Load(25, 2), s.load)
        assertEquals(Change.SAME, s.change)
        // With each round's weight known, a clean ladder goes up as before.
        assertEquals(Load(35, 2), weight(listOf(ladder.copy(heaviestOnly = false, easy = true)), target = 8..8).load)
    }

    // A board that alternates 8s and 10s, every session clean: each rep count goes up on its own
    // history, and the number never walks down.
    @Test
    fun alternatingRepCountsEachProgress() {
        val log = mutableListOf(logged(1, 30, listOf(8), easy = true))
        // Nothing at 10 yet: converted from the 8s, rounded down.
        assertEquals(Load(25, 2), weight(log, target = 10..10).load)
        log += logged(2, 25, listOf(10), target = 10..10, easy = true)
        // Back to 8: from the clean 30 at 8, up a step.
        assertEquals(Load(35, 2), weight(log).load)
        log += logged(3, 35, listOf(8))
        assertEquals(Load(30, 2), weight(log, target = 10..10).load)
        // A near miss since, at the other reps, stops the step up but not the number.
        log += logged(4, 30, listOf(8), target = 10..10)
        assertEquals(Load(35, 2), weight(log).load)
        // A miss since puts the latest session back in charge (trap 4): under the 30 missed.
        log += logged(5, 30, listOf(5), target = 10..10)
        assertTrue(weight(log).load.lb < 30)
    }

    // A number from long ago doesn't come back: 8s clean at 40, then the 10s settled at 25.
    @Test
    fun aStaleSessionAtTheseRepsIsNotTheBasis() {
        val log = listOf(logged(1, 40, listOf(8)), logged(5, 25, listOf(10), target = 10..10), logged(9, 25, listOf(10), target = 10..10))
        assertEquals(Load(25, 2), weight(log).load)
    }

    // What twinged at 8 reps is too much for 12: the cap converts down.
    @Test
    fun aTwingeAtFewerRepsCapsLower() {
        val log = listOf(logged(1, 25, listOf(12), target = 12..12), logged(2, 30, listOf(8), hurt = true))
        val s = weight(log, target = 12..12)
        assertEquals(Load(25, 2), s.load)
        assertEquals("it twinged last time at 2×30", s.caution)
    }

    // A sound session after a twinge clears it, whichever reps it was at.
    @Test
    fun aSoundSessionClearsATwinge() {
        val log = listOf(
            logged(1, 35, listOf(8), easy = true),
            logged(2, 20, listOf(10), target = 10..10, hurt = true),
            logged(3, 30, listOf(10), target = 10..10),
        )
        val s = weight(log)
        assertEquals(Load(40, 2), s.load)
        assertNull(s.caution)
    }

    // The arrow is against the session the number rests on, so it agrees with the reason.
    @Test
    fun theArrowAgreesWithTheReason() {
        // 8s near-missed at 40, 10s at 35: asking 8 holds 40, and says so.
        val log = listOf(logged(1, 40, listOf(8, 7, 7)), logged(2, 35, listOf(10), target = 10..10))
        val s = weight(log)
        assertEquals(Load(40, 2), s.load)
        assertEquals(Change.SAME, s.change)
    }

    // A ladder's one weight was its heaviest round's: other reps convert from that round's.
    @Test
    fun aLaddersOneWeightConvertsFromItsHeaviestRound() {
        val ladder = Logged(day(1), press, Load(30, 2), null, listOf(12, 10, 8), ladder = listOf(12, 10, 8), heaviestOnly = true)
        // From the 8 it was held for, not the 12 of round one: 30, not 35.
        assertEquals(Load(30, 2), weight(listOf(ladder), target = 6..6).load)
    }

    // More reps than asked convert from what was asked, rather than leaving no number.
    @Test
    fun extraRepsConvertFromTheTarget() {
        val extra = logged(1, 20, listOf(15), target = 12..12)
        assertTrue(suggest(listOf(extra), target = 8..8) is Suggestion.Weight)
    }

    // A two-legged lift that twinged lends nothing to a first go on one leg.
    @Test
    fun aTwingeIsNotBorrowedFrom() {
        val squat = exercise("8 x squat")
        val log = listOf(Logged(day(1), squat, Load(35, 2), 8..8, listOf(8), hurt = true))
        assertEquals(Suggestion.NoNumber.NEW, suggest(log, exercise("8 x single leg squat")))
    }

    // A conversion starts from the reps done, and never rides on a step up.
    @Test
    fun aConversionStartsFromWhatWasDone() {
        // A range ticked at its low end: 8 done at 30, and 6 asked now. From 8, not 12.
        val ticked = logged(1, 30, listOf(8), target = 8..12)
        assertEquals(Load(30, 2), weight(listOf(ticked), target = 6..6).load)
        // Clean at 12, 6 asked: from the 30 held, not from a step up to 35.
        val clean = logged(1, 30, listOf(12), target = 12..12)
        val s = weight(listOf(clean), target = 6..6)
        assertEquals(Load(35, 2), s.load)
        assertEquals(Change.SAME, s.change)
    }

    // Trap 4: clean at 40 on Tuesday, 3 of 8 at 35 on Thursday: under 35, not 40.
    @Test
    fun aLaterMissCapsAnOlderSuccess() {
        val s = weight(listOf(logged(3, 40, listOf(8, 8, 8)), logged(5, 35, listOf(3))))
        assertTrue(s.load.lb < 35)
    }

    @Test
    fun aBigJumpHoldsAndOffersATest() {
        val s = weight(listOf(logged(1, 12, listOf(8, 8, 8)), logged(3, 12, listOf(8, 8, 8))))
        assertEquals(Load(12, 2), s.load)
        assertEquals(Load(15, 2), s.test)
        // Not offered on one clean session: the test is for a weight that has settled.
        assertNull(weight(listOf(logged(1, 12, listOf(8, 8, 8)))).test)
        assertEquals(Change.SAME, s.change)
    }

    // Epley converts between rep counts only within 3 to 12.
    @Test
    fun differentRepsConvertOnlyWhereEpleyHolds() {
        val log = listOf(logged(1, 25, listOf(8, 8, 8, 7), target = 8..8))
        // A near miss holds 25 for 8; 12 reps is lighter, on the rack.
        assertEquals(Load(20, 2), weight(log, target = 12..12).load)
        assertTrue(suggest(log, target = 20..20) is Suggestion.NoNumber)
    }

    @Test
    fun aTimedSetKeepsLastTimesWeight() {
        // 25 to 30 isn't a big jump, so only the timed rule holds it.
        val log = listOf(logged(1, 25, emptyList(), target = null))
        assertEquals(Load(25, 2), weight(log, target = null).load)
    }

    @Test
    fun theCountIsKept() {
        val goblet = exercise("8 x goblet squat")
        val s = weight(listOf(logged(1, 40, listOf(8, 8), ex = goblet, count = 1, easy = true)), ex = goblet)
        assertEquals(Load(45, 1), s.load)
    }

    // Trap 10: a one-legged version starts from the two-legged lift, at about 0.6.
    @Test
    fun oneLegBorrowsFromTwoAtAFactor() {
        val deadlift = exercise("8 x deadlift")
        val single = exercise("8 x single leg deadlift")
        val s = weight(listOf(logged(1, 40, listOf(8, 8, 8), ex = deadlift, easy = true)), ex = single)
        // An easy 40 suggests 45 two-legged; 0.6 of 45 is 27, so 25 on the rack.
        assertEquals(Load(25, 2), s.load)
        assertEquals(Change.EST, s.change)
        assertTrue(s.reasons.single().contains("0.6"))
    }

    // A heavier weight done clean vouches for a lighter one come back to, for eight weeks
    // (madCode, 2026-11-23): after that the lighter weight earns its step with two of its own.
    @Test
    fun aHeavierWeightVouchesForALighterOneOnlyForEightWeeks() {
        fun on(date: LocalDate, lb: Int) = Logged(date, press, Load(lb, 2), 8..8, listOf(8, 8))
        val back = LocalDate.of(2026, 6, 1)
        // Clean at 30 seven weeks ago, clean at 25 today: back to 30.
        assertEquals(Load(30, 2), weight(listOf(on(back.minusWeeks(7), 30), on(back, 25))).load)
        // Nine weeks ago: 25 holds, and says one more.
        val stale = weight(listOf(on(back.minusWeeks(9), 30), on(back, 25)))
        assertEquals(listOf(Load(25, 2), true), listOf(stale.load, stale.oneMore))
        // Eight weeks to the day still counts; a day more doesn't.
        assertEquals(Load(30, 2), weight(listOf(on(back.minusWeeks(8), 30), on(back, 25))).load)
        assertEquals(Load(25, 2), weight(listOf(on(back.minusWeeks(8).minusDays(1), 30), on(back, 25))).load)
        // Its second clean session goes up, as any weight's does.
        assertEquals(Load(30, 2), weight(listOf(on(back.minusWeeks(9), 30), on(back, 25), on(back.plusDays(3), 25))).load)
        // Two clean sessions at the same weight count however far apart.
        assertEquals(Load(30, 2), weight(listOf(on(back.minusWeeks(20), 25), on(back, 25))).load)
    }

    // A step up earned at other reps isn't taken on a conversion, but the dumbbell it would have
    // meant is offered to try (madCode, 2026-11-23).
    @Test
    fun aStepEarnedAtOtherRepsIsOfferedAsATry() {
        val earned = listOf(logged(1, 25, listOf(8, 8)), logged(2, 25, listOf(8, 8)))
        assertEquals(Load(30, 2), weight(earned).load)
        // Fewer reps: what was held, and the next dumbbell to try.
        val six = weight(earned, target = 6..6)
        assertEquals(listOf(Load(25, 2), Load(30, 2), Change.SAME), listOf(six.load, six.test, six.change))
        assertEquals(listOf("done at 2×25", "a step up was earned at 8 reps: try 30 if set 1 feels easy"), six.reasons)
        // More reps: the conversion comes down, and the try is the dumbbell above it.
        val twelve = weight(earned, target = 12..12)
        assertEquals(listOf(Load(20, 2), Load(25, 2)), listOf(twelve.load, twelve.test))
        // Nothing earned, nothing offered: one clean session, or one that came up short.
        assertNull(weight(earned.take(1), target = 6..6).test)
        assertNull(weight(listOf(logged(1, 25, listOf(8, 6))), target = 6..6).test)
        // A twinge since takes the offer away, and the reasons don't make it.
        val twinged = weight(earned + logged(3, 25, listOf(8, 8), hurt = true), target = 6..6)
        assertNull(twinged.test)
        assertTrue(twinged.reasons.none { "try" in it })
        // A twinge above the offer takes it away too.
        assertNull(weight(earned + logged(3, 35, listOf(8, 8), hurt = true), target = 6..6).test)
        // A step that is a big jump was earned as well: its try is for these reps, not those.
        val big = weight(listOf(logged(1, 20, listOf(8, 8)), logged(2, 20, listOf(8, 8))), target = 6..6)
        assertEquals(listOf(Load(20, 2), Load(25, 2)), listOf(big.load, big.test))
        assertEquals(listOf("done at 2×20", "a step up was earned at 8 reps: try 25 if set 1 feels easy"), big.reasons)
        // Never more than the earned dumbbell converts to: 35 for 5 is no more than 25 for 12.
        val far = weight(listOf(logged(1, 30, listOf(5, 5), 5..5), logged(2, 30, listOf(5, 5), 5..5)), target = 12..12)
        assertEquals(listOf(Load(25, 2), null), listOf(far.load, far.test))
        // To far fewer reps the earned 20 converts to 25: one dumbbell above the number is offered.
        val few = weight(listOf(logged(1, 15, listOf(12, 12), 12..12), logged(2, 15, listOf(12, 12), 12..12)), target = 3..3)
        assertEquals(listOf(Load(15, 2), Load(20, 2)), listOf(few.load, few.test))
        // Taken and done clean, it is these reps' own history from then on.
        assertEquals(Load(30, 2), weight(earned + logged(3, 30, listOf(6, 6), 6..6), target = 6..6).load)
    }

    // Wordings that leave the dumbbells as they are share one history (madCode, 2026-11-23):
    // the number, the two clean sessions and a twinge all carry across.
    @Test
    fun wordingsThatLeaveTheWeightShareAHistory() {
        val curl = exercise("8 x bicep curl")
        val alternating = exercise("8 x alternating bicep curl")
        assertEquals("bicep curl [alternating=yes]", alternating.key)
        assertEquals("bicep curl", alternating.weightKey)
        val once = listOf(logged(1, 25, listOf(8, 8), ex = curl))
        val s = weight(once, ex = alternating)
        assertEquals(listOf(Load(25, 2), Change.SAME), listOf(s.load, s.change))
        assertEquals("counted with its other wordings, which take the same dumbbells", s.reasons.last())
        // The second clean session may be in other words: then the third goes up, whichever it is.
        val twice = once + logged(2, 25, listOf(8, 8), ex = alternating)
        assertEquals(Load(30, 2), weight(twice, ex = exercise("8 x seated bicep curl")).load)
        assertEquals(Load(30, 2), weight(twice, ex = curl).load)
        // Said also when the row's own session is the basis and the other wording made it two.
        assertEquals(listOf("clean at 2×25: up a dumbbell", "counted with its other wordings, which take the same dumbbells"), weight(twice, ex = alternating).reasons)
        assertEquals(listOf("clean at 2×25 once: up after one more clean session, or one that felt easy"), weight(once, ex = curl).reasons)
        // A twinge in one wording holds the others.
        val hurt = twice + logged(3, 30, listOf(8, 8), ex = curl, hurt = true)
        assertEquals("it twinged last time at 2×30", weight(hurt, ex = alternating).caution)
        // Switched off, only identical exercises share.
        assertEquals(Suggestion.NoNumber.NEW, Progression.suggest(alternating, 8..8, once, Rules(shared = false)))

        // Each of the words, on a lift it leaves alone.
        // Sharing a history, which an estimate from a near version isn't.
        fun shares(a: String, b: String) = (suggest(listOf(logged(1, 25, listOf(8, 8), ex = exercise(a))), ex = exercise(b)) as? Suggestion.Weight)?.change == Change.SAME
        for ((a, b) in listOf(
            "8 x chest press" to "8 x floor chest press", "8 x bench pullover" to "8 x pullover", "8 x chest fly" to "8 x bench chest fly",
            "8 x low row" to "8 x seated low row", "8 x shoulder press" to "8 x seated shoulder press", "8 x deadlift" to "8 x dumbbell deadlift",
            "8 x shoulder press" to "8 x standing alternating shoulder press", "8 x tricep extension" to "8 x seated tricep extension",
        )) {
            assertTrue("$a and $b", shares(a, b) && shares(b, a))
        }
        // Words that change the leverage or the balance keep a history of their own, and so do
        // "bench" and "seated" on a lift the legs do.
        for ((a, b) in listOf(
            "8 x chest press" to "8 x incline chest press", "8 x chest press" to "8 x single arm chest press", "8 x low row" to "8 x low row (explosive)",
            "8 x squat" to "8 x sumo squat", "8 x chest press" to "8 x bosu chest press", "8 x squat" to "8 x goblet squat",
            "8 x squat" to "8 x bench squat", "8 x chest press" to "8 x chest fly", "8 x calf raise" to "8 x seated calf raise",
            // A grip is lifted at its own weight beside the plain lift; a triceps extension on a
            // bench is another lift than one overhead; legs drive a thruster.
            "8 x chest press" to "8 x close grip chest press", "8 x bicep curl" to "8 x hammer curl", "8 x tricep extension" to "8 x bench tricep extension",
            "8 x thruster" to "8 x seated thruster",
        )) {
            assertTrue("$a and $b", !shares(a, b) && !shares(b, a))
        }
    }

    // "If it's the same exercise with minor tweaks", the sheet gives a number (madCode,
    // 2026-11-24): a lift never logged in these words takes an estimate from a near version.
    // madCode, 2026-11-24: a step earned long ago is held once before it is taken.
    @Test
    fun aStepEarnedLongAgoWaitsASession() {
        val clean = listOf(8, 8, 8)
        val earned = listOf(logged(1, 25, clean), logged(3, 25, clean))
        fun on(date: LocalDate, log: List<Logged> = earned, target: IntRange? = 8..8) =
            Progression.suggest(press, target, log, Rules(today = date)) as Suggestion.Weight
        // Within eight weeks the step is taken; with no day known, as before.
        assertEquals(Load(30, 2), on(day(3).plusWeeks(8)).load)
        assertEquals(Load(30, 2), weight(earned).load)
        // After longer it is held, and says why.
        val late = on(day(3).plusWeeks(20))
        assertEquals(listOf(Load(25, 2), Change.SAME, null), listOf(late.load, late.change, late.test))
        assertEquals(listOf("clean at 2×25, but that was 20 weeks ago: same again first, then up"), late.reasons)
        // One clean session at it on coming back, and the step is taken.
        val back = earned + Logged(day(3).plusWeeks(20), press, Load(25, 2), 8..8, clean)
        assertEquals(Load(30, 2), on(day(3).plusWeeks(21), back).load)
        // Neither a big jump's test nor a step earned at other reps is offered after that long.
        val big = listOf(logged(1, 20, clean), logged(3, 20, clean))
        assertEquals(Load(25, 2), on(day(4), big).test)
        assertNull(on(day(3).plusWeeks(20), big).test)
        assertNull(on(day(3).plusWeeks(20), target = 6..6).test)
        // "Felt easy" long ago is a step earned long ago too.
        assertEquals(Load(25, 2), on(day(1).plusWeeks(20), listOf(logged(1, 25, clean, easy = true))).load)
        // A weight that was only held is as it was: nothing to wait for.
        val once = on(day(1).plusWeeks(20), listOf(logged(1, 25, clean)))
        assertTrue(once.reasons.single().contains("up after one more clean session"))
    }

    // madCode, 2026-11-24: lifts filed under different names that are one lift done another
    // way lend each other a first number, never a heavier one.
    @Test
    fun aLiftFiledApartTakesAnEstimateFromItsKin() {
        fun estimate(line: String, log: List<Logged>) = suggest(log, ex = exercise(line))
        fun of(line: String, lb: Int, day: Int = 1, count: Int? = 2) = logged(day, lb, listOf(8, 8), ex = exercise(line), count = count)
        val forward = listOf(of("8 x forward lunge", 25), of("8 x forward lunge", 25, day = 2))
        // A step back is the same lunge: what was held, not the step up the other has earned.
        val reverse = estimate("8 x reverse lunge", forward) as Suggestion.Weight
        assertEquals(listOf(Load(25, 2), Change.EST), listOf(reverse.load, reverse.change))
        assertEquals("an estimate: your forward lunge stands at 2×25, and this is a small change to it", reverse.reasons.single())
        assertEquals(Load(25, 2), (estimate("8 x lunge", forward) as Suggestion.Weight).load)
        // A lateral lunge starts lighter; a lunge takes a lateral lunge's weight as it stands.
        val lateral = estimate("8 x lateral lunge", forward) as Suggestion.Weight
        assertEquals(Load(20, 2), lateral.load)
        assertEquals("an estimate: your forward lunge stands at 2×25, and this starts at about 0.8 times that dumbbell", lateral.reasons.single())
        assertEquals(Load(15, 2), (estimate("8 x reverse lunge", listOf(of("8 x lateral lunge", 15))) as Suggestion.Weight).load)
        // A split squat from the squat, and nothing the other way.
        assertEquals(Load(15, 2), (estimate("8 x split squat", listOf(of("8 x squat", 30))) as Suggestion.Weight).load)
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x squat", listOf(of("8 x split squat", 20))))
        // Lunges that weren't ruled on stay apart.
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x curtsy lunge", forward))
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x walking lunge", forward))
        // The lift's own wording is nearer than its kin: the goblet reverse lunge's own plain
        // version decides, not the heavier forward lunge.
        val both = forward + of("8 x reverse lunge", 15)
        assertEquals(Load(20, 1), (estimate("8 x goblet reverse lunge", both) as Suggestion.Weight).load)
        // However far its own version is worded, it comes before a kin worded alike: the kin
        // can't outweigh it, and a kin that twinged can't blank it.
        val slowBosu = listOf(of("8 x reverse lunge", 15), of("8 x slow bosu forward lunge", 20))
        assertEquals(Load(10, 2), (estimate("8 x slow bosu reverse lunge", slowBosu) as Suggestion.Weight).load)
        val twinged = listOf(of("8 x bosu reverse lunge", 15), logged(2, 25, listOf(8, 4), ex = exercise("8 x forward lunge"), hurt = true))
        assertEquals(Load(15, 2), (estimate("8 x reverse lunge", twinged) as Suggestion.Weight).load)
        // Kin and a tweak together: one dumbbell in both hands from the forward lunge's two.
        assertEquals(Load(35, 1), (estimate("8 x goblet reverse lunge", forward) as Suggestion.Weight).load)
        // Once logged in its own words the lift stands on its own.
        assertEquals(Change.SAME, (estimate("8 x reverse lunge", forward + of("8 x reverse lunge", 15, day = 3)) as Suggestion.Weight).change)
        // A kin that twinged, or whose count nobody said beside a one-arm row, lends nothing.
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x reverse lunge", listOf(logged(1, 25, listOf(8, 4), ex = exercise("8 x forward lunge"), hurt = true))))
    }

    @Test
    fun aTweakedLiftTakesAnEstimateFromANearVersion() {
        val pressed = listOf(logged(1, 25, listOf(8, 8)), logged(2, 25, listOf(8, 8)))
        fun estimate(line: String, log: List<Logged> = pressed) = suggest(log, ex = exercise(line))
        // What was held, not the step up the press has earned, and marked as an estimate.
        assertEquals(Load(30, 2), weight(pressed).load)
        val incline = estimate("8 x incline chest press") as Suggestion.Weight
        assertEquals(listOf(Load(25, 2), Change.EST, null), listOf(incline.load, incline.change, incline.test))
        assertEquals(listOf("an estimate: your chest press stands at 2×25, and this is a small change to it"), incline.reasons)
        // One arm takes the dumbbell each of two arms held, and two arms the one arm's.
        assertEquals(Load(25, 1), (estimate("8 x single arm chest press") as Suggestion.Weight).load)
        val oneArm = listOf(logged(1, 20, listOf(8, 8), ex = exercise("8 x single arm chest press"), count = 1))
        val both = estimate("8 x chest press", oneArm) as Suggestion.Weight
        assertEquals(Load(20, 2), both.load)
        assertEquals("an estimate: your single arm chest press stands at 1×20, and this is a small change to it", both.reasons.single())
        // But not when nobody said how many dumbbells the two-armed lift was: it could be one in both hands.
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x single arm chest press", listOf(logged(1, 25, listOf(8, 8), count = null))))
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x single arm chest press", listOf(logged(1, 25, listOf(8, 8), count = 1))))
        // Each of the tweaks madCode ruled on, alone and together.
        val squat = listOf(logged(1, 30, listOf(8, 8), ex = exercise("8 x squat")))
        val row = listOf(logged(1, 20, listOf(8, 8), ex = exercise("8 x low row")))
        assertEquals(Load(30, 2), (estimate("8 x sumo squat", squat) as Suggestion.Weight).load)
        assertEquals(Load(20, 2), (estimate("8 x low row (explosive)", row) as Suggestion.Weight).load)
        assertEquals(Load(20, 1), (estimate("8 x single arm low row (explosive)", row) as Suggestion.Weight).load)
        // The nearest version lends: one tweak away before two.
        val nearer = row + logged(2, 15, listOf(8, 8), ex = exercise("8 x single arm low row"), count = 1)
        assertEquals(Load(15, 1), (estimate("8 x single arm low row (explosive)", nearer) as Suggestion.Weight).load)
        // Two as near: the lighter of the two estimates, whichever was logged last.
        val two = listOf(logged(1, 20, listOf(8, 8), ex = exercise("8 x incline chest press")), logged(5, 30, listOf(8, 8), ex = exercise("8 x single arm chest press"), count = 1))
        assertEquals(Load(20, 1), (estimate("8 x single arm incline chest press", two) as Suggestion.Weight).load)
        // If one of them has nothing sound to lend, the other's would be the heavier: no number.
        val twinged = two + logged(6, 20, listOf(8, 8), ex = exercise("8 x incline chest press"), hurt = true)
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x single arm incline chest press", twinged))
        // A reason says a yes-or-no word by its name.
        val lying = estimate("8 x lying chest fly (explosive)", listOf(logged(1, 15, listOf(8, 8), ex = exercise("8 x lying chest fly")))) as Suggestion.Weight
        assertEquals("an estimate: your lying chest fly stands at 2×15, and this is a small change to it", lying.reasons.single())
        // Two arms take one arm's dumbbell only where the arm is the limit: not on a lunge,
        // where it would double what the legs move, nor a swing, held in both hands, nor a
        // press the legs drive.
        for (line in listOf("8 x reverse lunge", "8 x hip hinge swing", "8 x pullover", "8 x push press")) {
            val single = listOf(logged(1, 25, listOf(8, 8), ex = exercise(line.replace("8 x ", "8 x single arm ")), count = 1))
            assertEquals(line, Suggestion.NoNumber.NEW, estimate(line, single))
        }
        // The session the number rests on must say two dumbbells: an older one in both hands,
        // which Epley can't set beside the latest, would land in one arm.
        val mixed = listOf(logged(1, 40, listOf(15, 15), 15..15, ex = exercise("15 x deadlift"), count = 1), logged(2, 20, listOf(8, 8), ex = exercise("8 x deadlift")))
        assertEquals(Suggestion.NoNumber.NEW, suggest(mixed, ex = exercise("15 x single arm deadlift"), target = 15..15))
        // Converted from other reps, it is never more than that version was lifted at.
        val twelve = listOf(logged(1, 20, listOf(12, 12), 12..12))
        assertEquals(Load(20, 2), (suggest(twelve, ex = exercise("3 x chest press (explosive)"), target = 3..3) as Suggestion.Weight).load)
        // Logged in these words with nothing to judge, as an import can be: no estimate over it.
        val unjudged = pressed + Logged(day(3), exercise("8 x incline chest press"), Load(15, 2), 8..8, emptyList())
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x incline chest press", unjudged))
        // A band pulls across the lift: the banded row and the plain one take the same dumbbell.
        val banded = listOf(logged(1, 15, listOf(8, 8), ex = exercise("8 x mid band upright row")))
        assertEquals(Load(15, 2), (estimate("8 x upright row", banded) as Suggestion.Weight).load)
        assertEquals(Load(15, 2), (estimate("8 x mid band upright row", listOf(logged(1, 15, listOf(8, 8), ex = exercise("8 x upright row")))) as Suggestion.Weight).load)
        // A row the sheet gives no weight (pulses with nothing held, which an import can still
        // have a number for) has none to lend.
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x squat", listOf(logged(1, 15, listOf(8, 8), ex = exercise("8 x squat pulses")))))
        // Sumo is a stance for a squat or a deadlift.
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x sumo calf raise", listOf(logged(1, 30, listOf(8, 8), ex = exercise("8 x calf raise")))))
        // A version that twinged lends nothing, nor a tweak nobody ruled on, nor another lift.
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x incline chest press", pressed + logged(3, 25, listOf(8, 8), hurt = true)))
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x single leg chest press"))
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x chest fly"))
        // Logged once, it has a history of its own, and the plain lift's no longer speaks for it.
        val own = pressed + logged(3, 20, listOf(8, 8), ex = exercise("8 x incline chest press"))
        assertEquals(listOf(Load(20, 2), Change.SAME), (estimate("8 x incline chest press", own) as Suggestion.Weight).let { listOf(it.load, it.change) })
        assertEquals(Load(30, 2), weight(own).load)
        // Switched off, a tweak is a new lift.
        assertEquals(Suggestion.NoNumber.NEW, Progression.suggest(exercise("8 x incline chest press"), 8..8, pressed, Rules(estimates = false)))
        // A bare "tricep extension" is the overhead one: those two share a history outright.
        val overhead = suggest(listOf(logged(1, 20, listOf(8, 8), ex = exercise("8 x tricep extension"), count = 1)), ex = exercise("8 x overhead tricep extension"))
        assertEquals(listOf(Load(20, 1), Change.SAME), (overhead as Suggestion.Weight).let { listOf(it.load, it.change) })
    }

    // The tweaks that make a lift harder start it lighter, by a stated factor rounded down to
    // the rack; the easier way round, the harder version's weight stands (madCode, 2026-11-24).
    @Test
    fun aHarderTweakStartsLighter() {
        fun on(line: String, lb: Int, count: Int? = 2) = listOf(logged(1, lb, listOf(8, 8), ex = exercise(line), count = count))
        fun estimate(line: String, log: List<Logged>) = (suggest(log, ex = exercise(line)) as? Suggestion.Weight)?.load
        val press = on("8 x chest press", 30)
        val squat = on("8 x squat", 40)
        // Lying on a bosu costs a press less than standing on one costs a squat.
        assertEquals(Load(25, 2), estimate("8 x bosu chest press", press))
        assertEquals(Load(30, 2), estimate("8 x bosu squat", squat))
        val bosu = suggest(squat, ex = exercise("8 x bosu squat")) as Suggestion.Weight
        assertEquals(listOf("an estimate: your squat stands at 2×40, and this starts at about 0.8 times that dumbbell"), bosu.reasons)
        // Tempos: slow and pulse 0.85, a pause 0.9, rounded down.
        assertEquals(Load(25, 2), estimate("8 x chest press (slow)", press))
        assertEquals(Load(25, 2), estimate("8 x chest press (pause)", press))
        assertEquals(Load(35, 2), estimate("8 x squat (pause)", squat))
        // Two tweaks multiply: slow on a bosu is 0.85 of 0.9 of 30, which is under 25.
        assertEquals(Load(20, 2), estimate("8 x bosu chest press (slow)", press))
        // The same weight: a grip, a decline, kneeling, another handle.
        for (line in listOf("8 x close grip chest press", "8 x decline chest press")) assertEquals(line, Load(30, 2), estimate(line, press))
        // Not a kettlebell, which comes in other weights; nor a reverse grip, another lift on a press.
        for (line in listOf("8 x kettlebell chest press", "8 x reverse grip chest press")) assertNull(line, estimate(line, press))
        // A hammer curl is the strong one: it says too much for the plain curl, but not the other way.
        assertEquals(Load(20, 2), estimate("8 x hammer curl", on("8 x bicep curl", 20)))
        assertNull(estimate("8 x bicep curl", on("8 x hammer curl", 20)))
        // Kneeling is the same lift for the arms, not for a press the legs drive.
        assertNull(estimate("8 x kneeling push press", on("8 x push press", 25)))
        assertEquals(Load(20, 2), estimate("8 x half kneeling shoulder press", on("8 x shoulder press", 20)))
        // The easier way round, the harder version's weight stands: never divided up.
        assertEquals(Load(25, 2), estimate("8 x chest press", on("8 x bosu chest press", 25)))
        assertEquals(Load(30, 2), estimate("8 x squat", on("8 x squat (pause)", 30)))
        // A staggered stance is 0.8 of two legs, where one leg is 0.6.
        val deadlift = on("8 x deadlift", 40)
        assertEquals(Load(30, 2), estimate("8 x staggered deadlift", deadlift))
        assertEquals(Load(30, 2), estimate("8 x b-stance deadlift", deadlift))
        // 0.85 is said as 0.85, not rounded into a pause's 0.9.
        assertEquals("an estimate: your chest press stands at 2×30, and this starts at about 0.85 times that dumbbell", (suggest(press, ex = exercise("8 x chest press (slow)")) as Suggestion.Weight).reasons.single())
        assertEquals(Load(20, 2), estimate("8 x single leg deadlift", deadlift))
    }

    // One dumbbell in both hands against one in each (madCode, 2026-11-24): 1.5 of one of the
    // pair where it is held at the chest or overhead, 2 on a deadlift, where it hangs.
    @Test
    fun oneDumbbellInBothHandsIsAMultipleOfOneOfThePair() {
        fun on(line: String, lb: Int, count: Int? = 2) = listOf(logged(1, lb, listOf(8, 8), ex = exercise(line), count = count))
        fun estimate(line: String, log: List<Logged>) = suggest(log, ex = exercise(line))
        val goblet = estimate("8 x goblet squat", on("8 x squat", 20)) as Suggestion.Weight
        assertEquals(listOf(Load(30, 1), Change.EST), listOf(goblet.load, goblet.change))
        assertEquals(listOf("an estimate: your squat stands at 2×20, and this starts at about 1.5 times that dumbbell"), goblet.reasons)
        // 1.5 of 25 is 37.5: down to 35.
        assertEquals(Load(35, 1), (estimate("8 x goblet squat", on("8 x squat", 25)) as Suggestion.Weight).load)
        assertEquals(Load(30, 1), (estimate("8 x goblet reverse lunge", on("8 x reverse lunge", 20)) as Suggestion.Weight).load)
        assertEquals(Load(40, 1), (estimate("8 x single dumbbell deadlift", on("8 x deadlift", 20)) as Suggestion.Weight).load)
        // And back, to a row that says two dumbbells: 30 at the chest is 20 in each hand.
        assertEquals(Load(20, 2), (estimate("8 x two dumbbell squat", on("8 x goblet squat", 30, count = 1)) as Suggestion.Weight).load)
        assertEquals(Load(20, 2), (estimate("8 x two dumbbell deadlift", on("8 x single dumbbell deadlift", 40, count = 1)) as Suggestion.Weight).load)
        // Only from a session that says how it was held, and only where a multiple was stated.
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x goblet squat", on("8 x squat", 20, count = null)))
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x goblet squat", on("8 x squat", 40, count = 1)))
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x single dumbbell chest press", on("8 x chest press", 20)))
        // A bare row says nothing of how it is held: no estimate from the goblet one.
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x squat", on("8 x goblet squat", 30, count = 1)))
        // Not on one leg, where "single dumbbell" is as often one hand's.
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x single dumbbell single leg deadlift", on("8 x single leg deadlift", 20)))
        // Of two as near, the lower dumbbell, however each was held.
        val mixed = on("8 x squat (slow)", 30) + logged(2, 35, listOf(8, 8), ex = exercise("8 x squat (pause)"), count = 1)
        assertEquals(30, (estimate("8 x squat", mixed) as Suggestion.Weight).load.lb)
        // A "two dumbbell" row logged with one lends nothing to a row that says two.
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x two dumbbell squat", on("8 x two dumbbell squat (slow)", 30, count = 1)))
        // Twice one of the pair on a deadlift only: a good morning holds it at the chest.
        assertEquals(Suggestion.NoNumber.NEW, estimate("8 x single dumbbell good morning", on("8 x good morning", 15)))
        // A goblet version in the log doesn't stand in the way of a nearer plain one.
        val both = on("8 x squat", 30) + logged(2, 40, listOf(8, 8), ex = exercise("8 x sumo goblet squat"), count = 1)
        assertEquals(Load(30, 2), (estimate("8 x sumo squat", both) as Suggestion.Weight).load)
        // "Two dumbbells" said of a lift already done with two is the same lift.
        assertEquals(Load(20, 2), (estimate("8 x two dumbbell squat", on("8 x squat", 20)) as Suggestion.Weight).load)
    }

    // One side at a time is the same weight only with a dumbbell in each hand: one dumbbell may
    // have been held in both hands, which in one arm would be double, and a count nobody said
    // could be either.
    @Test
    fun alternatingSharesOnlyWithADumbbellInEachHand() {
        val curl = exercise("8 x bicep curl")
        val alternating = exercise("8 x alternating bicep curl")
        for (count in listOf(1, null)) {
            assertEquals(Suggestion.NoNumber.NEW, suggest(listOf(logged(1, 25, listOf(8, 8), ex = curl, count = count)), ex = alternating))
            assertEquals(Suggestion.NoNumber.NEW, suggest(listOf(logged(1, 25, listOf(8, 8), ex = alternating, count = count)), ex = curl))
        }
        // A one-dumbbell session is left out; the two-dumbbell one still lends.
        val mixed = listOf(logged(1, 40, listOf(8, 8), ex = curl, count = 1), logged(2, 20, listOf(8, 8), ex = curl))
        assertEquals(Load(20, 2), weight(mixed, ex = alternating).load)
        // Between two wordings that are both a side at a time, the count is whatever it was;
        // and where the words say how it is held, a side at a time doesn't change it.
        assertEquals(Load(25, 1), weight(listOf(logged(1, 25, listOf(8, 8), ex = alternating, count = 1)), ex = exercise("8 x seated alternating bicep curl")).load)
        val goblet = listOf(logged(1, 30, listOf(8, 8), ex = exercise("8 x goblet lateral lunge"), count = 1))
        assertEquals(Load(30, 1), weight(goblet, ex = exercise("8 x goblet alt lateral lunge")).load)
        // The one-leg link reads a shared history too.
        val deadlift = listOf(logged(1, 40, listOf(8, 8, 8), ex = exercise("8 x dumbbell deadlift"), easy = true))
        assertTrue(suggest(deadlift, ex = exercise("8 x single leg deadlift")) is Suggestion.Weight)
    }

    // Trap 11: a loose relative is no basis. A fly says nothing about a press.
    @Test
    fun aLooseRelativeIsNoBasis() {
        val fly = exercise("8 x chest fly")
        val s = suggest(listOf(logged(1, 30, listOf(8, 8, 8))), ex = fly)
        assertEquals(Suggestion.NoNumber.NEW, s)
        // Nor does a two-legged lift with another variation feed a one-legged one.
        val log = listOf(logged(1, 40, listOf(8, 8, 8), ex = exercise("8 x sumo deadlift")))
        assertTrue(suggest(log, ex = exercise("8 x single leg deadlift")) is Suggestion.NoNumber)
    }

    @Test
    fun nothingLoggedIsGoLight() {
        assertEquals(Suggestion.NoNumber.NEW, suggest(emptyList()))
        assertNull((suggest(listOf(logged(1, 25, listOf(8, 8, 8)))) as Suggestion.Weight).test)
    }

    // Review findings: a hurt set still caps whatever the other rules do.
    @Test
    fun aHurtSetCapsEveryOtherRule() {
        // Hurt and short at 35 after a clean 40: under 35, as a miss would be (trap 4).
        val hurtMiss = weight(listOf(logged(1, 40, listOf(8, 8, 8)), logged(3, 35, listOf(3), hurt = true)))
        assertTrue(hurtMiss.load.lb < 35)
        // Epley to fewer reps doesn't lift the number past what hurt.
        val epley = listOf(logged(1, 20, listOf(12, 12), target = 12..12), logged(3, 20, listOf(12, 12), target = 12..12, hurt = true))
        assertEquals(Load(20, 2), weight(epley, target = 3..3).load)
        // No test weight at or above what hurt.
        val test = weight(listOf(logged(1, 12, listOf(8, 8, 8)), logged(3, 12, listOf(8, 8, 8), hurt = true)))
        assertNull(test.test)
    }

    @Test
    fun nothingLoggedOnARepTargetSaysNothing() {
        assertEquals(Suggestion.NoNumber.NEW, suggest(listOf(logged(1, 25, emptyList()))))
    }

    @Test
    fun timedAndCountedSetsDontConvert() {
        assertTrue(suggest(listOf(logged(1, 40, listOf(3, 3), target = 3..3)), target = null) is Suggestion.NoNumber)
        assertTrue(suggest(listOf(logged(1, 40, emptyList(), target = null)), target = 8..8) is Suggestion.NoNumber)
    }

    // Trap 8: each round of a ladder is judged against its own count; the number is for round 1.
    @Test
    fun aLadderIsJudgedRoundByRound() {
        val asListed = listOf(logged(1, 25, listOf(8, 6, 4), target = null, ladder = listOf(8, 6, 4), easy = true))
        assertEquals(Load(30, 2), weight(asListed, target = 8..8).load)
        val short = listOf(logged(1, 25, listOf(8, 5, 4), target = null, ladder = listOf(8, 6, 4)))
        assertEquals(Load(25, 2), weight(short, target = 8..8).load)
    }

    @Test
    fun aMissAtTheLightestSaysSo() {
        val s = weight(listOf(logged(1, 3, listOf(5, 8))))
        assertEquals(Load(3, 2), s.load)
        assertTrue(s.reasons.single().contains("lightest"))
    }
}
