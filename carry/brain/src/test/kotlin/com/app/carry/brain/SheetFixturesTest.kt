package com.app.carry.brain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * The invented lifter's run, replayed the way gym-coach's `fixtures.check()`
 * replays it: each day's board matched, the history so far read as the
 * coach reads it, every row suggested and the sheet built, then the
 * lifter's answers turned into history rows. Both must equal what the
 * Python wrote, every day. The next day builds on the fixture's own rows,
 * not this port's, so one wrong day is reported once rather than poisoning
 * every day after it.
 *
 * Numbers compare by value, as Python's own `==` on the loaded JSON does:
 * `json.dumps` writes 30.0 where Kotlin may hold 30. Where a number is
 * printed into a sentence, the sentence itself must match.
 */
class SheetFixturesTest {
    private val dir = File(System.getProperty("brainFixtures") ?: "../brain-fixtures")
    private val kb = Kb.fromJson(File(dir, "exercises.json").readText())
    private val manifest = Json.parseToJsonElement(File(dir, "manifest.json").readText()).jsonObject
    private val boards = File(dir, "boards.jsonl").readLines().filter { it.isNotBlank() }
        .map { Json.parseToJsonElement(it).jsonObject }.associateBy { it["id"]!!.jsonPrimitive.content }
    private val days = File(dir, "sheets.jsonl").readLines().filter { it.isNotBlank() }
        .map { Json.parseToJsonElement(it).jsonObject }

    @Test
    fun `the run is every day the manifest counts`() {
        assertEquals(manifest["days"]!!.jsonPrimitive.content.toInt(), days.size)
        assertTrue("a run this short proves little: ${days.size}", days.size > 100)
    }

    @Test
    fun `each day's sheet and history rows come out as the coach's`() {
        val baselines = manifest["baselines"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content.toDouble() }
        val history = mutableListOf<JsonObject>()
        val failures = mutableListOf<String>()
        var sets = 0
        var rows = 0
        for (d in days) {
            val date = d["date"]!!.jsonPrimitive.content
            val classTag = d["class"]!!.jsonPrimitive.content
            val board = boards.getValue(d["board"]!!.jsonPrimitive.content)
            val text = board["lines"]!!.jsonArray.joinToString("\n") { it.jsonPrimitive.content }
            val today = LocalDate.parse(date)
            val read = readToday(history, kb)
            val pairs = kb.matchWod(text).matched.map { it to suggest(it, kb, read, today, baselines) }
            // Which class's board stands in, and when the class starts, are inputs
            // to the sheet (the calendar's and the fetch's), so they come from the fixture.
            val wanted = d["sheet"]!!.jsonObject
            val sheet = buildSheet(
                date, classTag, pairs,
                borrowedFrom = (wanted["borrowed_from"] as? JsonPrimitive)?.contentOrNull,
                startsAt = (wanted["starts_at"] as? JsonPrimitive)?.contentOrNull,
            )
            sets += pairs.size

            val diffs = mutableListOf<String>()
            diff("sheet", d["sheet"]!!, sheet, diffs)

            val answers = d["answers"]!!.jsonObject
            val filled = JsonObject(sheet + mapOf(
                "sets" to JsonArray(sheet["sets"]!!.jsonArray.map { s ->
                    val ref = s.jsonObject["ref"]!!.jsonPrimitive.content
                    JsonObject(s.jsonObject + (answers[ref]?.jsonObject ?: emptyMap()))
                }),
                "added" to d["added"]!!,
            ))
            val (logged, _) = toHistory(filled, kb)
            rows += logged.size
            diff("logged", d["logged"]!!, JsonArray(logged), diffs)
            if (diffs.isNotEmpty()) failures += "$date $classTag (${diffs.size} fields):\n    " + diffs.take(6).joinToString("\n    ")

            history += d["logged"]!!.jsonArray.map { it.jsonObject }
            // Rows as an older build stored them, without a variant: read_today re-reads them.
            history += d["legacy"]!!.jsonArray.map { it.jsonObject }
        }
        assertTrue("$sets sets suggested", sets > 1000)
        assertTrue("$rows rows logged", rows > 800)
        assertTrue(
            "${failures.size} of ${days.size} days differ from the Python:\n" + failures.take(5).joinToString("\n"),
            failures.isEmpty(),
        )
    }

    /** Every place [want] and [got] differ, as path: python vs kotlin. */
    private fun diff(path: String, want: JsonElement, got: JsonElement, out: MutableList<String>) {
        when {
            want is JsonObject && got is JsonObject -> for (k in (want.keys + got.keys).sorted()) {
                val w = want[k]
                val g = got[k]
                if (w == null || g == null) out += "$path.$k: python ${w ?: "(absent)"} vs kotlin ${g ?: "(absent)"}"
                else diff("$path.$k", w, g, out)
            }
            want is JsonArray && got is JsonArray -> {
                if (want.size != got.size) out += "$path: python has ${want.size} items, kotlin ${got.size}"
                for (i in 0 until minOf(want.size, got.size)) diff("$path[$i]", want[i], got[i], out)
            }
            !sameValue(want, got) -> out += "$path: python $want vs kotlin $got"
        }
    }

    /** Python's `==` on loaded JSON for scalars: 30 equals 30.0, a string never equals a number. */
    private fun sameValue(a: JsonElement, b: JsonElement): Boolean {
        if (a is JsonNull || b is JsonNull) return a is JsonNull && b is JsonNull
        if (a !is JsonPrimitive || b !is JsonPrimitive) return a == b
        if (a.isString || b.isString) return a.isString && b.isString && a.content == b.content
        val ab = a.booleanOrNull
        val bb = b.booleanOrNull
        if (ab != null || bb != null) return ab == bb
        return a.doubleOrNull == b.doubleOrNull
    }
}
