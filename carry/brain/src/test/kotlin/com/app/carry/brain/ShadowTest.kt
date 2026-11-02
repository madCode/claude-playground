package com.app.carry.brain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The side-by-side check, driven the way gym-coach's sheets job will drive
 * it: an input file per day (board, history as stored, baselines, the
 * knowledge base inline) in, a sheet out, compared on the coach's fields.
 */
class ShadowTest {
    private val dir = File(System.getProperty("brainFixtures") ?: "../brain-fixtures")
    private val exercises = Json.parseToJsonElement(File(dir, "exercises.json").readText()).jsonObject
    private val manifest = Json.parseToJsonElement(File(dir, "manifest.json").readText()).jsonObject
    private val boards = File(dir, "boards.jsonl").readLines().filter { it.isNotBlank() }
        .map { Json.parseToJsonElement(it).jsonObject }.associateBy { it["id"]!!.jsonPrimitive.content }
    private val days = File(dir, "sheets.jsonl").readLines().filter { it.isNotBlank() }
        .map { Json.parseToJsonElement(it).jsonObject }

    private fun input(day: JsonObject, history: List<JsonElement>): JsonObject {
        val sheet = day["sheet"]!!.jsonObject
        return JsonObject(mapOf(
            "schema" to JsonPrimitive(Shadow.INPUT_SCHEMA),
            "sheet_id" to sheet["sheet_id"]!!, "date" to day["date"]!!, "class" to day["class"]!!,
            "borrowed_from" to (sheet["borrowed_from"] ?: JsonNull), "starts_at" to (sheet["starts_at"] ?: JsonNull),
            "board" to JsonPrimitive(boards.getValue(day["board"]!!.jsonPrimitive.content)["lines"]!!.jsonArray
                .joinToString("\n") { it.jsonPrimitive.content }),
            "history" to JsonArray(history), "baselines" to manifest["baselines"]!!, "exercises" to exercises,
        ))
    }

    @Test
    fun `every day's input gives back the coach's sheet`() {
        val history = mutableListOf<JsonElement>()
        var differing = 0
        for (day in days) {
            val diffs = Shadow.differences(day["sheet"]!!.jsonObject, Shadow.sheetFromInput(input(day, history)))
            if (diffs.isNotEmpty()) differing++
            history += day["logged"]!!.jsonArray + day["legacy"]!!.jsonArray
        }
        assertEquals("days whose sheet differs", 0, differing)
    }

    @Test
    fun `a changed suggestion is reported where it is, and the owner's own entries are not compared`() {
        val day = days[40]
        val phone = Shadow.sheetFromInput(input(day, days.take(40).flatMap { it["logged"]!!.jsonArray + it["legacy"]!!.jsonArray }))
        val sets = phone["sets"]!!.jsonArray.map { it.jsonObject }
        val changed = JsonObject(phone + mapOf("sets" to JsonArray(sets.mapIndexed { i, s ->
            JsonObject(s + mapOf("weight" to JsonPrimitive(99)) +
                (if (i == 0) mapOf("suggested" to JsonPrimitive("try 999 lb")) else emptyMap()))
        })))
        val diffs = Shadow.differences(day["sheet"]!!.jsonObject, changed)
        assertEquals(listOf("sets[0].suggested"), diffs.map { it.path })
        assertTrue(Shadow.differences(day["sheet"]!!.jsonObject, phone).isEmpty())
    }
}
