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
    fun anUnknownSchemaIsRefused() {
        val error = runCatching {
            Vocabulary.parse("""{"schema":"spotter-movements/2","movements":[]}""",
                """{"schema":"spotter-words/1","variations":{},"connectors":{},"filler":[],"scheme":[]}""")
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun theLongestPhraseIsKnown() {
        // "count and remember reps" is four words; a reader looks that far ahead.
        assertEquals(4, words.longestPhrase)
    }
}
