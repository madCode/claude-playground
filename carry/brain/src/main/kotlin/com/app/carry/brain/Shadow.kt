package com.app.carry.brain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/**
 * The side-by-side period of the brain move: gym-coach writes, beside each
 * sheet, everything it worked the sheet out from (`brain/input-<date>-<class>.json`,
 * schema `brain-input/1`), and this works the sheet out again from exactly
 * that, so the two can be compared on real classes before the phone's sheet
 * is the one shown.
 */
object Shadow {
    const val INPUT_SCHEMA = "brain-input/1"

    /** The sheet this port writes from a brain input file. */
    fun sheetFromInput(input: JsonObject): JsonObject {
        require((input["schema"] as? JsonPrimitive)?.contentOrNull == INPUT_SCHEMA) { "not a $INPUT_SCHEMA file" }
        val kb = Kb.fromJson(input["exercises"]!!.jsonObject)
        val date = input["date"]!!.jsonPrimitive.content
        val today = LocalDate.parse(date)
        val history = readToday(input["history"]!!.jsonArray.map { it.jsonObject }, kb)
        val baselines = (input["baselines"] as? JsonObject)
            ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.doubleOrNull?.let { k to it } }?.toMap() ?: emptyMap()
        val pairs = kb.matchWod(input["board"]!!.jsonPrimitive.content).matched
            .map { it to suggest(it, kb, history, today, baselines) }
        return buildSheet(
            date, input["class"]!!.jsonPrimitive.content, pairs,
            borrowedFrom = (input["borrowed_from"] as? JsonPrimitive)?.contentOrNull,
            startsAt = (input["starts_at"] as? JsonPrimitive)?.contentOrNull,
        )
    }

    /** The sheet's own fields and each set's coach-written ones: what the owner fills in is left out. */
    private val SHEET_FIELDS = listOf("sheet_id", "date", "class", "borrowed_from", "starts_at")
    private val SET_FIELDS = listOf(
        "ref", "key", "line", "canonical", "components", "variant", "prescribed_reps", "prescribed_text", "rounds",
        "prescribed_duration_sec", "family", "suggested", "suggested_weight", "related", "claims", "pattern_note",
        "miss_note", "previous_weight", "streak_done", "streak_needed",
    )

    /** One place the two sheets differ: where, and what each wrote there. */
    data class Difference(val path: String, val laptop: JsonElement?, val phone: JsonElement?)

    /**
     * Where [phone]'s sheet differs from [laptop]'s on what the coach writes.
     * Numbers compare by value (Python writes 30.0 where this may hold 30),
     * as the fixture tests do; a field one side lacks counts as null.
     */
    fun differences(laptop: JsonObject, phone: JsonObject): List<Difference> {
        val out = mutableListOf<Difference>()
        for (f in SHEET_FIELDS) compare(f, laptop[f], phone[f], out)
        val a = (laptop["sets"] as? JsonArray).orEmpty()
        val b = (phone["sets"] as? JsonArray).orEmpty()
        if (a.size != b.size) out += Difference("sets", JsonPrimitive(a.size), JsonPrimitive(b.size))
        for (i in 0 until minOf(a.size, b.size)) {
            val x = a[i] as? JsonObject ?: JsonObject(emptyMap())
            val y = b[i] as? JsonObject ?: JsonObject(emptyMap())
            for (f in SET_FIELDS) compare("sets[$i].$f", x[f], y[f], out)
        }
        return out
    }

    private fun compare(path: String, a: JsonElement?, b: JsonElement?, out: MutableList<Difference>) {
        val x = a ?: JsonNull
        val y = b ?: JsonNull
        when {
            x is JsonObject && y is JsonObject -> for (k in (x.keys + y.keys).sorted()) compare("$path.$k", x[k], y[k], out)
            x is JsonArray && y is JsonArray && x.size == y.size -> x.indices.forEach { compare("$path[$it]", x[it], y[it], out) }
            !sameScalar(x, y) -> out += Difference(path, a, b)
        }
    }

    private fun sameScalar(a: JsonElement, b: JsonElement): Boolean {
        if (a is JsonNull || b is JsonNull) return a is JsonNull && b is JsonNull
        if (a !is JsonPrimitive || b !is JsonPrimitive) return a == b
        if (a.isString || b.isString) return a.isString && b.isString && a.content == b.content
        val ab = a.booleanOrNull
        val bb = b.booleanOrNull
        if (ab != null || bb != null) return ab == bb
        return a.doubleOrNull == b.doubleOrNull
    }
}
