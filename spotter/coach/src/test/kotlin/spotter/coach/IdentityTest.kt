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
        val tap = notSure("6 - 10 total x high plank alt dumbbell tap, rest")
        assertEquals(Why.UNKNOWN_WORDS, tap.why)
        assertEquals(listOf("tap"), tap.words)
        // "wide" alone is a stance on a squat and a grip on a curl.
        assertEquals(listOf("wide"), notSure("8 x wide bicep curl").words)
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
}
