package com.app.carry.brain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The comparison half of gym-coach's `variants.py`: whether a set logged at
 * one setup says anything about today's, and in which direction. "more"
 * means today's setup should take more weight than the logged one, "less"
 * less, "same" the same, and "cant_say" that the two are not comparable.
 * The reasons for each rule are in `variants.py` and its PLAN.md.
 */

private val STANCE_ORDER = mapOf("both" to 3, "staggered" to 2, "split" to 1, "single" to 0)
private val TEMPO_NON_NORMAL = setOf("slow", "explosive", "pause")

/** `variants.LOWER_BODY_PATTERNS`: where stance limits the load. */
internal val LOWER_BODY_PATTERNS = setOf("squat", "lunge", "hinge")

private fun compareStability(target: String, logged: String): String = when {
    target == logged -> "same"
    target == "bosu" && logged == "stable" -> "less"
    target == "stable" && logged == "bosu" -> "more"
    else -> "cant_say"
}

private fun compareStance(target: String, logged: String, pattern: String?): String {
    if (target == logged) return "same"
    if (pattern != null && pattern !in LOWER_BODY_PATTERNS && "single" != target && "single" != logged) return "cant_say"
    if (target in setOf("seated", "kneeling") || logged in setOf("seated", "kneeling")) return "cant_say"
    val to = STANCE_ORDER[target] ?: return "cant_say"
    val lo = STANCE_ORDER[logged] ?: return "cant_say"
    return if (to > lo) "more" else "less"
}

private fun compareMustMatch(target: Any?, logged: Any?) = if (target == logged) "same" else "cant_say"

private fun compareTempo(target: String, logged: String): String = when {
    target == logged -> "same"
    target == "normal" && logged in TEMPO_NON_NORMAL -> "more"
    target in TEMPO_NON_NORMAL && logged == "normal" -> "less"
    else -> "cant_say"
}

private fun compareDifficulty(target: String, logged: String): String {
    val order = mapOf("none" to 0, "harder" to 1)
    val to = order[target] ?: return "cant_say"
    val lo = order[logged] ?: return "cant_say"
    if (to == lo) return "same"
    return if (lo > to) "more" else "less"
}

private val INTENSITY_ORDER = mapOf("none" to 0, "light" to 1, "moderate" to 2, "heavy" to 3)

private fun compareIntensity(target: String, logged: String): String {
    if (target == "none" || logged == "none") return "same"
    // Python indexes the table and raises on a value outside it; only the table's own values reach here.
    val to = INTENSITY_ORDER.getValue(target)
    val lo = INTENSITY_ORDER.getValue(logged)
    if (to == lo) return "same"
    return if (to > lo) "more" else "less"
}

private fun compareReps(target: Int?, logged: Int?): String = when {
    target == null && logged == null -> "same"
    target == null || logged == null -> "cant_say"
    target == logged -> "same"
    else -> if (target < logged) "more" else "less"
}

/** `variants.compare`: every attribute but grip must agree (same, or one direction) for a verdict. */
fun compare(target: Variant, logged: Variant, pattern: String? = null): String {
    if (target.unknown != logged.unknown) return "cant_say"
    val directions = mutableSetOf(
        compareStability(target.stability, logged.stability),
        compareStance(target.stance, logged.stance, pattern),
        compareMustMatch(target.arms, logged.arms),
        compareMustMatch(target.implement, logged.implement),
        compareMustMatch(target.mode, logged.mode),
        compareTempo(target.tempo, logged.tempo),
        compareMustMatch(target.position, logged.position),
        compareDifficulty(target.difficulty, logged.difficulty),
        compareIntensity(target.intensity, logged.intensity),
        compareReps(target.reps, logged.reps),
    )
    if ("cant_say" in directions) return "cant_say"
    directions.remove("same")
    return when (directions.size) {
        0 -> "same"
        1 -> directions.single()
        else -> "cant_say"
    }
}

/** `variants.same_variation`: every attribute but reps, intensity, difficulty and grip matches. */
fun sameVariation(target: Variant, logged: Variant): Boolean =
    target.unknown == logged.unknown && target.stability == logged.stability && target.stance == logged.stance &&
        target.arms == logged.arms && target.implement == logged.implement && target.mode == logged.mode &&
        target.tempo == logged.tempo && target.position == logged.position

/**
 * `variants.variant_from_dict`: a stored variant read back. No dict at all
 * is an entry from before variants were stored, and reads as the knowledge
 * base's defaults; stance and unknown words are re-derived on every read.
 * A key stored as null reads as its default here, where Python would keep
 * the None; nothing the coach writes stores one.
 */
internal fun variantFromDict(data: JsonObject?, canonical: String?, kb: Kb): Variant {
    val entry = canonical?.let { kb[it] }
    val pattern = entry?.pattern
    if (data == null) {
        val stanceDefault = if (canonical != null && "lunge" in canonical) "split" else DEFAULTS.getValue("stance")
        return Variant(stance = stanceDefault, implement = entry?.implement)
    }
    fun s(key: String) = data.str(key) ?: DEFAULTS.getValue(key)
    return Variant(
        stability = s("stability"),
        stance = stanceFor(s("stance"), pattern),
        arms = s("arms"),
        implement = data.str("implement"),
        mode = s("mode"),
        tempo = s("tempo"),
        position = s("position"),
        difficulty = s("difficulty"),
        intensity = s("intensity"),
        reps = data.int("reps"),
        repsText = data.str("reps_text"),
        durationSec = data.int("duration_sec"),
        grip = s("grip"),
        unknown = (data.strings("unknown") ?: emptyList()).filter { it !in STRUCTURAL_WORDS && !(it.isNotEmpty() && it.all { c -> c in '0'..'9' }) },
    )
}

/** `dataclasses.asdict(variant)`, as the session file and history store it. */
internal fun variantJson(v: Variant): JsonObject {
    fun s(x: String?): JsonElement = x?.let(::JsonPrimitive) ?: JsonNull
    fun i(x: Int?): JsonElement = x?.let(::JsonPrimitive) ?: JsonNull
    return JsonObject(mapOf(
        "stability" to s(v.stability), "stance" to s(v.stance), "arms" to s(v.arms), "implement" to s(v.implement),
        "mode" to s(v.mode), "tempo" to s(v.tempo), "position" to s(v.position), "difficulty" to s(v.difficulty),
        "intensity" to s(v.intensity), "reps" to i(v.reps), "reps_text" to s(v.repsText),
        "duration_sec" to i(v.durationSec), "grip" to s(v.grip), "unknown" to JsonArray(v.unknown.map(::JsonPrimitive)),
    ))
}

private val COMBO_LOWER_PATTERNS = setOf("squat", "lunge", "hinge", "calf")
private val COMBO_UPPER_PATTERNS = setOf(
    "vertical_push", "horizontal_push", "vertical_pull", "horizontal_pull", "curl", "triceps", "fly", "raise", "pullover",
)

/**
 * `variants.combo_part_variant`: the setup one part of a combo was done at.
 * The combo's attributes, with stance and stability the line's for a
 * lower-body part and the part's own defaults for an upper-body one; null
 * for a part in neither group.
 */
internal fun comboPartVariant(line: String, canonicals: List<String>, part: String, kb: Kb): Variant? {
    val entry = kb[part]
    val pattern = entry?.pattern
    if (pattern !in COMBO_LOWER_PATTERNS && pattern !in COMBO_UPPER_PATTERNS) return null
    val combo = extractCombo(line, canonicals, kb)
    val own = build(entry, emptyMap(), emptyList(), part)
    return if (pattern in COMBO_LOWER_PATTERNS) {
        val (found, _) = comboFound(line, canonicals, kb)
        combo.copy(stance = stanceFor(found["stance"] ?: own.stance, pattern), stability = found["stability"] ?: own.stability)
    } else {
        combo.copy(stance = own.stance, stability = own.stability)
    }
}

/** `variants.easier_option`: a named easier version of the line for a struggle note, or null. */
internal fun easierOption(variant: Variant, pattern: String?): String? {
    if (variant.stability == "bosu") return "off the bosu"
    when (pattern) {
        "hinge" -> {
            if (variant.stance == "single") return "with a kickstand stance"
            if (variant.stance in setOf("split", "staggered")) return "with feet hip-width apart"
        }
        "squat" -> {
            if (variant.stance == "single") return "with both feet down"
            if (variant.stance in setOf("split", "staggered")) return "with feet side by side"
        }
        "lunge" -> return null
        else -> if (variant.stance == "single") return "with both feet down"
    }
    return null
}
