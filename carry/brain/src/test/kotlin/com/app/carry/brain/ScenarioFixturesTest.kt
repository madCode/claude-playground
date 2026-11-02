package com.app.carry.brain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * gym-coach's own hand-built cases: every `suggest` call its unit tests
 * make, with the line exactly as the test built it (setup included), the
 * history it was handed, and the answer the Python gives. These reach the
 * rules the invented lifter's run seldom does: a hurt set's floor, caps,
 * stated baselines, ladders. Each must come out the same here.
 */
class ScenarioFixturesTest {
    private val dir = File(System.getProperty("brainFixtures") ?: "../brain-fixtures")
    private val kb = Kb.fromJson(File(dir, "exercises.json").readText())
    private val scenarios = File(dir, "scenarios.jsonl").readLines().filter { it.isNotBlank() }
        .map { Json.parseToJsonElement(it).jsonObject }

    @Test
    fun `every scenario from the coach's tests gets the coach's answer`() {
        assertTrue("${scenarios.size} scenarios", scenarios.size > 150)
        val failures = mutableListOf<String>()
        for ((i, sc) in scenarios.withIndex()) {
            val m = sc["ml"]!!.jsonObject
            val ml = MatchedLine(
                line = m["line"]!!.jsonPrimitive.content,
                canonical = (m["canonical"] as? JsonPrimitive)?.contentOrNull,
                components = (m["components"] as? JsonArray)?.map { it.jsonPrimitive.content },
                variant = exactVariant(m["variant"]!!.jsonObject),
                prescribedReps = (m["prescribed_reps"] as? JsonPrimitive)?.intOrNull,
            )
            val today = LocalDate.parse(sc["today"]!!.jsonPrimitive.content)
            val history = sc["history"]!!.jsonArray.map { it.jsonObject }
            val baselines = (sc["baselines"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content.toDouble() } ?: emptyMap()
            val sg = suggest(ml, kb, history, today, baselines)
            val got = buildSheet(today.toString(), "2G", listOf(ml to sg))["sets"]!!.jsonArray[0]
            val diffs = mutableListOf<String>()
            diff("set", sc["expected"]!!, got, diffs)
            if (diffs.isNotEmpty()) failures += "#$i ${ml.line} on $today:\n    " + diffs.take(4).joinToString("\n    ")
        }
        assertTrue(
            "${failures.size} of ${scenarios.size} scenarios differ from the Python:\n" + failures.take(6).joinToString("\n"),
            failures.isEmpty(),
        )
    }

    /** The variant as the test built it, field for field: no defaults re-derived. */
    private fun exactVariant(v: JsonObject): Variant {
        fun s(k: String) = (v[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
        fun i(k: String) = (v[k] as? JsonPrimitive)?.intOrNull
        return Variant(
            stability = s("stability")!!, stance = s("stance")!!, arms = s("arms")!!, implement = s("implement"),
            mode = s("mode")!!, tempo = s("tempo")!!, position = s("position")!!, difficulty = s("difficulty")!!,
            intensity = s("intensity")!!, reps = i("reps"), repsText = s("reps_text"), durationSec = i("duration_sec"),
            grip = s("grip")!!, unknown = v["unknown"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }
}
