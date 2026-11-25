package spotter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import spotter.app.words.WordAnswerStore
import spotter.coach.WordAnswer
import java.io.File

class WordAnswerStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    // Answers outlive the app, and a damaged file costs the answers, not a crash.
    @Test
    fun answersAreKeptAcrossARestart() {
        val answers = listOf(WordAnswer("zorbed"), WordAnswer("head smasher", "tricep extension"))
        assertTrue(WordAnswerStore(tmp.root).load().isEmpty())
        WordAnswerStore(tmp.root).save(answers)
        assertEquals(answers, WordAnswerStore(tmp.root).load())
        assertEquals(listOf("word_answers.json"), tmp.root.list()!!.toList())
        File(tmp.root, "word_answers.json").writeText("{ not a file")
        assertTrue(WordAnswerStore(tmp.root).load().isEmpty())
    }
}
