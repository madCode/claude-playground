package spotter.app.words

import spotter.coach.WordAnswer
import spotter.coach.WordAnswers
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * The person's answers about words the lists don't have, kept in the app's own storage. They
 * are laid over the coach's lists each time the app reads a line, so an answer changes every
 * board and every logged row at once, and undoing one changes them back.
 */
class WordAnswerStore(dir: File) {
    private val file = File(dir, "word_answers.json")
    private val tmp = File(dir, "word_answers.json.tmp")

    /** The kept answers; none when there is no file or it can't be read. */
    @Synchronized
    fun load(): List<WordAnswer> = if (!file.isFile) emptyList() else runCatching { WordAnswers.parse(file.readText()) }.getOrDefault(emptyList())

    /** Whole and on disk before it takes the name: half a file would lose every answer. */
    @Synchronized
    fun save(answers: List<WordAnswer>) {
        try {
            FileOutputStream(tmp).use {
                it.write(WordAnswers.write(answers).toByteArray())
                it.fd.sync()
            }
            if (!tmp.renameTo(file)) throw IOException("couldn't keep the answers")
        } finally {
            tmp.delete()
        }
    }
}
