package com.app.carry

import com.app.carry.brain.History
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
import java.time.LocalDate

/**
 * The history check's Android-free half, on gym-coach's fixture days: an
 * invented lifter's filled sheets, and the history rows the laptop's Python
 * logged from each (`logged`), as the laptop's copy would hold them.
 */
class HistoryCheckTest {
    private val fixtures = File("../brain-fixtures")
    private val days = File(fixtures, "sheets.jsonl").readLines().filter { it.isNotBlank() }
        .map { Json.parseToJsonElement(it).jsonObject }
    private val kbInput = JsonObject(mapOf(
        "schema" to JsonPrimitive("brain-input/1"),
        "exercises" to Json.parseToJsonElement(File(fixtures, "exercises.json").readText()),
    )).toString()

    private fun date(n: Int) = days[n]["date"]!!.jsonPrimitive.content
    private fun name(n: Int) = "session-${date(n)}-${days[n]["class"]!!.jsonPrimitive.content}.json"

    /** Day [n]'s file as Carry saves it: the sheet with the lifter's answers in. */
    private fun filled(n: Int): String {
        val day = days[n]
        val answers = day["answers"]!!.jsonObject
        val sheet = day["sheet"]!!.jsonObject
        val sets = sheet["sets"]!!.jsonArray.map { s ->
            val ref = s.jsonObject["ref"]!!.jsonPrimitive.content
            JsonObject(s.jsonObject + (answers[ref]?.jsonObject ?: emptyMap<String, JsonElement>()))
        }
        return JsonObject(sheet + mapOf("sets" to JsonArray(sets), "added" to day["added"]!!)).toString()
    }

    private fun logged(range: IntRange) = range.flatMap { days[it]["logged"]!!.jsonArray.map { r -> r.jsonObject } }

    private class Sessions(val files: Map<String, ByteArray>) : Folder {
        override val label = "Sync/gym"
        override val chosen = true
        override val grantLost = false
        override fun list() = files.keys.map { it to it }
        override fun read(documentId: String) = files[documentId]?.decodeToString()
        override fun readBytes(documentId: String, max: Int) = files[documentId]
        override fun write(documentId: String, text: String) = false
    }

    private class Brain(val files: MutableMap<String, String>, val canWrite: Boolean = true) : BrainFolder {
        override fun list() = files.keys.map { it to it }
        override fun read(documentId: String) = files[documentId]
        override fun create(name: String, text: String): Boolean {
            if (canWrite) files[name] = text
            return canWrite
        }
        override fun write(documentId: String, text: String): Boolean {
            if (canWrite) files[documentId] = text
            return canWrite
        }
    }

    private fun brain(laptop: List<JsonObject>, vararg more: Pair<String, String>) =
        Brain(mutableMapOf("history.jsonl" to History.write(laptop), "input-2000-01-01-2G.json" to kbInput, *more))

    private fun sessions(vararg n: Int) = Sessions(n.associate { name(it) to filled(it).encodeToByteArray() })

    private fun report(b: Brain) = Json.parseToJsonElement(b.files.getValue("history-compare.json")).jsonObject

    private fun flag(o: JsonObject, key: String) = o[key]!!.jsonPrimitive.content.toBoolean()

    private val today get() = LocalDate.parse(date(22))

    @Test
    fun `the first run starts the phone's history from the laptop's, and they agree`() {
        val b = brain(logged(0..21))
        assertEquals(HistoryCheck.Outcome.CHECKED, HistoryCheck.runOnce(sessions(19, 20, 21), b, today, "test"))
        assertEquals(History.parse(b.files.getValue("history.jsonl")), History.parse(b.files.getValue("history-phone.jsonl")))
        val r = report(b)
        assertTrue(flag(r, "seeded"))
        assertTrue("the files already logged change nothing: $r", flag(r, "same"))
    }

    @Test
    fun `a class the laptop has not logged yet shows as the phone's alone, until the laptop catches up`() {
        val b = brain(logged(0..20))
        HistoryCheck.runOnce(sessions(20, 21), b, today, "test")
        val r = report(b)
        assertFalse(flag(r, "same"))
        val ids = logged(21..21).map { it["row_id"]!!.jsonPrimitive.content }.sorted()
        assertEquals(ids, r["only_phone"]!!.jsonArray.map { it.jsonPrimitive.content })
        // Its first run: seeded from the laptop's, then the new class imported on top.
        assertTrue(flag(r, "seeded"))

        // The laptop imports the same file: the two now agree, row for row and field for field.
        b.files["history.jsonl"] = History.write(logged(0..21))
        HistoryCheck.runOnce(sessions(20, 21), b, today, "test")
        assertTrue("the phone's import matches the laptop's: ${report(b)}", flag(report(b), "same"))
        assertFalse("the phone kept its own history this time", flag(report(b), "seeded"))
    }

    @Test
    fun `today's class is not imported until the day is over`() {
        val b = brain(logged(0..20))
        HistoryCheck.runOnce(sessions(21), b, LocalDate.parse(date(21)), "test")
        assertTrue(flag(report(b), "same"))
    }

    @Test
    fun `a phone history a cut-short write damaged is started again from the laptop's, and says so`() {
        val b = brain(logged(0..21), "history-phone.jsonl" to "{\"row_id\": \"x\", \"wei")
        HistoryCheck.runOnce(sessions(21), b, today, "test")
        assertTrue(flag(report(b), "seeded"))
        assertTrue(flag(report(b), "same"))
    }

    @Test
    fun `a session file that is not UTF-8 is refused as the laptop refuses it`() {
        val b = brain(logged(0..20))
        val bad = byteArrayOf(0x7b, 0xff.toByte(), 0x7d)
        HistoryCheck.runOnce(Sessions(mapOf(name(21) to bad)), b, today, "test")
        val import = report(b)["import"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("${name(21)}: not imported, unreadable (UnicodeDecodeError)"), import)
        assertTrue(flag(report(b), "same"))
    }

    @Test
    fun `with no laptop history there is nothing to start from, and nothing is written`() {
        val b = Brain(mutableMapOf("input-2000-01-01-2G.json" to kbInput))
        assertEquals(HistoryCheck.Outcome.NO_LAPTOP_HISTORY, HistoryCheck.runOnce(sessions(21), b, today, "test"))
        assertEquals(setOf("input-2000-01-01-2G.json"), b.files.keys)
    }

    @Test
    fun `a folder that can't be written is reported as such`() {
        val b = Brain(mutableMapOf("history.jsonl" to History.write(logged(0..20)), "input-2000-01-01-2G.json" to kbInput), canWrite = false)
        assertEquals(HistoryCheck.Outcome.NOT_WRITTEN, HistoryCheck.runOnce(sessions(21), b, today, "test"))
    }
}
