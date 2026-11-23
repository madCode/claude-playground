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
        assertEquals(Change.NEW, s.change)
        assertTrue(s.reasons.single().contains("0.6"))
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
        fun shares(a: String, b: String) = suggest(listOf(logged(1, 25, listOf(8, 8), ex = exercise(a))), ex = exercise(b)) is Suggestion.Weight
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
