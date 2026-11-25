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
