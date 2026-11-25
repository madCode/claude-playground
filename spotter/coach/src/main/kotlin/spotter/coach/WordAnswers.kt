package spotter.coach

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the person said a word the lists don't have is (DESIGN.md, "New words go to a review
 * list"): nothing to read, when [movement] is null, or another name for that movement. A word
 * that is neither, a new exercise or a new way of doing one, has no answer here: it waits for
 * the lists themselves.
 */
@Serializable
data class WordAnswer(val word: String, val movement: String? = null)

/** The person's answers, kept on the phone and laid over the coach's own lists. */
object WordAnswers {
    /** More than anyone sorts by hand; a file built to be slow to read stops here. */
    const val MAX = 500

    /** Longer than any word or exercise name on a board. */
    const val MAX_CHARS = 80

    @Serializable
    private data class File(val schema: String, val answers: List<WordAnswer>)

    private const val SCHEMA = "spotter-word-answers/1"
    private val json = Json { ignoreUnknownKeys = true }

    fun write(answers: List<WordAnswer>): String = json.encodeToString(File.serializer(), File(SCHEMA, answers))

    /** The answers in [text]; none when it isn't this file, so a damaged one costs answers, not the app. */
    fun parse(text: String): List<WordAnswer> {
        if (text.length > MAX * MAX_CHARS * 4) return emptyList()
        val file = runCatching { json.decodeFromString(File.serializer(), text) }.getOrNull()?.takeIf { it.schema == SCHEMA } ?: return emptyList()
        return file.answers.filter { it.word.length <= MAX_CHARS && (it.movement?.length ?: 0) <= MAX_CHARS }.take(MAX)
    }

    /** [answers] with [answer] in place of any earlier one for the same word. */
    fun adding(answers: List<WordAnswer>, answer: WordAnswer): List<WordAnswer> =
        (answers.filterNot { same(it.word, answer.word) } + answer).takeLast(MAX)

    fun removing(answers: List<WordAnswer>, word: String): List<WordAnswer> = answers.filterNot { same(it.word, word) }

    private fun same(a: String, b: String) = Vocabulary.normalise(a) == Vocabulary.normalise(b)

    /**
     * Whether [word] can take an answer at all. Not a word the reader settles by its neighbours,
     * nor anything with a number in it ("2 x" is a count, and passing it over would lose the
     * two of "2 x dumbbell"), nor text too long to be a word.
     */
    fun sortable(word: String): Boolean {
        val key = Vocabulary.normalise(word)
        return key.isNotEmpty() && word.length <= MAX_CHARS && key.none { it.isDigit() } && key.split(' ').none { it in Identity.byNeighbours }
    }

    /** What the card says back before an answer is kept, and whether it is worth keeping. */
    data class SayBack(val text: String, val keep: Boolean)

    /**
     * What [answer] would do to [line], the line its word was seen on, with the [answers]
     * already given. Kept only when it changes how that line is read: an answer that does
     * nothing there, or leaves the line unread for another reason, would sit in the list as if
     * it worked.
     */
    fun sayBack(line: String, answer: WordAnswer, answers: List<WordAnswer>, vocabulary: Vocabulary): SayBack {
        val words = BoardLine.read(line).words
        val before = Identity(vocabulary.with(answers)).read(words)
        val after = Identity(vocabulary.with(adding(answers, answer))).read(words)
        val what = if (answer.movement == null) "\"${answer.word}\" will be passed over on every board."
        else "\"${answer.word}\" will be read as ${answer.movement} on every board, with its history and weight."
        return when {
            after is Reading.Known -> SayBack("$what This line will read as ${Estimated.name(after)}.", true)
            after == before -> SayBack("That wouldn't change this line: its words aren't side by side here, or mean something else. Nothing to keep.", false)
            after is Reading.NotSure && after.why == Why.UNKNOWN_WORDS ->
                SayBack("$what This line stays \"not sure\" until ${after.words.joinToString(" ")} is known too.", true)
            else -> SayBack("With that answer this line still couldn't be read as one exercise. Nothing to keep.", false)
        }
    }

    /**
     * How [line] would read with [answers] laid over [vocabulary]: the exercise as a person
     * says it, or null while some other word of the line still isn't known.
     */
    fun preview(line: String, answers: List<WordAnswer>, vocabulary: Vocabulary): String? =
        (Identity(vocabulary.with(answers)).read(BoardLine.read(line).words) as? Reading.Known)?.let(Estimated::name)
}
