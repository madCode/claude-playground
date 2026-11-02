package com.app.carry.brain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The session file, both ways: gym-coach's `session_file.build`, the sheet
 * the coach writes and Carry fills in, and `session_file.to_history`, the
 * history rows a filled sheet becomes. The schema belongs to the Python
 * until it is retired; field names and meanings are its own.
 */

internal const val SESSION_SCHEMA = "gym-session/1"

/** `session_file.FAMILIES`: Carry's colour for a row, by the knowledge base's muscle groups. */
private val FAMILIES = linkedMapOf(
    "legs" to setOf("legs", "glutes", "hamstrings", "inner thigh", "posterior chain"),
    "push" to setOf("chest", "shoulders", "triceps"),
    "pull" to setOf("back", "biceps", "lower back", "grip"),
    "core" to setOf("core", "obliques"),
)

/** `session_file.family_of`: legs, push, pull, core, combo when it spans several, or null. */
internal fun familyOf(muscleGroups: List<String>?): String? {
    val hit = FAMILIES.filter { (_, groups) -> (muscleGroups ?: emptyList()).any { it in groups } }.keys
    if (hit.isEmpty()) return null
    return if (hit.size == 1) hit.single() else "combo"
}

/** `session_file._unlabelled`: the related line without its "related:" label. */
internal fun unlabelled(line: String?): String? {
    if (line.isNullOrEmpty()) return null
    val label = "related:"
    return if (line.startsWith(label)) line.substring(label.length).pyStrip() else line
}

/** `session_file.sheet_id`. */
internal fun sheetId(date: String, classTag: String) = "$date:$classTag"

/**
 * `session_file.build`: the file the runner writes, each set carrying the
 * identity the coach computed and the numbers behind its sentence, with
 * the owner's fields blank. [date] is ISO; no start time is written, as
 * the Python leaves one out when the calendar gives none.
 */
fun buildSheet(
    date: String, classTag: String, suggestions: List<Pair<MatchedLine, Suggestion>>,
    borrowedFrom: String? = null,
    /** The class's start, local time to the minute ("2026-01-10T09:50"); left out when the calendar gave none. */
    startsAt: String? = null,
): JsonObject {
    val sets = suggestions.mapIndexed { ref, (ml, sg) ->
        JsonObject(mapOf(
            "ref" to JsonPrimitive(ref),
            "key" to JsonPrimitive(stableKey(ml.canonical, ml.components, ml.variant)),
            "line" to JsonPrimitive(ml.line),
            "canonical" to json(ml.canonical),
            "components" to json(ml.components),
            "variant" to variantJson(ml.variant),
            "prescribed_reps" to json(ml.prescribedReps),
            "prescribed_text" to json(ml.variant.repsText),
            "rounds" to (prescribedRounds(ml.line)?.let { r -> JsonArray(r.map(::JsonPrimitive)) } ?: JsonNull),
            "prescribed_duration_sec" to json(ml.variant.durationSec),
            "family" to json(familyOf(sg.muscleGroups)),
            "suggested" to JsonPrimitive(sg.head),
            "suggested_weight" to json(sg.weight),
            "related" to json(unlabelled(sg.related)),
            "claims" to (sg.claims.takeIf { it.isNotEmpty() }?.let(::json) ?: JsonNull),
            "pattern_note" to json(sg.patternNote),
            "miss_note" to json(sg.missNote),
            "previous_weight" to json(sg.previousWeight),
            "streak_done" to json(sg.streakDone),
            "streak_needed" to json(sg.streakNeeded),
            "weight" to JsonNull,
            "reps" to JsonNull,
            "easy" to json(false),
            "hard" to json(false),
            "easier" to json(false),
            "hurt" to json(false),
            "skipped" to json(false),
            "note" to JsonNull,
        ))
    }
    return JsonObject(mapOf(
        "schema" to JsonPrimitive(SESSION_SCHEMA),
        "sheet_id" to JsonPrimitive(sheetId(date, classTag)),
        "date" to JsonPrimitive(date),
        "class" to JsonPrimitive(classTag),
        "borrowed_from" to json(borrowedFrom),
        "sets" to JsonArray(sets),
        "added" to JsonArray(emptyList()),
    ) + (startsAt?.let { mapOf("starts_at" to JsonPrimitive(it)) } ?: emptyMap()))
}

private val FEEL_MARKERS = listOf("easy", "hard", "easier")
private const val DONE = "done"

/** `session_file._answered`: a row the owner filled in. `hurt` alone does not count. */
internal fun answered(s: JsonObject): Boolean =
    s.el("weight") != null || s.el("reps") != null || s.flag("skipped") || s.flag(DONE) ||
        FEEL_MARKERS.any { s.flag(it) } || pyStr(s.el("note")).pyStrip().isNotEmpty()

/** `str(x or "")`. */
private fun pyStr(e: JsonElement?): String = if (truthy(e)) pyStrOf(e) else ""

/** `session_file._variant_for`: the row's setup, minus a no-weight implement its own weight contradicts. */
internal fun variantFor(s: JsonObject): JsonElement {
    val variant = s["variant"] ?: JsonNull
    val weight = s.num("weight")
    if (variant !is JsonObject || weight == null || weight == 0.0) return variant
    if (variant.str("implement") !in NO_WEIGHT_IMPLEMENTS) return variant
    return variant.with("implement" to JsonNull)
}

private fun JsonObject.raw(key: String): JsonElement = this[key] ?: JsonNull

/** `x or None`. */
private fun orNull(e: JsonElement?): JsonElement = if (truthy(e)) e!! else JsonNull

/**
 * `session_file.to_history`: (history rows, notes worth printing). A blank
 * row produces nothing; a backfilled file logs only its added rows; a
 * hand-typed row is matched against the knowledge base and flagged when
 * nothing matches, never guessed.
 */
fun toHistory(obj: JsonObject, kb: Kb): Pair<List<JsonObject>, List<String>> {
    val backfilled = obj.flag("backfilled")
    val entries = mutableListOf<JsonObject>()
    val notes = mutableListOf<String>()
    val sid = (obj["sheet_id"] as JsonPrimitive).content
    if (backfilled) {
        notes += "$sid is a backfilled record: its sets are already in history, so only rows added in the app are logged"
    }
    val common = linkedMapOf(
        "date" to obj.raw("date"), "class" to obj.raw("class"),
        "source" to JsonPrimitive("form"), "form_id" to JsonPrimitive(sid),
    )
    if (obj.flag("borrowed_from")) common["borrowed_from"] = obj.raw("borrowed_from")

    val sets = if (backfilled) emptyList() else (obj.el("sets") as? JsonArray)?.map { it as JsonObject } ?: emptyList()
    for (s in sets) {
        if (!answered(s)) continue
        val reps = s.num("reps")
        val prescribed = s.num("prescribed_reps")
        entries += JsonObject(common + mapOf(
            "row_id" to JsonPrimitive("$sid#${pyStrOf(s["ref"])}"),
            "exercise" to s.raw("canonical"),
            "components" to s.raw("components"),
            "line" to s.raw("line"),
            "variant" to variantFor(s),
            "prescribed_reps" to s.raw("prescribed_reps"),
            "weight" to s.raw("weight"),
            "reps" to s.raw("reps"),
            "skipped" to json(s.flag("skipped")),
            "easy" to json(s.flag("easy")),
            "hard" to json(s.flag("hard")),
            "hurt" to json(s.flag("hurt")),
            "done" to json(s.flag(DONE)),
            "easier" to json(s.flag("easier")),
            "incomplete" to json(reps != null && prescribed != null && reps < prescribed),
            "breakdown" to s.raw("breakdown"),
            "note" to orNull(s.el("note")),
            "suggested" to s.raw("suggested"),
        ))
    }

    for (a in (obj.el("added") as? JsonArray)?.map { it as JsonObject } ?: emptyList()) {
        // As Python's f-strings write them: `str()` of whatever the file holds, so a row id is the one Python made.
        val name = pyStrOf(a["name"]).pyStrip()
        val reps = a["reps"]?.takeIf(::truthy)?.let(::pyStrOf) ?: "8"
        val ml = kb.matchWod("$reps x $name").matched.firstOrNull()
        if (ml == null) notes += "added row ${pyStrRepr(name)} matched nothing in the exercise KB"
        val id = a["id"]?.takeIf(::truthy)?.let(::pyStrOf) ?: name
        entries += JsonObject(common + mapOf(
            "row_id" to JsonPrimitive("$sid#added#$id"),
            "exercise" to json(ml?.canonical),
            "components" to json(ml?.components),
            "line" to JsonPrimitive(ml?.line ?: name),
            "variant" to JsonNull,
            "prescribed_reps" to JsonNull,
            "weight" to a.raw("weight"),
            "reps" to a.raw("reps"),
            "skipped" to json(false),
            "easy" to json(a.flag("easy")),
            "hard" to json(a.flag("hard")),
            "hurt" to json(a.flag("hurt")),
            "easier" to json(a.flag("easier")),
            "incomplete" to json(false),
            "breakdown" to a.raw("breakdown"),
            "note" to orNull(a.el("note")),
            "added_by_owner" to json(true),
            "matched_kb" to json(ml != null),
        ))
    }
    return entries to notes
}
