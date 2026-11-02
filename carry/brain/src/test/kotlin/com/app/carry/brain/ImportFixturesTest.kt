package com.app.carry.brain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * gym-coach's import run (`fixtures.imports`), replayed in order from an
 * empty history: each step's files through [planImport], which must give
 * the Python's report line for line, the same rewrite (or none), and the
 * same appended rows. History then moves on by the fixture's own rows, so
 * one wrong step is reported once rather than poisoning every step after.
 */
class ImportFixturesTest {
    private val dir = File(System.getProperty("brainFixtures") ?: "../brain-fixtures")
    private val kb = Kb.fromJson(File(dir, "exercises.json").readText())
    private val steps = File(dir, "imports.jsonl").readLines().filter { it.isNotBlank() }
        .map { Json.parseToJsonElement(it).jsonObject }

    @Test
    fun `every import step comes out as the Python's`() {
        val runs = steps.filter { "files" in it }
        assertTrue("${runs.size} import runs", runs.size > 300)
        // Each rule the run mixes in has to be reached, or a regenerated fixture could stop testing it.
        val lines = runs.flatMap { it["report"]!!.jsonArray.mapNotNull { r -> (r as? JsonPrimitive)?.contentOrNull } }
        for (rule in listOf(
            "updated", "would update", "differ from this file", "already has sets logged another way",
            "without an id", "two rows share an id", "now a different exercise", "unreadable (OSError)",
            "unreadable (JSONDecodeError)", "not a valid session file", "name and contents disagree",
        )) assertTrue("no step reaches \"$rule\"", lines.any { rule in it })
        assertTrue("no rewrite that changed no old row", runs.any { it["rewrote"] == JsonObject(emptyMap()) })

        var history = listOf<JsonObject>()
        val failures = mutableListOf<String>()
        for (step in steps) {
            val id = step["id"]!!.jsonPrimitive.content
            step["seed"]?.let { seed -> history = history + seed.jsonArray.map { it.jsonObject } }
            step["patch"]?.let { patch ->
                history = history.mapIndexed { i, h -> patch.jsonObject[i.toString()]?.jsonObject ?: h }
            }
            val files = step["files"]?.jsonArray ?: continue
            val given = files.map { f ->
                val o = f.jsonObject
                val name = o["name"]!!.jsonPrimitive.content
                name to (o["text"]?.let { ImportSource.Text(it.jsonPrimitive.content) } ?: readFailure(o["error"]!!.jsonPrimitive.content))
            }
            val plan = planImport(given, history, kb, step["write"]!!.jsonPrimitive.boolean)

            val rewrote = step["rewrote"]!!.takeUnless { it is JsonNull }?.jsonObject
            val appended = step["appended"]!!.jsonArray
            val wanted = rewrote?.let { r -> history.mapIndexed { i, h -> r[i.toString()] ?: h } + appended }
            val diffs = mutableListOf<String>()
            val report = step["report"]!!.jsonArray.map { (it as? JsonPrimitive)?.contentOrNull }
            for (i in 0 until maxOf(report.size, plan.report.size)) {
                val w = report.getOrNull(i)
                val g = plan.report.getOrNull(i)
                if (w != g) diffs += "report[$i]: python ${w ?: "null"} vs kotlin ${g ?: "null"}"
            }
            when {
                (wanted == null) != (plan.rewritten == null) ->
                    diffs += "rewrite: python ${if (wanted == null) "none" else "one"} vs kotlin ${if (plan.rewritten == null) "none" else "one"}"
                wanted != null -> diff("rewritten", JsonArray(wanted), JsonArray(plan.rewritten!!), diffs)
                else -> diff("appended", appended, JsonArray(plan.appended), diffs)
            }
            if (diffs.isNotEmpty()) failures += "$id: ${diffs.first()}" + if (diffs.size > 1) " (and ${diffs.size - 1} more)" else ""
            history = wanted?.map { it.jsonObject } ?: (history + appended.map { it.jsonObject })
        }
        if (failures.isNotEmpty()) {
            fail("${failures.size} of ${runs.size} import runs differ from the Python:\n" + failures.take(6).joinToString("\n"))
        }
    }

    /** A read that raised the Python exception [name]: OSError and ValueError, and theirs, are unreadable files. */
    private fun readFailure(name: String) = ImportSource.Failed(name, name in PY_READ_ERRORS)

    private companion object {
        val PY_READ_ERRORS = setOf(
            "OSError", "FileNotFoundError", "PermissionError", "IsADirectoryError", "BlockingIOError",
            "ValueError", "UnicodeDecodeError", "JSONDecodeError",
        )
    }
}
