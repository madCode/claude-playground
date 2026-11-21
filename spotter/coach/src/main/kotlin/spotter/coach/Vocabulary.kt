package spotter.coach

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A movement: what is lifted, before any variation. Strength is tracked per movement. */
@Serializable
data class Movement(
    val name: String,
    val aliases: List<String> = emptyList(),
    val pattern: String? = null,
    val muscles: List<String> = emptyList(),
    /** Done on one leg even when the line has no stance word: lunges, step-ups (trap 10). */
    val oneLeg: Boolean = false,
)

/** What a phrase on a board line means. Every phrase means exactly one thing. */
sealed interface Meaning {
    data class OfMovement(val movement: Movement) : Meaning
    /** One value on one axis: grip = close, position = floor. */
    data class Variation(val axis: String, val value: String) : Meaning
    /** Joins two movements: "to", "with" make a combo; "or" a choice. */
    data class Connector(val kind: String) : Meaning
    /** Says how many: "each", "total", "sec". Read by the rep-scheme parser. */
    data object Scheme : Meaning
    /** Carries nothing for identity or load: "rest", "finisher". */
    data object Filler : Meaning
}

/**
 * The coach's starting lists: movements, and the closed lists of words a board line may use
 * around them. A word in none of them leaves a line "not sure" rather than read as something
 * plainer, so the lists are where coverage grows, one reviewed word at a time.
 */
class Vocabulary private constructor(val movements: List<Movement>, private val phrases: Map<String, Meaning>) {

    /** The meaning of [phrase], or null when no list has it. */
    fun lookup(phrase: String): Meaning? = phrases[normalise(phrase)]

    /** The longest phrase any list holds, in words: how far a reader needs to look ahead. */
    val longestPhrase: Int = phrases.keys.maxOf { it.split(' ').size }

    companion object {
        private val json = Json { ignoreUnknownKeys = false }

        /** The lists shipped in the coach's resources. */
        fun starting(): Vocabulary = parse(resource("movements.json"), resource("words.json"))

        /**
         * Builds a vocabulary from the two files' text. Fails when a phrase means two things,
         * since then which one a line meant would come down to the order the lists were read in.
         */
        fun parse(movementsJson: String, wordsJson: String): Vocabulary {
            val movementsFile = json.decodeFromString<MovementsFile>(movementsJson)
            val words = json.decodeFromString<WordsFile>(wordsJson)
            require(movementsFile.schema == "spotter-movements/1") { "unknown schema ${movementsFile.schema}" }
            require(words.schema == "spotter-words/1") { "unknown schema ${words.schema}" }
            val movements = movementsFile.movements
            val phrases = mutableMapOf<String, Meaning>()
            fun add(phrase: String, meaning: Meaning) {
                val key = normalise(phrase)
                require(key.isNotEmpty()) { "an empty phrase in the lists" }
                val earlier = phrases.putIfAbsent(key, meaning)
                require(earlier == null || earlier == meaning) {
                    "\"$phrase\" means two things: $earlier and $meaning"
                }
            }
            for (m in movements) (listOf(m.name) + m.aliases).forEach { add(it, Meaning.OfMovement(m)) }
            for ((axis, values) in words.variations) {
                for ((value, list) in values) list.forEach { add(it, Meaning.Variation(axis, value)) }
            }
            for ((kind, list) in words.connectors) list.forEach { add(it, Meaning.Connector(kind)) }
            words.scheme.forEach { add(it, Meaning.Scheme) }
            words.filler.forEach { add(it, Meaning.Filler) }
            return Vocabulary(movements, phrases)
        }

        /**
         * One spelling per phrase: lower case, hyphens and runs of spaces as one space, and no
         * apostrophes, so "Step-Up", "step up" and "farmers carry" match the lists' spellings.
         */
        fun normalise(text: String): String =
            text.lowercase().replace("'", "").replace("’", "").replace('-', ' ').trim().replace(Regex("\\s+"), " ")

        private fun resource(name: String): String =
            checkNotNull(Vocabulary::class.java.getResource("/spotter/$name")) { "missing resource $name" }.readText()
    }
}

@Serializable
private data class MovementsFile(val schema: String, val movements: List<Movement>)

@Serializable
private data class WordsFile(
    val schema: String,
    val variations: Map<String, Map<String, List<String>>>,
    val connectors: Map<String, List<String>>,
    val filler: List<String>,
    val scheme: List<String>,
)
