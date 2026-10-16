package com.app.carry

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Which file in the folder is today's, and what it parses to.
 *
 * The choosing and the parsing are pure so they can be tested without a
 * device; everything that needs Android's storage framework lives in
 * [SessionFolder], which is as thin as it can be. SAF behaviour is the one
 * part no JVM test can answer honestly.
 */
object SessionFiles {
    /** How the session's own date relates to the day it is being read on. */
    enum class Day { TODAY, PAST, FUTURE, UNKNOWN }

    /**
     * A sheet is answering the question "what do I carry in THIS MORNING".
     * If the sync has not run, the newest file in the folder is an older
     * workout, and showing its weights under "carry these in" is the one
     * way this app can put a wrong number in someone's head. So the day is
     * compared, and a sheet that is not today's says so above the numbers.
     */
    fun dayOf(sessionDate: String, today: java.time.LocalDate): Day = try {
        val d = java.time.LocalDate.parse(sessionDate)
        when {
            d == today -> Day.TODAY
            d.isBefore(today) -> Day.PAST
            else -> Day.FUTURE
        }
    } catch (_: java.time.format.DateTimeParseException) {
        Day.UNKNOWN
    }

    /**
     * "2026-10-17" reads as "Sat 17 Oct"; anything else is shown as sent.
     *
     * Formatted in the reader's own locale, so no test may assert the
     * literal string -- an earlier one hardcoded English and would have
     * failed CI on a German runner.
     */
    fun formatDate(sessionDate: String): String = try {
        java.time.LocalDate.parse(sessionDate)
            .format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM"))
    } catch (_: java.time.format.DateTimeParseException) {
        sessionDate
    }

    /** The runner writes `session-<date>-<class>.json`. */
    private val NAME = Regex("""^session-(\d{4}-\d{2}-\d{2})-([A-Za-z0-9]+)\.json$""")

    fun isSessionFile(name: String) = NAME.matches(name)

    /** The day this file is for, from its NAME. The content carries one
     *  too, and they are not guaranteed to agree -- this is the one the
     *  folder is ordered by. */
    fun dateIn(name: String): String? = NAME.find(name)?.groupValues?.get(1)

    /**
     * The newest session by the date in its name, not by file timestamp:
     * a sync copies a file whenever it feels like it, so mtime says when it
     * arrived rather than which workout it is. Ties break on the name so
     * the choice is stable rather than arbitrary.
     */
    fun newest(names: List<String>): String? =
        names.filter(::isSessionFile).maxWithOrNull(
            compareBy({ NAME.find(it)!!.groupValues[1] }, { it })
        )

    /**
     * Returns null rather than throwing on anything unreadable. A folder is
     * a shared space -- other things write there, and a half-synced file is
     * normal, not exceptional.
     */
    /**
     * What a file's `"schema"` says about it. Only [OURS] is read.
     *
     * A missing label is NOT taken as ours. Every file the coach writes
     * carries `gym-session/1`, and its importer refuses a file without
     * it; a reader that accepted one would open a form for a file the
     * importer then drops, or read another tool's JSON as a session.
     */
    enum class Label { OURS, NEWER, MISSING, UNKNOWN }

    /** [Label] of [doc]'s `schema`: missing (absent or null), ours, a later
     *  `gym-session/N`, or anything else, a bare number included. */
    fun labelOf(doc: JsonObject): Label {
        val value = doc["schema"]?.takeIf { it !is JsonNull } ?: return Label.MISSING
        val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return Label.UNKNOWN
        if (text == Session.SCHEMA) return Label.OURS
        val version = LABEL.matchEntire(text)?.groupValues?.get(1)?.toIntOrNull()
        return if (version != null && version > 1) Label.NEWER else Label.UNKNOWN
    }

    /** [labelOf] for raw text, or null when it is not a JSON object at all
     *  -- that is damage, which is a different message. */
    fun labelOf(text: String): Label? = runCatching {
        Session.json.parseToJsonElement(text) as? JsonObject
    }.getOrNull()?.let(::labelOf)

    /** The label as written, cut short and made safe to show, for the
     *  message that says Carry doesn't know it. */
    fun labelText(text: String): String? = runCatching {
        (Session.json.parseToJsonElement(text) as? JsonObject)?.get("schema")
    }.getOrNull()?.takeIf { it !is JsonNull }?.toString()?.readable()?.take(40)

    private val LABEL = Regex("gym-session/(\\d+)")

    /** A readable file whose schema we do not know -- "update the app" and
     *  "wait for the sync" need different words. */
    fun looksLikeNewerSchema(text: String): Boolean =
        Regex(""""schema"\s*:\s*"gym-session/(\d+)"""").find(text)
            ?.groupValues?.get(1)?.toIntOrNull()?.let { it > 1 } == true

    /**
     * The rows are decoded ONE AT A TIME, so a row this build cannot read
     * costs that row rather than the day.
     *
     * Decoding the document whole meant any malformed field anywhere took
     * the whole file down -- a `"ref": null`, a `"weight": "45 lb"`, a
     * `"done": "yes"` -- and the owner saw "Can't read that session" in a
     * studio, with no way to fix it from the phone. The same bargain
     * `SessionPatch.splitAdded` already strikes for the `added` array.
     *
     * Dropping a row is safe because the SAVE path never sees this: it
     * patches the raw JSON and copies every row it did not edit through
     * byte for byte, so a row missing from here is still written back
     * untouched. What it is not is silent -- [unreadableRows] is what the
     * screens say out loud, because a row the owner cannot see is a
     * refusal they cannot act on.
     */
    fun parse(text: String): Session? = try {
        val doc = Session.json.parseToJsonElement(text) as? JsonObject
        val rows = doc?.let(::rowsIn)
        // The head is decoded with `sets` taken out, so one bad row cannot
        // take the date and the class tag with it.
        if (doc == null || rows == null || labelOf(doc) != Label.OURS) null
        else Session.json.decodeFromJsonElement<Session>(JsonObject(doc - "sets")).copy(sets = rows)
    } catch (_: IllegalArgumentException) {
        // SerializationException extends this, so one catch covers both.
        null
    }

    /**
     * The rows that decode, or null when the file is damaged in a way that
     * is not about one row.
     *
     * What counts as "not about one row" is not decided here: it is
     * whatever [SessionPatch.unshaped] names, because that is what the
     * SAVE refuses. A reader that accepts what its writer rejects is
     * worse than one that refuses -- it opens a form, takes a whole
     * borrowed day's typing, and then refuses to write it, with a message
     * about a list the owner was shown as empty.
     *
     * `null` and an absent `sets` are not damage: both mean no rows, and
     * the save takes both.
     */
    private fun rowsIn(doc: JsonObject): List<ExerciseSet>? {
        if (SessionPatch.unshaped(doc) != null) return null
        val listed = doc["sets"].takeIf { it !is JsonNull } ?: return emptyList()
        return (listed as JsonArray).mapNotNull {
            runCatching { Session.json.decodeFromJsonElement<ExerciseSet>(it) }.getOrNull()
        }
    }

    /**
     * What to say about [unreadableRows], or null when there is nothing
     * to say.
     *
     * Here rather than on either screen because both say it and the two
     * must not drift: the Before tab explains why the sheet is short, and
     * the After tab explains why there is no box to type into.
     */
    fun unreadableNote(rows: Int): String? = when {
        rows <= 0 -> null
        rows == 1 -> "1 row of this sheet didn't read. It stays in the file untouched; Carry can't show it."
        else -> "$rows rows of this sheet didn't read. They stay in the file untouched; Carry can't show them."
    }

    /**
     * How many rows the file holds that [parse] could not read.
     *
     * Derived by SUBTRACTING what parse kept from what the file lists, so
     * the count and the rows on screen cannot drift apart -- the one way
     * this could go wrong is saying "1 row didn't read" beside a sheet
     * showing all of them, or the reverse.
     */
    fun unreadableRows(text: String, parsed: Session): Int {
        val listed = ((runCatching { Session.json.parseToJsonElement(text) }.getOrNull() as? JsonObject)
            ?.get("sets") as? JsonArray)?.size ?: return 0
        return (listed - parsed.sets.size).coerceAtLeast(0)
    }
}
