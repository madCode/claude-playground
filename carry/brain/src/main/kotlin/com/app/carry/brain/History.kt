package com.app.carry.brain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.LocalDate

/**
 * The history file both ways, and the phone's history set beside the
 * laptop's (phase 4 of docs/designs/brain-move.md). Reading follows
 * gym-coach's `load_history`, the files an import takes follow its
 * `import_finished`, and a read of a session file follows its `read_text`,
 * so the phone imports exactly what the laptop would.
 */
object History {
    const val REPORT_SCHEMA = "brain-history/1"

    /** `session_file.MAX_FILE_BYTES`: past this a session file is refused, not read. */
    const val MAX_SESSION_FILE_BYTES = 1024 * 1024

    /** The ids a report lists of each kind; past this it only counts them. */
    private const val MAX_LISTED = 50

    /** `session_file._SESSION_NAME` (ASCII: the Python pattern sets `re.ASCII`). */
    private val SESSION_NAME = Regex("^session-([0-9]{4}-[0-9]{2}-[0-9]{2})-([A-Za-z0-9]+)\\.json$")

    /** `gym_lib.load_history`: one JSON object a line, blank lines skipped. Throws on anything else. */
    fun parse(text: String): List<JsonObject> =
        text.pySplitLines().map { it.pyStrip() }.filter { it.isNotEmpty() }
            .map { pyJsonLoads(it) as? JsonObject ?: throw IllegalArgumentException("a history line that is not an object") }

    /**
     * One row a line. `JsonElement.toString()`, never the default encoder: a row can hold a
     * NaN or an integer past 2^64, which the encoder refuses or rounds, and the laptop's
     * Python writes and reads both.
     */
    fun write(rows: List<JsonObject>): String = rows.joinToString("") { "$it\n" }

    /** `import_finished`'s choice of files: session files for days before [today], in name order. */
    fun finished(names: List<String>, today: LocalDate): List<String> = names
        .filter { name -> SESSION_NAME.matchEntire(name)?.let { it.groupValues[1] < today.toString() } == true }
        .sorted()

    /**
     * A session file's bytes as the import takes them, `read_text`'s way: too large or not UTF-8
     * is refused as unreadable, never decoded leniently into replacement characters that would
     * then import.
     */
    fun source(bytes: ByteArray?): ImportSource = when {
        bytes == null -> ImportSource.Failed("OSError", unreadable = true)
        bytes.size > MAX_SESSION_FILE_BYTES -> ImportSource.Failed("ValueError", unreadable = true)
        else -> try {
            ImportSource.Text(
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString(),
            )
        } catch (e: CharacterCodingException) {
            ImportSource.Failed("UnicodeDecodeError", unreadable = true)
        }
    }

    /** The knowledge base a brain input carries: the laptop's own, as its import reads it. */
    fun kbFromInput(text: String): Kb = Kb.fromJson((pyJsonLoads(text) as JsonObject)["exercises"] as JsonObject)

    /**
     * How the phone's history stands against the laptop's, row by row on `row_id`. Rows from
     * before ids existed have none, and are only counted. Equal means equal as Python reads
     * them: 25 is 25.0, and a key missing on one side is null there.
     */
    fun compare(phone: List<JsonObject>, laptop: List<JsonObject>): JsonObject {
        fun keyed(rows: List<JsonObject>) = rows.mapNotNull { r -> (r["row_id"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { it to r } }.toMap()
        val p = keyed(phone)
        val l = keyed(laptop)
        val onlyPhone = (p.keys - l.keys).sorted()
        val onlyLaptop = (l.keys - p.keys).sorted()
        val differing = (p.keys intersect l.keys).sorted().mapNotNull { id ->
            val a = p.getValue(id)
            val b = l.getValue(id)
            val fields = (a.keys + b.keys).filter { !pyEquals(a[it] ?: JsonNull, b[it] ?: JsonNull) }.sorted()
            if (fields.isEmpty()) null else id to fields
        }
        val unkeyedPhone = phone.size - p.size
        val unkeyedLaptop = laptop.size - l.size
        return JsonObject(mapOf(
            "schema" to JsonPrimitive(REPORT_SCHEMA),
            "same" to JsonPrimitive(onlyPhone.isEmpty() && onlyLaptop.isEmpty() && differing.isEmpty() && unkeyedPhone == unkeyedLaptop),
            "phone_rows" to JsonPrimitive(phone.size),
            "laptop_rows" to JsonPrimitive(laptop.size),
            "unkeyed_phone" to JsonPrimitive(unkeyedPhone),
            "unkeyed_laptop" to JsonPrimitive(unkeyedLaptop),
            "only_phone_count" to JsonPrimitive(onlyPhone.size),
            "only_phone" to strings(onlyPhone.take(MAX_LISTED)),
            "only_laptop_count" to JsonPrimitive(onlyLaptop.size),
            "only_laptop" to strings(onlyLaptop.take(MAX_LISTED)),
            "differing_count" to JsonPrimitive(differing.size),
            "differing" to JsonArray(differing.take(MAX_LISTED).map { (id, fields) ->
                JsonObject(mapOf("row_id" to JsonPrimitive(id), "fields" to strings(fields)))
            }),
        ))
    }

    private fun strings(xs: List<String>): JsonElement = JsonArray(xs.map(::JsonPrimitive))
}
