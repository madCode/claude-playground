package com.app.carry

import com.app.carry.brain.Shadow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** The `brain/` subfolder gym-coach writes each sheet's inputs into. */
interface BrainFolder {
    /** (document id, display name) for everything in it. */
    fun list(): List<Pair<String, String>>
    fun read(documentId: String): String?
    /** Makes a new file and writes [text] into it; false when either step failed. */
    fun create(name: String, text: String): Boolean
}

/**
 * The side-by-side period of the brain move (docs/designs/brain-move.md):
 * for each sheet gym-coach wrote with its inputs beside it, the Kotlin brain
 * works the sheet out again from those inputs, and the outcome is written
 * back as `brain/shadow-<date>-<class>.json`, which syncs to the laptop to be
 * read. The sheet the owner sees is still the laptop's; nothing here shows.
 *
 * One report per input, never rewritten, so a day is compared once, with the
 * brain that was installed when its sheet arrived.
 */
object ShadowCheck {
    const val REPORT_SCHEMA = "brain-shadow/1"

    private val INPUT = Regex("""^input-(\d{4}-\d{2}-\d{2})-([A-Za-z0-9]+)\.json$""")

    /** A few per run: this runs in the hourly background check, which has a time budget. */
    private const val MAX_REPORTS = 3

    /** The differences a report lists; past this it only counts them. */
    private const val MAX_DIFFERENCES = 50

    /** Reports for the inputs that have their sheet and no report yet; how many were written. */
    fun runOnce(sessions: Folder, brain: BrainFolder, build: String): Int {
        val files = brain.list()
        val names = files.map { it.second }.toSet()
        val sheets = sessions.list().associate { (id, name) -> name to id }
        var written = 0
        for ((id, name) in files.sortedBy { it.second }) {
            val m = INPUT.matchEntire(name) ?: continue
            val (date, tag) = m.destructured
            val reportName = "shadow-$date-$tag.json"
            if (reportName in names) continue
            // The sheet syncs separately; until it is here there is nothing to compare with.
            val sheetId = sheets["session-$date-$tag.json"] ?: continue
            val report = report(brain.read(id), sessions.read(sheetId), build)
            if (brain.create(reportName, Json.encodeToString(JsonElement.serializer(), report))) written++
            if (written >= MAX_REPORTS) break
        }
        return written
    }

    /** What one comparison found. An input or sheet that can't be read is reported, not skipped. */
    internal fun report(inputText: String?, sheetText: String?, build: String): JsonObject {
        val head = mapOf("schema" to JsonPrimitive(REPORT_SCHEMA), "brain" to JsonPrimitive(build))
        fun failed(why: String) = JsonObject(head + mapOf("same" to JsonPrimitive(false), "error" to JsonPrimitive(why)))
        if (inputText == null) return failed("the input could not be read")
        if (sheetText == null) return failed("the sheet could not be read")
        val input = runCatching { Json.parseToJsonElement(inputText) as JsonObject }.getOrNull()
            ?: return failed("the input is not a JSON object")
        val laptop = runCatching { Json.parseToJsonElement(sheetText) as JsonObject }.getOrNull()
            ?: return failed("the sheet is not a JSON object")
        val phone = runCatching { Shadow.sheetFromInput(input) }
            .getOrElse { return failed("the brain failed: ${it.javaClass.simpleName}: ${it.message?.take(200)}") }
        val diffs = Shadow.differences(laptop, phone)
        return JsonObject(head + mapOf(
            "sheet_id" to (laptop["sheet_id"] as? JsonPrimitive ?: JsonNull),
            "same" to JsonPrimitive(diffs.isEmpty()),
            "sets" to JsonPrimitive((laptop["sets"] as? JsonArray)?.size ?: 0),
            "difference_count" to JsonPrimitive(diffs.size),
            "differences" to JsonArray(diffs.take(MAX_DIFFERENCES).map {
                JsonObject(mapOf("path" to JsonPrimitive(it.path), "laptop" to (it.laptop ?: JsonNull), "phone" to (it.phone ?: JsonNull)))
            }),
        ))
    }

    /** Whether a report says the two sheets agreed. */
    fun same(report: JsonObject): Boolean = (report["same"] as? JsonPrimitive)?.contentOrNull == "true"
}
