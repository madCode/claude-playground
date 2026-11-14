package com.app.carry.brain

/**
 * How a line asks for a movement to be done, beyond which movement it is:
 * bosu or floor, one arm or both, a dumbbell or a band. Ported from
 * gym-coach's `variants.py`, rule for rule; the reasons for each rule are
 * there and in its PLAN.md, and are not repeated here. Field values are the
 * Python ones, so the session file and history read the same either way.
 */
data class Variant(
    val stability: String = "stable",
    val stance: String = "both",
    val arms: String = "both",
    val implement: String? = null,
    val mode: String = "reps",
    val tempo: String = "normal",
    val position: String = "none",
    val difficulty: String = "none",
    val intensity: String = "none",
    val reps: Int? = null,
    val repsText: String? = null,
    val durationSec: Int? = null,
    val grip: String = "none",
    val unknown: List<String> = emptyList(),
)

// Words that carry no variant information; `variants.STRUCTURAL_WORDS`.
internal val STRUCTURAL_WORDS = setOf(
    "x", "total", "each", "ea", "sec", "min", "of", "rest",
    "l", "r", "left", "right", "or", "alt", "alternating",
    "dumbbell", "dumbbells", "with", "count", "aka", "reps",
    "to", "into",
    "the", "a", "an", "and", "is", "are", "until", "time", "called",
    "finisher", "block", "first", "second", "then", "in", "on", "shoulders",
    "feet", "foot", "hands", "hand", "stand",
    "tap",
    "as", "needed", "bonus", "repeat", "for", "increase", "by", "round",
    "complete", "recover", "when", "done", "next", "begins",
)

private val STRUCTURAL_PHRASES = listOf(
    listOf("full", "extension"), listOf("full", "body"), listOf("full", "range"), listOf("lateral", "walk"),
    listOf("check", "reps"), listOf("remember", "reps"), listOf("front", "loaded"),
    listOf("low", "bench"), listOf("low", "band"), listOf("mid", "band"), listOf("mini", "band"),
)

private class Phrase(val words: List<String>, val attr: String, val value: String)

private fun p(vararg words: String, attr: String, value: String) = Phrase(words.toList(), attr, value)

/** `variants._PHRASE_TABLE`, in its order, then sorted longest first (a stable sort, as Python's). */
private val PHRASE_TABLE: List<Phrase> = listOf(
    p("bosu", attr = "stability", value = "bosu"),
    p("bstance", attr = "stance", value = "staggered"),
    p("kickstand", attr = "stance", value = "staggered"),
    p("split", "stance", attr = "stance", value = "split"),
    p("split", "squat", attr = "stance", value = "split"),
    p("split", attr = "stance", value = "split"),
    p("sumo", attr = "stance", value = "sumo"),
    p("wide", attr = "stance", value = "wide"),
    p("single", "leg", attr = "stance", value = "single"),
    p("balance", attr = "stance", value = "single"),
    p("seated", attr = "stance", value = "seated"),
    p("half", "kneeling", attr = "stance", value = "kneeling"),
    p("tall", "kneeling", attr = "stance", value = "kneeling"),
    p("kneeling", attr = "stance", value = "kneeling"),
    p("stepdown", attr = "stance", value = "single"),
    p("step", "down", attr = "stance", value = "single"),
    p("single", "arm", attr = "arms", value = "single"),
    p("singlearm", attr = "arms", value = "single"),
    p("reciprocating", attr = "arms", value = "reciprocating"),
    p("two", "handed", "single", "dumbbell", attr = "implement", value = "single_dumbbell"),
    p("single", "dumbbell", attr = "implement", value = "single_dumbbell"),
    p("double", "dumbbell", attr = "implement", value = "double_dumbbell"),
    p("two", "dumbbells", attr = "implement", value = "double_dumbbell"),
    p("ybell", attr = "implement", value = "ybell"),
    p("y", "bell", attr = "implement", value = "ybell"),
    p("goblet", attr = "implement", value = "single_dumbbell"),
    p("bodyweight", attr = "implement", value = "bodyweight"),
    p("trx", attr = "implement", value = "trx"),
    p("band", attr = "implement", value = "band"),
    p("kettlebell", attr = "implement", value = "kettlebell"),
    p("kb", attr = "implement", value = "kettlebell"),
    p("ybell", attr = "implement", value = "ybell"),
    p("yb", attr = "implement", value = "ybell"),
    p("medicine", "ball", attr = "implement", value = "medball"),
    p("med", "ball", attr = "implement", value = "medball"),
    p("medball", attr = "implement", value = "medball"),
    p("hold", attr = "mode", value = "hold"),
    p("iso", attr = "mode", value = "hold"),
    p("pulsing", attr = "mode", value = "pulse"),
    p("pulse", attr = "mode", value = "pulse"),
    p("with", "power", attr = "tempo", value = "explosive"),
    p("explosive", attr = "tempo", value = "explosive"),
    p("speed", attr = "tempo", value = "explosive"),
    p("stick", "it", attr = "tempo", value = "pause"),
    p("stickit", attr = "tempo", value = "pause"),
    p("slow", attr = "tempo", value = "slow"),
    p("high", "plank", attr = "position", value = "high_plank"),
    p("low", "plank", attr = "position", value = "low_plank"),
    p("bear", "plank", attr = "position", value = "bear_plank"),
    p("plank", attr = "position", value = "plank"),
    p("high", attr = "position", value = "high_plank"),
    p("low", attr = "position", value = "low_plank"),
    p("bear", attr = "position", value = "bear_plank"),
    p("forearm", attr = "position", value = "low_plank"),
    p("elbow", attr = "position", value = "low_plank"),
    p("low", "bench", attr = "position", value = "bench"),
    p("bench", attr = "position", value = "bench"),
    p("on", "the", "floor", attr = "position", value = "floor"),
    p("lying", "on", "bench", attr = "position", value = "lying"),
    p("incline", attr = "position", value = "incline"),
    p("decline", attr = "position", value = "decline"),
    p("floor", attr = "position", value = "floor"),
    p("full", attr = "difficulty", value = "harder"),
    p("lateral", attr = "difficulty", value = "harder"),
    p("stepout", attr = "difficulty", value = "harder"),
    p("step", "out", attr = "difficulty", value = "harder"),
    p("heavier", attr = "intensity", value = "heavy"),
    p("heavy", attr = "intensity", value = "heavy"),
    p("moderate", attr = "intensity", value = "moderate"),
    p("light", attr = "intensity", value = "light"),
    p("hammer", "curl", attr = "grip", value = "hammer"),
    p("neutral", "grip", attr = "grip", value = "neutral"),
    p("close", "grip", attr = "grip", value = "close"),
    p("wide", "grip", attr = "grip", value = "wide"),
    p("reverse", "grip", attr = "grip", value = "reverse"),
).sortedByDescending { it.words.size }

internal val DEFAULTS = mapOf(
    "stability" to "stable", "stance" to "both", "arms" to "both", "mode" to "reps", "tempo" to "normal",
    "position" to "none", "difficulty" to "none", "intensity" to "none", "grip" to "none",
)

private const val LUNGE_DEFAULT_STANCE = "lunge"
internal val SEATED_IS_STANDING_PATTERNS = setOf("horizontal_pull", "triceps")

internal fun stanceFor(stance: String, pattern: String?): String =
    if (stance == "seated" && pattern in SEATED_IS_STANDING_PATTERNS) DEFAULTS.getValue("stance") else stance

private fun isNumber(word: String) = word.isNotEmpty() && word.all { it in '0'..'9' }

/** `variants.variant_key`'s fields after the identity: what "the same exercise" means for streaks. */
fun Variant.keyFields(): List<Any?> = listOf(stability, stance, arms, implement, mode, tempo, position, difficulty, unknown)

// --------------------------------------------------------- alias stripping

/** `variants._canonical_alias_word_lists`: the canonical's own names as word lists, longest first. */
internal fun aliasWordLists(canonical: String, kb: Kb): List<List<String>> =
    (listOf(canonical) + (kb[canonical]?.aliases ?: emptyList()))
        .map { normalize(it).pySplit() }
        .filter { it.isNotEmpty() }
        .sortedByDescending { it.size }

private val TOKEN = Regex("\\S+")

/** `variants._strip_words`: the first alias found loses its own words; filler between them stays. */
internal fun stripWords(normalizedLine: String, wordLists: List<List<String>>): String {
    val tokens = TOKEN.findAll(normalizedLine).toList()
    for (words in wordLists) {
        val m = phrasePattern(words).find(normalizedLine) ?: continue
        val start = m.range.first
        val end = m.range.last + 1
        val inside = tokens.filter { it.range.first in start until end }
        val fixed = mutableSetOf<Int>()
        var ti = inside.size - 1
        for (w in words.asReversed()) {
            val own = Regex("${Regex.escape(w)}s?")
            while (ti >= 0 && !own.matches(inside[ti].value)) ti--
            if (ti >= 0) {
                fixed += ti
                ti--
            }
        }
        val insideSet = inside.toSet()
        val result = mutableListOf<String>()
        var insideI = 0
        for (t in tokens) {
            if (t in insideSet) {
                if (insideI !in fixed) result += t.value
                insideI++
            } else {
                result += t.value
            }
        }
        return result.joinToString(" ")
    }
    return normalizedLine
}

private val CLAUSE_SPLIT = pyRegex("""[.!?;]\s+|\bthen\b""", ignoreCase = true)

/** `variants._scope_to_clauses`. One clause normalizes the line as written, entities and all, as Python does. */
internal fun scopeToClauses(line: String, wordLists: List<List<String>>): String {
    val clauses = CLAUSE_SPLIT.split(htmlUnescape(line)).filter { it.isNotEmpty() && it.pyStrip().isNotEmpty() }
    if (clauses.size < 2) return normalize(line)
    val kept = clauses.map(::normalize).filter { n -> wordLists.any { phrasePattern(it).containsMatchIn(n) } }
    return if (kept.isNotEmpty()) kept.joinToString(" ") else normalize(line)
}

// --------------------------------------------------------------- matching

/** `variants._match_phrases`: (attribute to value, leftover words), phrases consumed longest first. */
internal fun matchPhrases(remaining: String): Pair<Map<String, String>, List<String>> {
    val tokens = remaining.pySplit()
    val consumed = BooleanArray(tokens.size)
    fun consume(words: List<String>): Boolean {
        val n = words.size
        for (i in 0..tokens.size - n) {
            if ((i until i + n).any { consumed[it] }) continue
            if (words.indices.all { j -> Regex("${Regex.escape(words[j])}s?").matches(tokens[i + j]) }) {
                for (j in 0 until n) consumed[i + j] = true
                return true
            }
        }
        return false
    }
    for (phrase in STRUCTURAL_PHRASES) consume(phrase)
    val found = linkedMapOf<String, String>()
    for (phrase in PHRASE_TABLE) {
        val already = found[phrase.attr]
        if (already != null) {
            if (already == phrase.value) consume(phrase.words)
            continue
        }
        if (consume(phrase.words)) found[phrase.attr] = phrase.value
    }
    val leftover = tokens.filterIndexed { i, tok -> !consumed[i] && !isNumber(tok) && tok !in STRUCTURAL_WORDS }
    return found to leftover
}

// ------------------------------------------------------------- entry point

private val WEIGHT_WORDS = setOf("dumbbell", "dumbbells", "weighted")

internal fun build(entry: Exercise?, found: Map<String, String>, leftover: List<String>, canonicalForStance: String?,
                   words: Set<String> = emptySet()): Variant {
    val stanceDefault = if (canonicalForStance != null && LUNGE_DEFAULT_STANCE in canonicalForStance) "split" else DEFAULTS.getValue("stance")
    var implementDefault = entry?.implement
    if (implementDefault == "bodyweight" && words.any { it in WEIGHT_WORDS }) implementDefault = null
    val pattern = entry?.pattern
    return Variant(
        stability = found["stability"] ?: DEFAULTS.getValue("stability"),
        stance = stanceFor(found["stance"] ?: stanceDefault, pattern),
        arms = found["arms"] ?: DEFAULTS.getValue("arms"),
        implement = if ("implement" in found) found["implement"] else implementDefault,
        mode = found["mode"] ?: DEFAULTS.getValue("mode"),
        tempo = found["tempo"] ?: DEFAULTS.getValue("tempo"),
        position = found["position"] ?: DEFAULTS.getValue("position"),
        difficulty = found["difficulty"] ?: DEFAULTS.getValue("difficulty"),
        intensity = found["intensity"] ?: DEFAULTS.getValue("intensity"),
        grip = found["grip"] ?: DEFAULTS.getValue("grip"),
        unknown = leftover,
    )
}

/** `variants.extract`: one movement's line. */
fun extract(line: String, canonical: String, kb: Kb): Variant {
    val wordLists = aliasWordLists(canonical, kb)
    val normalized = scopeToClauses(line, wordLists)
    val remaining = stripWords(normalized, wordLists)
    val (found, leftover) = matchPhrases(remaining)
    return build(kb[canonical], found, leftover, canonical, normalized.pySplit().toSet())
        .copy(reps = prescribedReps(line), repsText = repsText(line), durationSec = prescribedDuration(line))
}

/**
 * `variants.stated_implement`: the implement the line's own words name, or
 * null when it names none and [extract] falls back to the knowledge base's.
 */
fun statedImplement(line: String, canonical: String, kb: Kb): String? {
    val wordLists = aliasWordLists(canonical, kb)
    val (found, _) = matchPhrases(stripWords(scopeToClauses(line, wordLists), wordLists))
    return found["implement"]
}

/** `variants._combo_found`: the attribute words a combo line states, every part's own words stripped first. */
internal fun comboFound(line: String, canonicals: List<String>, kb: Kb): Pair<Map<String, String>, List<String>> {
    val all = canonicals.flatMap { aliasWordLists(it, kb) }
    var remaining = scopeToClauses(line, all)
    for (canonical in canonicals) remaining = stripWords(remaining, aliasWordLists(canonical, kb))
    return matchPhrases(remaining)
}

/** `variants.extract_combo`: the line's attributes apply to every part. */
fun extractCombo(line: String, canonicals: List<String>, kb: Kb): Variant {
    val (found, leftover) = comboFound(line, canonicals, kb)
    val implements = canonicals.mapNotNull { kb[it]?.implement?.takeIf(String::isNotEmpty) }.toSet()
    val implementDefault = implements.singleOrNull()
    val stanceDefault = if (canonicals.any { LUNGE_DEFAULT_STANCE in it }) "split" else DEFAULTS.getValue("stance")
    val patterns = canonicals.map { kb[it]?.pattern }
    val comboPattern = if (patterns.isNotEmpty() && patterns.all { it in SEATED_IS_STANDING_PATTERNS }) patterns[0] else null
    return Variant(
        stability = found["stability"] ?: DEFAULTS.getValue("stability"),
        stance = stanceFor(found["stance"] ?: stanceDefault, comboPattern),
        arms = found["arms"] ?: DEFAULTS.getValue("arms"),
        implement = if ("implement" in found) found["implement"] else implementDefault,
        mode = found["mode"] ?: DEFAULTS.getValue("mode"),
        tempo = found["tempo"] ?: DEFAULTS.getValue("tempo"),
        position = found["position"] ?: DEFAULTS.getValue("position"),
        difficulty = found["difficulty"] ?: DEFAULTS.getValue("difficulty"),
        intensity = found["intensity"] ?: DEFAULTS.getValue("intensity"),
        grip = found["grip"] ?: DEFAULTS.getValue("grip"),
        unknown = leftover,
        reps = prescribedReps(line), repsText = repsText(line), durationSec = prescribedDuration(line),
    )
}

// ------------------------------------------------------ prescribed duration

private val DURATION = Regex("""^(\d+)\s*(sec|secs|second|seconds|min|mins|minute|minutes)\b\s*(.*)$""")
private val NOT_A_MOVEMENT = Regex(
    """^(of\s+)?(recovery|rest|ao row|all out|wr\b|base|push row|row\b|tread\b)""" +
        """|^to transition|^to the (rower|treadmill|floor)|^to (do|complete)\s+\d""",
)
private val LABEL_PREFIX = pyRegex("""^\W*(?:finisher|buy-?in|buy-?out|bonus)\W*:\s*""", ignoreCase = true)
private val TIME_CAP = pyRegex("""^\W*\d+\s*(?:sec|secs|seconds?|min|mins|minutes?)\s+to\s+(?:do|complete)\s+""", ignoreCase = true)

internal fun withoutLabel(line: String) = LABEL_PREFIX.replaceFirst(line, "")
internal fun withoutTimeCap(line: String) = TIME_CAP.replaceFirst(withoutLabel(line), "")

/** `variants.prescribed_duration`: seconds for a line that prescribes time for a movement. */
fun prescribedDuration(line: String): Int? {
    // On normalized text, which is ASCII, so the plain classes match Python's.
    val m = DURATION.find(normalize(withoutLabel(line))) ?: return null
    if (NOT_A_MOVEMENT.find(m.groupValues[3])?.range?.first == 0) return null
    val n = m.groupValues[1].toIntOrNull() ?: return null
    return if (m.groupValues[2].startsWith("min")) n * 60 else n
}

// ---------------------------------------------------------- prescribed reps

/**
 * `variants.prescribed_reps`. A count past an Int's range reads as no count;
 * Python's ints have no ceiling, so that one board-sized absurdity is the
 * only place the two can differ.
 */
fun prescribedReps(line: String, opener: Boolean = false): Int? {
    val tokens = normalize(withoutTimeCap(line)).pySplit()
    var i = 0
    val nums = mutableListOf<Int>()
    while (i < tokens.size && isNumber(tokens[i])) {
        nums += tokens[i].toIntOrNull() ?: return null
        i++
    }
    if (nums.isEmpty()) return null
    val rest = tokens.subList(i, minOf(i + 2, tokens.size))
    if (rest == listOf("sec", "of") || rest == listOf("min", "of")) return null
    if (rest.getOrNull(0) == "each" && rest.getOrNull(1) == "x") return nums[0]
    if (rest.getOrNull(0) == "total" && rest.getOrNull(1) == "x") {
        val alt = "alt" in tokens || "alternating" in tokens
        return if (alt) Math.floorDiv(nums[0], 2) else nums[0]
    }
    if (rest.getOrNull(0) == "x") return if (opener) nums[0] else nums.min()
    return null
}

private val RANGE = pyRegex("""^\W*\d+\s*-\s*\d+""")
private val LADDER = pyRegex("""^\W*\d+(?:\s*[/,]\s*\d+)+(?!\d)""")

/** `variants.reps_text`: the rep scheme as written, "8", "6 total", "10 each", "1/2/3/4/5". */
fun repsText(line: String): String? {
    if (prescribedReps(line) == null) return null
    val cut = withoutTimeCap(line)
    val tokens = normalize(cut).pySplit()
    val nums = tokens.takeWhile(::isNumber)
    val rest = tokens.getOrNull(nums.size)
    val joiner = if (nums.size == 2 && RANGE.find(cut)?.range?.first == 0) "-" else "/"
    val scheme = nums.joinToString(joiner)
    return if (rest == "total" || rest == "each") "$scheme $rest" else scheme
}

/** `variants.prescribed_rounds`: a ladder's rungs in the board's order, or null. */
fun prescribedRounds(line: String): List<Int>? {
    val text = repsText(line) ?: return null
    if ('/' !in text || LADDER.find(withoutTimeCap(line))?.range?.first != 0) return null
    return text.pySplit()[0].split("/").map { it.toInt() }
}

// ------------------------------------------------------------- describing

private val DESCRIBE_ATTRS = listOf("stability", "stance", "arms", "mode", "tempo", "position", "difficulty", "intensity", "grip")
private val WORDS = mapOf(
    "stability" to mapOf("stable" to "no bosu"),
    "stance" to mapOf("both" to "both legs", "split" to "split stance", "single" to "single leg", "sumo" to "sumo stance", "wide" to "wide stance"),
    "arms" to mapOf("both" to "both arms", "single" to "single arm"),
    "tempo" to mapOf("normal" to "normal tempo"),
    "grip" to mapOf("neutral" to "neutral grip", "close" to "close grip", "wide" to "wide grip", "reverse" to "reverse grip"),
    "difficulty" to mapOf("none" to "standard version", "harder" to "harder version"),
)
private val CONTRAST_DEFAULTS = setOf("position", "intensity", "grip")

internal fun Variant.attr(name: String): String? = when (name) {
    "stability" -> stability; "stance" -> stance; "arms" -> arms; "implement" -> implement; "mode" -> mode
    "tempo" -> tempo; "position" -> position; "difficulty" -> difficulty; "intensity" -> intensity; "grip" -> grip
    else -> error(name)
}

/** `variants.word_for`: how one attribute value reads; a default against [contrast] reads "not <contrast>". */
internal fun wordFor(attr: String, value: String, contrast: String? = null): String {
    if (attr in CONTRAST_DEFAULTS && value == DEFAULTS[attr] && contrast != null && contrast != value) {
        return "not ${wordFor(attr, contrast)}"
    }
    return WORDS[attr]?.get(value) ?: value
}

/** `variants.describe`: the attributes that differ from the plain movement, as words, in a fixed order. */
fun describe(variant: Variant, canonical: String?): List<String> {
    val defaults = DEFAULTS.toMutableMap()
    if (canonical != null && LUNGE_DEFAULT_STANCE in canonical) defaults["stance"] = "split"
    return DESCRIBE_ATTRS.filter { variant.attr(it) != defaults[it] }.map { wordFor(it, variant.attr(it)!!) }
}

/** `variants.duration_text`: "45 sec", "1 min". */
fun durationText(seconds: Int?): String? {
    if (seconds == null || seconds == 0) return null
    return if (seconds % 60 == 0) "${seconds / 60} min" else "$seconds sec"
}

/** `gym_lib.variant_label`: the name(s) and the words that differ from the plain movement. */
fun variantLabel(canonical: String?, components: List<String>?, variant: Variant, withReps: Boolean = false): String {
    var name = if (!components.isNullOrEmpty()) components.joinToString(" + ") else (canonical ?: "")
    val duration = durationText(variant.durationSec)
    if (withReps && duration != null) {
        name = "$duration of $name"
    } else if (withReps && (!variant.repsText.isNullOrEmpty() || (variant.reps ?: 0) != 0)) {
        name = "${variant.repsText?.takeIf { it.isNotEmpty() } ?: variant.reps} x $name"
    }
    val words = describe(variant, canonical)
    return if (words.isNotEmpty()) "$name \u00b7 ${words.joinToString(", ")}" else name
}
