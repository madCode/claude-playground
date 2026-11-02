package com.app.carry.brain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** One movement the coach knows: `gym_lib.Exercise`. */
data class Exercise(
    val name: String,
    val muscleGroups: List<String> = emptyList(),
    val implement: String? = null,
    val aliases: List<String> = emptyList(),
    val pattern: String? = null,
    val held: Boolean = false,
)

/**
 * The knowledge base, in the file's order: aliases of equal length are tried
 * in that order, so it decides which of two movements a line names.
 */
class Kb(val exercises: LinkedHashMap<String, Exercise>) {
    operator fun get(name: String): Exercise? = exercises[name]

    /** `gym_lib._alias_patterns`: every name and alias, most words first, file order within a length. */
    internal val aliasPatterns: List<Triple<Regex, String, Int>> by lazy {
        exercises.values.flatMap { e ->
            (listOf(e.name) + e.aliases).mapNotNull { alias ->
                val words = normalize(alias).pySplit()
                if (words.isEmpty()) null else Triple(phrasePattern(words), e.name, words.size)
            }
        }.sortedByDescending { it.third }
    }

    companion object {
        /** The knowledge base as gym-coach's YAML loader reads it, written out as JSON. */
        fun fromJson(text: String): Kb {
            val root = Json.parseToJsonElement(text) as JsonObject
            val out = LinkedHashMap<String, Exercise>()
            for ((name, value) in root) {
                val e = value as? JsonObject ?: JsonObject(emptyMap())
                fun strings(key: String) = (e[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
                fun string(key: String) = (e[key] as? JsonPrimitive)?.contentOrNull
                out[name] = Exercise(
                    name = name, muscleGroups = strings("muscle_groups"), implement = string("implement"),
                    aliases = strings("aliases"), pattern = string("pattern"),
                    held = (e["held"] as? JsonPrimitive)?.booleanOrNull ?: false,
                )
            }
            return Kb(out)
        }
    }
}

/** One board line the coach recognised: `gym_lib.MatchedLine`. Combos have [components], sorted, and no [canonical]. */
data class MatchedLine(
    val line: String,
    val canonical: String?,
    val components: List<String>?,
    val variant: Variant,
    val prescribedReps: Int?,
)

/** `gym_lib.MatchResult`: the rows, and the raw lines that matched nothing. */
data class MatchResult(val matched: List<MatchedLine>, val unmatched: List<String>)

/** `gym_lib._find_alias_match`: the first (most specific) alias found, and where. */
internal fun Kb.findAlias(normalized: String): Pair<String, MatchResult0>? {
    for ((pattern, canonical, _) in aliasPatterns) {
        val m = pattern.find(normalized) ?: continue
        return canonical to MatchResult0(m.range.first, m.range.last + 1)
    }
    return null
}

/** A match's span, end exclusive, as Python's `start()` and `end()`. */
internal data class MatchResult0(val start: Int, val end: Int)

private val COMBO_SEP = Regex("""\b(?:to|with|into|and)\b""")

private fun separators(normalized: String) = COMBO_SEP.findAll(normalized).toList()

/** `gym_lib._covers_all_separators`. */
private fun coversAllSeparators(normalized: String, m: MatchResult0): Boolean =
    separators(normalized).all { it.range.first >= m.start && it.range.first < m.end }

/** `gym_lib._separator_is_filler`: a separator inside the match that isn't one of the alias's own words. */
private fun Kb.separatorIsFiller(normalized: String, m: MatchResult0, canonical: String): Boolean {
    val own = aliasWordLists(canonical, this).flatten().toSet()
    return separators(normalized).any { it.range.first >= m.start && it.range.first < m.end && it.value !in own }
}

/** `gym_lib._split_outside_aliases`: split on the separators that are not inside a movement's own name. */
private fun Kb.splitOutsideAliases(normalized: String): List<String> {
    val protected = mutableListOf<IntRange>()
    for ((pattern, canonical, _) in aliasPatterns) {
        for (m in pattern.findAll(normalized)) {
            val span = MatchResult0(m.range.first, m.range.last + 1)
            if (!separatorIsFiller(normalized, span, canonical)) protected += span.start until span.end
        }
    }
    val cuts = separators(normalized).filter { s ->
        protected.none { p -> p.first <= s.range.first && s.range.last + 1 <= p.last + 1 }
    }
    val out = mutableListOf<String>()
    var last = 0
    for (c in cuts) {
        out += normalized.substring(last, c.range.first)
        last = c.range.last + 1
    }
    out += normalized.substring(last)
    return out.map { it.pyStrip() }.filter { it.isNotEmpty() }
}

/** `gym_lib._split_combo`: sorted distinct movements when two or more segments each name one. */
private fun Kb.splitCombo(normalized: String): List<String>? {
    val segments = splitOutsideAliases(normalized)
    if (segments.size < 2) return null
    val distinct = segments.mapNotNull { findAlias(it)?.first }.toSortedSet().toList()
    return if (distinct.size >= 2) distinct else null
}

/** `gym_lib._match_line_variant`: one stripped, non-empty line, or null when nothing in the knowledge base is in it. */
fun Kb.matchLine(line: String): MatchedLine? {
    val normalized = normalize(line)
    if (normalized.isEmpty()) return null
    val found = findAlias(normalized)
    val hasSeparator = COMBO_SEP.containsMatchIn(normalized)
    if (hasSeparator && !(found != null && coversAllSeparators(normalized, found.second) &&
            !separatorIsFiller(normalized, found.second, found.first))
    ) {
        val combo = splitCombo(normalized)
        if (combo != null) {
            val v = extractCombo(line, combo, this)
            return MatchedLine(line, null, combo, v, v.reps)
        }
    }
    if (found != null) {
        val v = extract(line, found.first, this)
        return MatchedLine(line, found.first, null, v, v.reps)
    }
    return null
}

private val STRUCTURED_LINE = pyRegex("""^\s*(?:[*\-•]|~?\d)""")
private val NARRATIVE_LEAD = pyRegex("""^\s*~?\d+\s*blocks?\b""", ignoreCase = true)

/** `gym_lib._is_structured`: a line laid out as a prescription, not a paragraph opening with "2 blocks". */
fun isStructured(rawLine: String): Boolean =
    STRUCTURED_LINE.find(rawLine)?.range?.first == 0 && NARRATIVE_LEAD.find(rawLine)?.range?.first != 0

private val INTENSITY_RANK = mapOf("none" to 0, "light" to 1, "moderate" to 2, "heavy" to 3)

/** `gym_lib.match_wod`: a board's text to its rows, deduplicated as the Python does. */
fun Kb.matchWod(text: String): MatchResult {
    val unmatched = mutableListOf<String>()
    var candidates = mutableListOf<Pair<MatchedLine, Boolean>>()
    for (raw in text.pySplitLines()) {
        val line = raw.pyStrip().trimStart('-', '*', '•').pyStrip()
        if (line.isEmpty()) continue
        val ml = matchLine(line)
        if (ml == null) {
            unmatched += line
            continue
        }
        candidates += ml to isStructured(raw)
    }
    if (candidates.any { it.second }) candidates = candidates.filter { it.second }.toMutableList()
    val byKey = LinkedHashMap<List<Any?>, MatchedLine>()
    for ((ml, _) in candidates) {
        val key = variantKey(ml.canonical, ml.components, ml.variant)
        val existing = byKey[key]
        if (existing == null) {
            byKey[key] = ml
        } else if ((INTENSITY_RANK[ml.variant.intensity] ?: 0) > (INTENSITY_RANK[existing.variant.intensity] ?: 0)) {
            byKey[key] = ml
        }
    }
    val byLabel = LinkedHashMap<Pair<String, String?>, MatchedLine>()
    for (ml in byKey.values) {
        val label = variantLabel(ml.canonical, ml.components, ml.variant) to ml.variant.implement
        val kept = byLabel[label]
        if (kept == null || rank(ml) < rank(kept)) byLabel[label] = ml
    }
    return MatchResult(byLabel.values.toList(), unmatched)
}

/** Python compares the tuple (reps is None, len(unknown)): False before True, then fewer unknown words. */
private fun rank(ml: MatchedLine) = (if (ml.variant.reps == null) 1 else 0) * 1_000_000 + ml.variant.unknown.size


/** `variants.variant_key`: the identity (canonical, or sorted parts) and the key fields. */
fun variantKey(canonical: String?, components: List<String>?, variant: Variant): List<Any?> =
    listOf(if (components != null) components.sorted() else listOf(canonical)) + variant.keyFields()

private val PRESCRIBED_LINE = pyRegex("""^\d+\s*(?:total\s*)?x\s+(.*)$""", ignoreCase = true)
private val TRAILING_REST = pyRegex(""",\s*rest\.?$""", ignoreCase = true)

/** `gym_lib.untracked_lines`: prescribed sets nothing matched, cleaned for the sheet. */
fun untrackedLines(unmatched: List<String>): List<String> {
    val out = mutableListOf<String>()
    for (line in unmatched) {
        var text = line.pyStrip().trimStart('*').pyStrip()
        text = TRAILING_REST.replace(text, "").pyStrip()
        if (PRESCRIBED_LINE.find(text)?.range?.first == 0 && text !in out) out += text
    }
    return out
}

/**
 * `session_file.stable_key`: the key as one string a client groups by, as
 * Python's `json.dumps(key, separators=(",", ":"))` writes it, non-ASCII
 * escaped the way its default `ensure_ascii` does.
 */
fun stableKey(canonical: String?, components: List<String>?, variant: Variant): String {
    fun enc(x: Any?): kotlinx.serialization.json.JsonElement = when (x) {
        null -> kotlinx.serialization.json.JsonNull
        is String -> JsonPrimitive(x)
        is List<*> -> JsonArray(x.map(::enc))
        else -> error("not a key part: $x")
    }
    val text = Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), enc(variantKey(canonical, components, variant)))
    return buildString {
        for (c in text) if (c.code > 0x7f) append("\\u%04x".format(c.code)) else append(c)
    }
}
