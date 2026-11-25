package spotter.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Invented words on real-shaped board lines.
class WordAnswersTest {
    private val lists = Vocabulary.starting()
    private fun read(line: String, answers: List<WordAnswer>) = Identity(lists.with(answers)).read(BoardLine.read(line).words)

    // The two answers: a word to pass over, and another name for a movement, which then has
    // that movement's key and so its history and weight.
    @Test
    fun anAnswerChangesHowALineReads() {
        assertEquals(Why.UNKNOWN_WORDS, (read("10 x zorbed shoulder press", emptyList()) as Reading.NotSure).why)
        val ignored = listOf(WordAnswer("zorbed"))
        assertEquals((read("10 x shoulder press", emptyList()) as Reading.Known).key, (read("10 x zorbed shoulder press", ignored) as Reading.Known).key)
        val named = listOf(WordAnswer("Head Smasher", "tricep extension"))
        assertEquals((read("12 x tricep extension", emptyList()) as Reading.Known).key, (read("12 x head-smasher", named) as Reading.Known).key)
        // Words around the new name still mean what they mean.
        assertEquals("tricep extension [arms=single]", (read("12 x single arm head smasher", named) as Reading.Known).key)
        assertEquals("single arm tricep extension", WordAnswers.preview("12 x single arm head smasher", named, lists))
        // Another unknown word on the line still leaves it unread.
        assertNull(WordAnswers.preview("12 x frobnicated head smasher", named, lists))
    }

    // The coach's own lists outrank an answer, and an answer that names nothing is dropped.
    @Test
    fun theListsWinAndABadAnswerIsSpent() {
        val over = listOf(WordAnswer("squat", "deadlift"), WordAnswer("goblet"), WordAnswer("zorb", "no such lift"), WordAnswer("  "))
        assertEquals("squat [implement=one dumbbell, both hands]", (read("8 x goblet squat", over) as Reading.Known).key)
        assertEquals(Why.UNKNOWN_WORDS, (read("8 x zorb", over) as Reading.NotSure).why)
        assertEquals(lists.longestPhrase, lists.with(over).longestPhrase)
    }

    // A word the reader settles by its neighbours takes no answer, even one already on file:
    // "press" after a squat is a shoulder press, and one answer would undo that everywhere.
    @Test
    fun aWordReadByItsNeighboursTakesNoAnswer() {
        val plain = (read("8 x squat to press", emptyList()) as Reading.Known).key
        assertEquals("squat + shoulder press", plain)
        for (bad in listOf(WordAnswer("press"), WordAnswer("press", "chest press"), WordAnswer("rotation"), WordAnswer("reach"), WordAnswer("2 x"))) {
            assertEquals(bad.toString(), false, WordAnswers.sortable(bad.word))
            assertEquals(plain, (read("8 x squat to press", listOf(bad)) as Reading.Known).key)
        }
        assertEquals("squat [implement=two dumbbells]", (read("8 x 2 dumbbell squat", listOf(WordAnswer("2 x"))) as Reading.Known).key)
        assertTrue(WordAnswers.sortable("head smasher"))
        assertEquals(false, WordAnswers.sortable("y".repeat(WordAnswers.MAX_CHARS + 1)))
    }

    // What is said back is what will happen, and an answer that would do nothing isn't kept.
    @Test
    fun theSayBackIsWhatTheAnswerDoes() {
        fun say(line: String, answer: WordAnswer) = WordAnswers.sayBack(line, answer, emptyList(), lists)
        assertEquals(
            WordAnswers.SayBack("\"head smasher\" will be read as tricep extension on every board, with its history and weight. This line will read as tricep extension.", true),
            say("12 x head smasher", WordAnswer("head smasher", "tricep extension")),
        )
        val other = say("12 x frobnicated head smasher", WordAnswer("head smasher", "tricep extension"))
        assertEquals(true to true, other.keep to other.text.endsWith("This line stays \"not sure\" until frobnicated is known too."))
        assertEquals("\"zorbed\" will be passed over on every board. This line will read as shoulder press.", say("10 x zorbed shoulder press", WordAnswer("zorbed")).text)
        // The words aren't side by side on the line, so the phrase never matches it.
        assertEquals(false, say("8 x zorbed squat frobnicated", WordAnswer("zorbed frobnicated")).keep)
        // Another name that makes two exercises side by side.
        assertEquals(false, say("8 x zottman curl", WordAnswer("zottman", "bicep curl")).keep)
    }

    // Answers are replaced by word, undone by word, and survive being written and read.
    @Test
    fun answersAreKeptByWord() {
        var answers = WordAnswers.adding(emptyList(), WordAnswer("zorbed"))
        answers = WordAnswers.adding(answers, WordAnswer("Zorbed", "squat"))
        answers = WordAnswers.adding(answers, WordAnswer("head smasher", "tricep extension"))
        assertEquals(listOf(WordAnswer("Zorbed", "squat"), WordAnswer("head smasher", "tricep extension")), answers)
        assertEquals(answers, WordAnswers.parse(WordAnswers.write(answers)))
        assertEquals(listOf(WordAnswer("Zorbed", "squat")), WordAnswers.removing(answers, "HEAD-SMASHER"))
        // A file that isn't one, or is built to be huge, gives no answers and no crash.
        assertTrue(WordAnswers.parse("not json").isEmpty())
        assertTrue(WordAnswers.parse("""{"schema": "other/1", "answers": [{"word": "x"}]}""").isEmpty())
        assertTrue(WordAnswers.parse("x".repeat(1_000_000)).isEmpty())
        val long = WordAnswers.write(listOf(WordAnswer("y".repeat(500)), WordAnswer("ok")))
        assertEquals(listOf(WordAnswer("ok")), WordAnswers.parse(long))
    }
}
