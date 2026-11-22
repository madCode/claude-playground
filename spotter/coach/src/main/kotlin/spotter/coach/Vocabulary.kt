package spotter.coach

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    /**
     * A variation only on the movements named: "wide" is a grip on a bicep curl, and on a squat
     * a stance no list settles. On any other movement the phrase is an unknown word.
     */
    data class Scoped(val byMovement: Map<String, Variation>) : Meaning
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
    val longestPhrase: Int = phrases.keys.maxOfOrNull { it.split(' ').size } ?: 0

    companion object {
        private val json = Json { ignoreUnknownKeys = false }

        /** The lists shipped in the coach's resources. */
        fun starting(): Vocabulary = parse(resource("movements.json"), resource("words.json"))

        /**
         * Builds a vocabulary from the two files' text. Fails when a phrase means two things,
         * since then which one a line meant would come down to the order the lists were read in.
         */
        fun parse(movementsJson: String, wordsJson: String): Vocabulary {
            // The schema first, so a newer file says so instead of failing on a field it added.
            requireSchema(movementsJson, "spotter-movements/1")
            requireSchema(wordsJson, "spotter-words/1")
            val movements = json.decodeFromString<MovementsFile>(movementsJson).movements
            val words = json.decodeFromString<WordsFile>(wordsJson)
            val names = movements.map { normalise(it.name) }
            require(names.size == names.toSet().size) { "a movement is listed twice" }
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
            for ((phrase, byMovement) in words.scoped) {
                val resolved = byMovement.mapValues { (name, axisValue) ->
                    require(names.contains(normalise(name))) { "\"$phrase\" is scoped to $name, which no list has" }
                    require(axisValue.size == 2) { "\"$phrase\" on $name needs an axis and a value" }
                    Meaning.Variation(axisValue[0], axisValue[1])
                }
                add(phrase, Meaning.Scoped(resolved))
            }
            return Vocabulary(movements, phrases)
        }

        /**
         * One spelling per phrase: lower case, "&amp;" as "&", no apostrophes or invisible
         * characters, and every dash and run of spaces as one space, so "Step-Up", "step up" and
         * "farmers carry" match the lists' spellings. Boards carry word joiners and HTML entities.
         */
        fun normalise(text: String): String =
            normaliseText(text).replace("'", "").replace('-', ' ').replace(Regex("\\s+"), " ").trim()

        /**
         * Board text as the line reader takes it: lower case, "&amp;" as "&", no invisible
         * characters, every dash as "-" and every kind of space as one space. Hyphens stay, since
         * "6 - 10" is a range; [normalise] then folds them for looking words up.
         */
        fun normaliseText(text: String): String =
            text.lowercase()
                .replace("&amp;", "&")
                .replace("’", "'")
                .replace(Regex("[\\u200b\\u200c\\u200d\\u2060\\ufeff]"), "")
                .replace(Regex("[\\u2010\\u2011\\u2012\\u2013\\u2014]"), "-")
                .replace(Regex("[\\s\\u00a0]+"), " ")
                .trim()

        private fun requireSchema(text: String, expected: String) {
            val schema = runCatching { json.parseToJsonElement(text).jsonObject["schema"]?.jsonPrimitive?.content }.getOrNull()
            require(schema == expected) { "unknown schema $schema, expected $expected" }
        }

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
    val scoped: Map<String, Map<String, List<String>>> = emptyMap(),
)
