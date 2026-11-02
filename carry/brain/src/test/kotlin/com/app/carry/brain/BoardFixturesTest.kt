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
 * Every board in gym-coach's corpus, matched by this port and by the Python,
 * must come out the same: the rows, each row's setup and key, and the lines
 * the sheet names as untracked. The fixtures are written by gym-coach's
 * `fixtures.py` from the Python as it is (brain-fixtures/README.md).
 */
class BoardFixturesTest {
    private val dir = File(System.getProperty("brainFixtures") ?: "../brain-fixtures")
    private val kb = Kb.fromJson(File(dir, "exercises.json").readText())
    private val boards = File(dir, "boards.jsonl").readLines().filter { it.isNotBlank() }
        .map { Json.parseToJsonElement(it).jsonObject }

    @Test
    fun `the fixtures are the whole corpus`() {
        val manifest = Json.parseToJsonElement(File(dir, "manifest.json").readText()).jsonObject
        assertEquals(manifest["boards"]!!.jsonPrimitive.content.toInt(), boards.size)
        assertTrue("a corpus this small proves little: ${boards.size}", boards.size > 200)
    }

    @Test
    fun `each line matches as the coach matches it`() {
        val misses = mutableListOf<String>()
        var lines = 0
        for (b in boards) {
            for (pl in b["per_line"]!!.jsonArray) {
                val raw = pl.jsonObject["raw"]!!.jsonPrimitive.content
                val stripped = raw.pyStrip().trimStart('-', '*', '•').pyStrip()
                lines++
                val got = kb.matchLine(stripped)?.let(::row) ?: JsonNull
                if (got != pl.jsonObject["match"]) misses += "${b.id()}: $raw\n  python: ${pl.jsonObject["match"]}\n  kotlin: $got"
                val structured = JsonPrimitive(isStructured(raw))
                if (structured != pl.jsonObject["structured"]) misses += "${b.id()}: structured differs for $raw"
            }
        }
        // Lines the coach reads past: a port that matches one has a row the Python never makes.
        var unread = 0
        for (b in boards) for (raw in b["unread"]!!.jsonArray.map { it.jsonPrimitive.content }) {
            val stripped = raw.pyStrip().trimStart('-', '*', '\u2022').pyStrip()
            unread++
            if (kb.matchLine(stripped) != null || untrackedLines(listOf(stripped)).isNotEmpty()) {
                misses += "${b.id()}: read a line the coach reads past: $raw"
            }
        }
        assertTrue("$lines lines read", lines > 2000)
        assertTrue("$unread lines read past", unread > 5000)
        assertTrue("${misses.size} of $lines lines differ:\n" + misses.take(15).joinToString("\n"), misses.isEmpty())
    }

    @Test
    fun `each board comes out as the coach's sheet rows`() {
        val misses = boards.mapNotNull { b ->
            val text = b["lines"]!!.jsonArray.joinToString("\n") { it.jsonPrimitive.content }
            val res = kb.matchWod(text)
            val got = JsonObject(mapOf(
                "matched" to JsonArray(res.matched.map(::row)),
                "untracked" to JsonArray(untrackedLines(res.unmatched).map(::JsonPrimitive)),
            ))
            if (got != b["expected"]) "${b.id()}\n  python: ${b["expected"]}\n  kotlin: $got" else null
        }
        assertTrue("${misses.size} of ${boards.size} boards differ:\n" + misses.take(5).joinToString("\n"), misses.isEmpty())
    }

    private fun JsonObject.id() = "${this["date"]!!.jsonPrimitive.content} ${this["class"]!!.jsonPrimitive.content} ${this["id"]!!.jsonPrimitive.content}"
}

/** A row as `fixtures._matched` writes it. */
internal fun row(ml: MatchedLine): JsonObject = JsonObject(mapOf(
    "line" to JsonPrimitive(ml.line),
    "key" to JsonPrimitive(stableKey(ml.canonical, ml.components, ml.variant)),
    "canonical" to (ml.canonical?.let(::JsonPrimitive) ?: JsonNull),
    "components" to (ml.components?.let { JsonArray(it.map(::JsonPrimitive)) } ?: JsonNull),
    "variant" to variantJson(ml.variant),
    "prescribed_reps" to (ml.prescribedReps?.let(::JsonPrimitive) ?: JsonNull),
))
