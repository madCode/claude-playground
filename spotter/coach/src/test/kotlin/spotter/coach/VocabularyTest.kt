package spotter.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import spotter.coach.Meaning.OfMovement
import spotter.coach.Meaning.Variation

class VocabularyTest {
    private val words = Vocabulary.starting()

    private fun movement(phrase: String): String? = (words.lookup(phrase) as? OfMovement)?.movement?.name

    // "Floor close grip chest press" was read as a plain bench press: the grip and the position
    // were lost. Here each is its own word, and the movement is only "chest press".
    @Test
    fun gripAndPositionAreVariationsNotPartOfTheMovement() {
        assertEquals(Variation("grip", "close"), words.lookup("close grip"))
        assertEquals(Variation("position", "floor"), words.lookup("floor"))
        assertEquals("chest press", movement("chest press"))
        assertEquals("chest press", movement("bench press"))
        assertNull(words.lookup("close grip chest press"))
    }

    @Test
    fun aHammerCurlIsACurlWithAGripNotAnotherNameForIt() {
        assertEquals(Variation("grip", "hammer"), words.lookup("hammer"))
        assertNull(words.lookup("hammer curl"))
        assertEquals("bicep curl", movement("curl"))
    }

    // Trap 5: "high plank pull through" is not a high pull.
    @Test
    fun highPlankIsAPositionAndHighPullAMovement() {
        assertEquals(Variation("position", "high plank"), words.lookup("high plank"))
        assertEquals("high pull", movement("high pull"))
        assertEquals("plank pull through", movement("pull through"))
    }

    // Trap 10: lunges and step-ups are one-legged even without a stance word.
    @Test
    fun oneLeggedMovementsSaySo() {
        for (name in listOf("step-up", "lunge", "reverse lunge", "lateral lunge", "split squat", "airplane")) {
            assertTrue(name, words.movements.single { it.name == name }.oneLeg)
        }
        assertFalse(words.movements.single { it.name == "deadlift" }.oneLeg)
        assertEquals(Variation("legs", "single"), words.lookup("single leg"))
    }

    @Test
    fun looseAliasesAreGone() {
        // Carry filed these under another exercise; each meant something else on some board.
        assertNull(words.lookup("rotation"))
        assertNull(words.lookup("power clean"))
        assertEquals("glute bridge", movement("glute bridge"))
        assertEquals("hip thrust", movement("hip thrust"))
    }

    @Test
    fun spellingsAreNormalised() {
        assertEquals("step-up", movement("Step Up"))
        assertEquals("farmer's carry", movement("Farmers  Carry"))
        assertEquals(Variation("legs", "single"), words.lookup("Single-Leg"))
    }

    @Test
    fun whereTheBandOrBosuGoesIsAVariation() {
        assertEquals(Variation("band", "mid"), words.lookup("mid band"))
        assertEquals(Variation("stability", "bosu"), words.lookup("feet on bosu"))
        assertEquals("plank pop", movement("plank pops"))
    }

    // "wide" is a stance on a squat and a grip on a curl, so on its own it is no one thing.
    @Test
    fun aWordThatChangesMeaningByMovementIsLeftUnsure() {
        assertNull(words.lookup("wide"))
        assertEquals(Variation("legs", "wide"), words.lookup("wide stance"))
        assertEquals(Variation("grip", "wide"), words.lookup("wide grip"))
    }

    // "alt" alternates legs on a lunge and arms on a curl; it isn't an arm count.
    @Test
    fun alternatingIsItsOwnAxis() {
        assertEquals(Variation("alternating", "yes"), words.lookup("alt"))
        assertEquals(Variation("arms", "single"), words.lookup("single arm"))
    }

    // Trap 1: "12 x 2 dumbbell bench bridge" says two dumbbells.
    @Test
    fun twoDumbbellsIsReadWithOrWithoutAnS() {
        assertEquals(Variation("implement", "two dumbbells"), words.lookup("2 dumbbell"))
        assertEquals(Variation("implement", "two dumbbells"), words.lookup("2 dumbbells"))
    }

    @Test
    fun aliasesThatHideAVariationAreGone() {
        // A Bulgarian split squat is rear foot elevated; a bare fly could be a reverse fly.
        assertNull(words.lookup("bulgarian split squat"))
        assertNull(words.lookup("fly"))
        assertNull(words.lookup("full v-up"))
        assertEquals(Variation("lying", "prone"), words.lookup("prone"))
        assertEquals(Variation("lying", "supine"), words.lookup("supine"))
    }

    // Board text carries word joiners and HTML entities.
    @Test
    fun invisibleCharactersAndEntitiesDontHideAWord() {
        assertEquals(Meaning.Filler, words.lookup("\u2060Finisher"))
        assertEquals(Meaning.Filler, words.lookup("count &amp; remember reps"))
        assertEquals("step-up", movement("step\u2011up"))
    }

    @Test
    fun aMovementListedTwiceIsRefused() {
        val movements = """{"schema":"spotter-movements/1","movements":[{"name":"plank"},{"name":"Plank"}]}"""
        val error = runCatching { Vocabulary.parse(movements, EMPTY_WORDS) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun aWordInNoListIsUnknown() {
        assertNull(words.lookup("windmill"))
    }

    @Test
    fun theFillerAndSchemeWordsAreKnown() {
        assertEquals(Meaning.Filler, words.lookup("rest"))
        assertEquals(Meaning.Scheme, words.lookup("each"))
        assertEquals(Meaning.Connector("choice"), words.lookup("or"))
    }

    @Test
    fun aPhraseThatMeansTwoThingsIsRefused() {
        val movements = """{"schema":"spotter-movements/1","movements":[{"name":"plank"}]}"""
        val wordsJson = """{"schema":"spotter-words/1","variations":{"position":{"plank":["plank"]}},
            "connectors":{},"filler":[],"scheme":[]}"""
        val error = runCatching { Vocabulary.parse(movements, wordsJson) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("\"plank\" means two things"))
    }

    @Test
    fun anUnknownSchemaIsRefusedByName() {
        // A newer file with a field this one doesn't know still fails on its schema, saying so.
        val error = runCatching {
            Vocabulary.parse("""{"schema":"spotter-movements/2","movements":[],"added":1}""", EMPTY_WORDS)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("spotter-movements/2"))
    }

    private companion object {
        const val EMPTY_WORDS = """{"schema":"spotter-words/1","variations":{},"connectors":{},"filler":[],"scheme":[]}"""
    }

    @Test
    fun theLongestPhraseIsKnown() {
        // "count and remember reps" is four words; a reader looks that far ahead.
        assertEquals(4, words.longestPhrase)
    }
}
