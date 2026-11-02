package com.app.carry

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The side-by-side check's Android-free half: which inputs get a report,
 * and what it says. The input is a real fixture day from gym-coach (made-up
 * lifter, public boards), in the shape its sheets job writes.
 */
class ShadowCheckTest {
    private val fixtures = File("../brain-fixtures")
    private val days = File(fixtures, "sheets.jsonl").readLines().filter { it.isNotBlank() }
        .map { Json.parseToJsonElement(it).jsonObject }
    private val boards = File(fixtures, "boards.jsonl").readLines().filter { it.isNotBlank() }
        .map { Json.parseToJsonElement(it).jsonObject }.associateBy { it["id"]!!.jsonPrimitive.content }
    private val day = days[20]
    private val date = day["date"]!!.jsonPrimitive.content
    private val tag = day["class"]!!.jsonPrimitive.content
    private val sheet = day["sheet"]!!.jsonObject

    private val input: String = JsonObject(mapOf(
        "schema" to JsonPrimitive("brain-input/1"), "sheet_id" to sheet["sheet_id"]!!, "date" to JsonPrimitive(date),
        "class" to JsonPrimitive(tag), "borrowed_from" to (sheet["borrowed_from"] ?: JsonNull),
        "starts_at" to (sheet["starts_at"] ?: JsonNull),
        "board" to JsonPrimitive(boards.getValue(day["board"]!!.jsonPrimitive.content)["lines"]!!.jsonArray
            .joinToString("\n") { it.jsonPrimitive.content }),
        "history" to JsonArray(days.take(20).flatMap { it["logged"]!!.jsonArray + it["legacy"]!!.jsonArray }),
        "baselines" to Json.parseToJsonElement(File(fixtures, "manifest.json").readText()).jsonObject["baselines"]!!,
        "exercises" to Json.parseToJsonElement(File(fixtures, "exercises.json").readText()),
    )).toString()

    private class Sessions(val files: Map<String, String>) : Folder {
        override val label = "Sync/gym"
        override val chosen = true
        override val grantLost = false
        override fun list() = files.keys.map { it to it }
        override fun read(documentId: String) = files[documentId]
        override fun write(documentId: String, text: String) = false
    }

    private class Brain(val files: MutableMap<String, String?>) : BrainFolder {
        override fun list() = files.keys.map { it to it }
        override fun read(documentId: String) = files[documentId]
        override fun create(name: String, text: String): Boolean { files[name] = text; return true }
    }

    private fun report(brain: Brain, name: String = "shadow-$date-$tag.json") =
        Json.parseToJsonElement(brain.files[name]!!).jsonObject

    @Test
    fun `a sheet the brain agrees with is reported the same, the owner's own entries aside`() {
        // Filled in, as it will be by the time the hourly check sees it.
        val filled = JsonObject(sheet + mapOf("sets" to JsonArray(sheet["sets"]!!.jsonArray.map {
            JsonObject(it.jsonObject + mapOf("weight" to JsonPrimitive(25.0), "easy" to JsonPrimitive(true)))
        })))
        val brain = Brain(mutableMapOf("input-$date-$tag.json" to input))
        val wrote = ShadowCheck.runOnce(Sessions(mapOf("session-$date-$tag.json" to filled.toString())), brain, "test")
        assertEquals(1, wrote)
        val r = report(brain)
        assertTrue(r.toString(), ShadowCheck.same(r))
        assertEquals(sheet["sets"]!!.jsonArray.size, r["sets"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `a sheet that differs is reported with where`() {
        val sets = sheet["sets"]!!.jsonArray.map { it.jsonObject }
        val changed = JsonObject(sheet + mapOf("sets" to JsonArray(listOf(
            JsonObject(sets[0] + mapOf("suggested" to JsonPrimitive("try 999 lb")))) + sets.drop(1))))
        val brain = Brain(mutableMapOf("input-$date-$tag.json" to input))
        ShadowCheck.runOnce(Sessions(mapOf("session-$date-$tag.json" to changed.toString())), brain, "test")
        val r = report(brain)
        assertFalse(ShadowCheck.same(r))
        assertEquals("sets[0].suggested", r["differences"]!!.jsonArray[0].jsonObject["path"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an input waits for its sheet, and a day is compared once`() {
        val brain = Brain(mutableMapOf("input-$date-$tag.json" to input))
        assertEquals("no sheet yet", 0, ShadowCheck.runOnce(Sessions(emptyMap()), brain, "test"))
        assertFalse(brain.files.keys.any { it.startsWith("shadow-") })
        val sessions = Sessions(mapOf("session-$date-$tag.json" to sheet.toString()))
        assertEquals(1, ShadowCheck.runOnce(sessions, brain, "test"))
        assertEquals("never rewritten", 0, ShadowCheck.runOnce(sessions, brain, "test"))
    }

    @Test
    fun `an input that can't be read is reported, not skipped, and a run does a few at most`() {
        val brain = Brain(mutableMapOf<String, String?>().apply {
            for (d in 1..5) put("input-2026-01-0$d-2G.json", null)
        })
        val sessions = Sessions((1..5).associate { "session-2026-01-0$it-2G.json" to sheet.toString() })
        assertEquals(3, ShadowCheck.runOnce(sessions, brain, "test"))
        val r = report(brain, "shadow-2026-01-01-2G.json")
        assertFalse(ShadowCheck.same(r))
        assertEquals("the input could not be read", r["error"]!!.jsonPrimitive.content)
    }
}
