package spotter.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Lines are from public r/orangetheory boards (carry/brain-fixtures/boards.jsonl), read through
// BoardLine first, the way the app reads them.
class IdentityTest {
    private val identity = Identity(Vocabulary.starting())
    private fun read(line: String) = identity.read(BoardLine.read(line).words)
    private fun known(line: String) = read(line) as Reading.Known
    private fun notSure(line: String) = read(line) as Reading.NotSure

    // madCode's case: Carry filed this as a plain bench press.
    @Test
    fun aFloorCloseGripChestPressKeepsItsGripAndPosition() {
        val press = known("12 x floor close grip chest press")
        assertEquals("chest press [grip=close, position=floor]", press.key)
        assertNotEquals(known("12 x chest press").key, press.key)
    }

    // Trap 6: a bare line says nothing about what is held; "air" and "bodyweight" do.
    @Test
    fun aBareLineIsNotBodyweight() {
        assertNull(known("5 x squat").parts.single().implement)
        assertEquals("bodyweight", known("12 x air squat").parts.single().implement)
        assertEquals("one dumbbell, both hands", known("10 total x goblet alt transverse squat, rest").parts.single().implement)
        assertEquals("hold", known("~20 sec of full v-up hold, rest").parts.single().variations["mode"])
    }

    // Trap 10: one-legged without a stance word, and one-legged because of one.
    @Test
    fun oneLeggedRowsSaySo() {
        assertTrue(known("8 x step-up").parts.single().oneLeg)
        assertTrue(known("10 total x TRX alt reverse lunge, rest").parts.single().oneLeg)
        assertTrue(known("8 x single leg deadlift").parts.single().oneLeg)
        assertTrue(known("6 each x split stance reverse fly").parts.single().oneLeg)
        assertFalse(known("8 x deadlift").parts.single().oneLeg)
    }

    @Test
    fun aComboIsItsParts() {
        val combo = known("6 - 10 x alt reverse lunge to goblet squat")
        assertEquals(listOf("reverse lunge", "squat"), combo.parts.map { it.movement.name })
        assertEquals(
            "reverse lunge [alternating=yes, implement=one dumbbell, both hands] + squat [implement=one dumbbell, both hands]",
            combo.key,
        )
    }

    // An implement named once is held for the whole combo, wherever the line puts it, so the
    // curl in a TRX combo isn't a dumbbell curl and both spellings are one exercise.
    @Test
    fun aComboSharesWhatIsHeld() {
        val trx = known("8 x TRX high row to bicep curl")
        assertEquals(listOf("trx", "trx"), trx.parts.map { it.implement })
        assertEquals(known("8 x TRX squat to TRX high row").key, known("8 x TRX squat to high row").key)
        val lunge = known("6 each x cross-back lunge to lateral knee raise (bodyweight)")
        assertEquals(listOf("bodyweight", "bodyweight"), lunge.parts.map { it.implement })
        assertEquals("bosu", known("8 x bosu squat to shoulder press").parts[1].variations["stability"])
        // Parts that say different things keep their own.
        val mixed = known("8 x bodyweight squat to dumbbell shoulder press")
        assertEquals(listOf("bodyweight", "dumbbell"), mixed.parts.map { it.implement })
    }

    // Standing on one leg to press is a stance, not the isometric "shoulder press hold".
    @Test
    fun aStanceHoldIsNotAnIsometric() {
        val press = known("8 each x low band single leg hold with single arm shoulder press")
        assertEquals(null, press.parts.single().variations["mode"])
        assertTrue(press.parts.single().oneLeg)
        assertEquals("hold", known("8 x lateral raise hold").parts.single().variations["mode"])
    }

    // "with" before an implement or a modifier is a variation, not a second movement.
    @Test
    fun withAnImplementIsAVariation() {
        assertEquals("speed skater [tempo=explosive]", known("12 total x speed skater with power").key)
        assertEquals(2, known("45 sec of dumbbell burpee with alt low row").parts.size)
        assertEquals("shoulder press [implement=y-bell, tempo=explosive]", known("8 x explosive overhead press with y-bell").key)
    }

    // A position before "with" is how the movement after it is done.
    @Test
    fun aBridgeHoldWithAPressIsAPressInABridgeHold() {
        assertEquals("chest press [position=bridge hold]", known("8 x bridge hold with chest press").key)
        assertEquals("plank [position=low plank]", known("**Finisher**: 1 min of low plank").key)
    }

    // A full read can still be wrong: these are refused (DESIGN.md).
    @Test
    fun twoMovementsSideBySideAreNotSure() {
        assertEquals(Why.TWO_MOVEMENTS, notSure("6 - 10 each x bench bird dog low row, rest").why)
        assertEquals(Why.TWO_MOVEMENTS, notSure("12 x TRX bridge row").why)
        assertEquals(Why.TWO_MOVEMENTS, notSure("8 total x low bench alt step-down reverse lunge to hammer curl").why)
    }

    @Test
    fun aChoiceIsNotSure() {
        assertEquals(Why.A_CHOICE, notSure("Repeat until finisher: 30 sec V hold or 30 sec high plank").why)
    }

    @Test
    fun anUnknownWordIsNotSureAndNamed() {
        val zigzag = notSure("6 - 10 total x zigzag lunge, rest")
        assertEquals(Why.UNKNOWN_WORDS, zigzag.why)
        assertEquals(listOf("zigzag"), zigzag.words)
        // "wide" alone is a grip on a curl, and on a squat a stance no list settles.
        assertEquals(listOf("wide"), notSure("8 x wide squat").words)
    }

    @Test
    fun twoValuesOnOneAxisAreNotSure() {
        assertEquals(Why.CONFLICT, notSure("8 each x low bench high plank single arm low row").why)
    }

    @Test
    fun wordsThatOnlyDescribeAreNotAnExercise() {
        assertEquals(Why.NO_MOVEMENT, identity.read("single arm").let { (it as Reading.NotSure).why })
        assertEquals(Why.EMPTY, identity.read("").let { (it as Reading.NotSure).why })
    }

    // Heavy and lighter shape the number, not the exercise.
    @Test
    fun intensityIsAHintNotIdentity() {
        val heavy = known("5 x sumo deadlift (heavy)")
        assertEquals(setOf("heavy"), heavy.hints)
        assertEquals(known("5 x sumo deadlift").key, heavy.key)
    }

    // The range and the band have axes of their own: these were refused as clashes.
    @Test
    fun rangeTempoBandAndLyingCombine() {
        assertEquals("squat [range=half, tempo=pulse]", known("~20 sec of pulsing half squat, rest").key)
        assertEquals("glute bridge [band=on, implement=dumbbell]", known("10 x dumbbell mini band bridge").key)
        assertEquals("tricep extension [lying=yes, position=bench]", known("8 x tricep extension (lying on bench), rest").key)
    }

    // Bare "close" or "narrow" is a stance on a squat and a grip on a press, like "wide".
    @Test
    fun aBareCloseIsNotAGrip() {
        assertEquals(Why.UNKNOWN_WORDS, notSure("8 x narrow squat").why)
        assertEquals(Why.UNKNOWN_WORDS, notSure("8 x close squat").why)
        assertEquals(known("8 x dumbbells bicep curl").key, known("8 x dbs bicep curl").key)
    }

    // Words from the corpus whose meaning is plain: each is its own exercise, so it borrows no
    // number from a plainer one ("tap squat" is not a squat, "tricep push-up" not a push-up).
    @Test
    fun corpusExercisesAreTheirOwn() {
        assertEquals("tap squat [position=bench]", known("10 x bench tap squat").key)
        assertEquals(
            "step-out squat [alternating=yes, implement=one dumbbell, both hands]",
            known("10 total x goblet alt step-out squat").key,
        )
        assertEquals("tricep push-up", known("8 x tricep push-up, rest").key)
        assertEquals("squat jack", known("30 sec of squat jacks").key)
        assertEquals(setOf("moderate"), known("5 x sumo goblet squat (moderate)").hints)
    }

    // Setup words that settle a variation read as it; ones that could mean either of two
    // exercises stay unknown.
    @Test
    fun setupWordsReadOnlyWhenTheyAreUnambiguous() {
        assertEquals("rear foot elevated", known("8 each x RFE split squat (rear foot on bench)").parts.single().variations["legs"])
        // On a bridge it is a bench bridge, on a press a press in a bridge: not one word.
        assertEquals(Why.UNKNOWN_WORDS, notSure("8 x close grip chest press (shoulders on bench)").why)
        // Feet in the straps is a different push-up from hands in them.
        assertNotEquals(known("10 x push-up").key, known("10 x push-up (feet in straps)").key)
        // madCode: feet on the bench is feet up on a climber, and a hip thrust on a bridge.
        assertEquals("mountain climber [position=decline]", known("10 total x mountain climber (feet on bench)").key)
        assertEquals(known("8 x bench bridge").key, known("8 x bridge (shoulders on bench)").key)
    }

    // madCode's answers on the board words, 2026-11-22.
    @Test
    fun madCodesAnswersOnBoardWords() {
        assertEquals("sit-up to stand [implement=dumbbell, position=bench]", known("8 x dumbbell bench sit-up to stand").key)
        assertEquals("shoulder press [legs=seated] + stand", known("10 x seated shoulder press to stand").key)
        assertEquals("single", known("6 each x balance bicep curl").parts.single().variations["legs"])
        // A bare "press" after a squat or lunge is a shoulder press; after a deadbug it isn't.
        assertEquals("squat + shoulder press", known("30 sec of squat to press").key)
        assertEquals(Why.UNKNOWN_WORDS, notSure("8 total x alt deadbug with press").why)
        assertEquals(Why.UNKNOWN_WORDS, notSure("8 x deadbug to press").why)
        assertEquals(Why.UNKNOWN_WORDS, notSure("8 x y-raise to press").why)
        assertEquals("single", known("4 each x single arm squat to press").parts[1].variations["arms"])
        // "with rotation" is a torso rotation; "trx rotation chest press" is unknown.
        assertEquals("torso rotation", known("8 total x alt neutral grip push press with rotation").parts[1].movement.name)
        assertEquals(Why.UNKNOWN_WORDS, notSure("8 x high plank with rotation").why)
        assertEquals(Why.UNKNOWN_WORDS, notSure("8 x deadbug with rotation").why)
        assertEquals("grip", known("8 x wide bicep curl").parts.single().variations.entries.single { it.value == "wide" }.key)
        assertEquals("hinged", known("8 each x single arm split squat with hinge").parts.single().variations["torso"])
        // "switch" passes the one dumbbell between hands; "anchor" is the move a circuit returns to.
        assertEquals(known("12 total x bear plank alt low row").key, known("12 total x bear plank switch alt low row").key)
        // A switch lunge is a jumping lunge, not a lunge.
        assertEquals(Why.UNKNOWN_WORDS, notSure("8 x switch lunge").why)
        assertEquals(known("8 x chest fly").key, known("8 x chest fly (anchor)").key)
        assertEquals("plank jack", known("8 x high plank jack").parts.single().movement.name)
        assertEquals("b-stance", known("6 each x low bench b-stance goblet squat").parts.single().variations["legs"])
        assertEquals("ultimate burpee", known("30 sec of ultimate burpee").key)
        assertEquals("dumbbell tap [alternating=yes, position=high plank]", known("6 - 10 total x high plank alt dumbbell tap, rest").key)
    }

    // madCode's second round of answers, 2026-11-22, on corpus lines.
    @Test
    fun madCodesSecondAnswers() {
        // Setups: what a hand or foot rests on, kept apart from the same move without it.
        assertEquals("low row [arms=single, legs=split, stability=hand on bench]", known("8 each x split stance single arm low row (hand on bench)").key)
        assertEquals("foot on bench", known("6 each x low bench single leg bridge (foot on bench)").parts.single().variations["stability"])
        assertEquals("knee tuck [implement=trx, position=high plank, stability=feet in straps]", known("1 min of TRX high plank knee tuck (feet in straps)").key)
        // A rear foot on the bench is still a split squat's elevation, not this setup.
        assertEquals("rear foot elevated", known("8 each x bench RFE split squat (rear foot on bench)").parts.single().variations["legs"])
        // Moves of their own.
        assertEquals("tap squat jump", known("8 x tap squat jump, rest").key)
        assertEquals(listOf("lateral lunge", "single leg hop"), known("4 each x lateral lunge to single leg hop").parts.map { it.movement.name })
        assertEquals("split squat hop [implement=bodyweight]", known("6 each x bodyweight split squat hop").key)
        assertEquals("bent arm lateral raise", known("10 x bent arm lateral raise").key)
        assertNotEquals(known("10 x lateral raise").key, known("10 x bent arm lateral raise").key)
        assertEquals("straight arm sit-up [position=low bench]", known("12 x low bench straight arm sit-up").key)
        // One arm or both reach overhead, so the reach says neither.
        assertEquals("overhead reach", known("5 each x bodyweight lateral lunge to knee raise with overhead reach, rest").parts.last().movement.name)
        assertEquals("chest press + reach", known("10 x chest press to reach").key)
    }

    // A reach and the hops are done without a weight; the press before a reach takes the number.
    @Test
    fun theNewMovesWantNoWeightOfTheirOwn() {
        // Lines that don't say "bodyweight": the move itself must.
        val sheet = Sheet.make(listOf("10 x chest press to reach", "8 x tap squat jump, rest", "12 x low bench straight arm sit-up"), emptyList(), identity).rows
        assertEquals(Suggestion.NoNumber.NEW, sheet[0].suggestion)
        assertNull(sheet[1].suggestion)
        assertNull(sheet[2].suggestion)
        // With a chest press logged, the reach doesn't stand in the way of its number.
        val press = Logged(java.time.LocalDate.of(2026, 3, 1), known("10 x chest press"), Load(25, 2), 10..10, listOf(10))
        val withLog = Sheet.make(listOf("10 x chest press to reach"), listOf(press), identity).rows.single()
        assertEquals(Load(30, 2), (withLog.suggestion as Suggestion.Weight).load)
    }

    // Hands on the Bosu and feet on it are different exercises; "bosu … (feet on bosu)" says
    // where, and isn't two Bosus.
    @Test
    fun whereOnTheBosuIsKeptApart() {
        val feet = known("16 total x high plank alt knee drive (feet on bosu)")
        val hands = known("16 total x high plank alt knee drive (hands on bosu)")
        assertNotEquals(feet.key, hands.key)
        assertEquals("feet on bosu", known("8 x bosu high plank alt knee drive (feet on bosu)").parts.single().variations["stability"])
        // In a combo each part keeps where it says: a squat on a Bosu is not hands on it.
        val combo = known("30 sec of bosu squat to push-up (hands on bosu)")
        assertEquals(listOf("bosu", "hands on bosu"), combo.parts.map { it.variations["stability"] })
    }
}
