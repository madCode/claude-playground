package com.app.carry.brain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigInteger
import java.time.DateTimeException
import java.time.LocalDate

/**
 * gym-coach's session import (`session_file.plan_import`): filled session
 * files in, the history rows they add or change out. The file is the
 * owner's word, so history follows it as far as it safely can: a new row is
 * logged, a logged row the owner has since changed in Carry is updated,
 * nothing is removed, and whatever else differs is reported and left.
 * Everything that is refused is said, one line per file.
 */

/** A session file as the import is handed it: its text, or the read that failed. */
sealed interface ImportSource {
    data class Text(val text: String) : ImportSource

    /**
     * A read that raised [error], named as the report should print it.
     * [unreadable] for what Python calls an `OSError` or `ValueError` (a
     * failed read, a file too large or not a file), which the report calls
     * unreadable; anything else is an unexpected failure, reported as such.
     */
    data class Failed(val error: String, val unreadable: Boolean) : ImportSource
}

/**
 * What an import adds up to. [report] has a line per file, null where there
 * was nothing to say. [rewritten] is the whole history to write when a
 * logged row changed, the new rows at its end, and null when none did;
 * [appended] is then the rows to append, and empty when [rewritten] is set
 * or on a dry run.
 */
data class ImportPlan(val report: List<String?>, val rewritten: List<JsonObject>?, val appended: List<JsonObject>)

private val SESSION_NAME = Regex("session-([0-9]{4}-[0-9]{2}-[0-9]{2})-([A-Za-z0-9]+)\\.json")

/** A difference in any of these between the file and history means the owner changed a set after it was logged. */
private val COMPARED = listOf("weight", "reps", "skipped", "easy", "hard", "easier", "done", "note", "hurt")
private val UPDATED = COMPARED + listOf("variant", "incomplete")
private val IDENTITY = listOf("exercise", "line", "components")

/** A history row an update can change while the plan is made: the rows the files see are the ones earlier files changed. */
private class Slot(var row: JsonObject) {
    val rowId: String get() = (row["row_id"] as JsonPrimitive).content
}

private class FilePlan(val updates: List<Pair<Slot, JsonObject>>, val new: List<JsonObject>)

/**
 * `session_file.plan_import`: [files] by name, each with its text or the
 * read that failed, against [history], which is not changed. With [write]
 * false nothing is applied and the report says what would happen. One bad
 * file never stops the rest.
 */
fun planImport(files: List<Pair<String, ImportSource>>, history: List<JsonObject>, kb: Kb, write: Boolean): ImportPlan {
    val old = history.map(::Slot)
    val byRow = LinkedHashMap<String, Slot>()
    // Row ids are strings wherever this importer wrote them; a row from before them has none.
    for (s in old) if (isPyStr(s.row["row_id"]) && truthy(s.row["row_id"])) byRow[s.rowId] = s
    val loggedAnotherWay = history.filter { !truthy(it["row_id"]) }.mapNotNull { it["date"]?.takeIf(::isPyStr)?.let { d -> (d as JsonPrimitive).content } }.toSet()
    val newRows = mutableListOf<Slot>()
    val report = mutableListOf<String?>()
    var changed = false
    for ((name, source) in files) {
        val (line, plan) = try {
            importOne(name, source, kb, byRow, loggedAnotherWay, write)
        } catch (e: Exception) {
            "$name: not imported (${(e as? PyException)?.pyName ?: e::class.simpleName})" to null
        } catch (e: StackOverflowError) {
            // A file nested deep enough to exhaust a worker thread's stack (on a
            // phone, about 1 MB): that file is refused, and the rest still import.
            "$name: not imported (RecursionError)" to null
        }
        report += line
        if (plan == null || !write) continue
        for ((h, e) in plan.updates) {
            var row = h.row.updated(fieldsToUpdate(h.row, e).associateWith { e[it] ?: JsonNull })
            if ("#added#" in h.rowId) {
                // A rename in Carry keeps the id: the new name and its numbers go together, or the numbers land on the old one.
                row = row.updated((IDENTITY + "matched_kb").associateWith { e[it] ?: JsonNull })
            }
            h.row = row
        }
        for (e in plan.new) Slot(e).also { newRows += it; byRow[it.rowId] = it }
        changed = changed || plan.updates.isNotEmpty()
    }
    if (write && changed) return ImportPlan(report, (old + newRows).map { it.row }, emptyList())
    return ImportPlan(report, null, if (write) newRows.map { it.row } else emptyList())
}

/** `dict.update`: a key already there keeps its place. */
private fun JsonObject.updated(values: Map<String, JsonElement>): JsonObject = JsonObject(LinkedHashMap(this).apply { putAll(values) })

/** `session_file.same`: an unset marker and false, or no note and an empty one, are the same answer; 25 is 25.0. A 0 is a number, not a blank. */
internal fun same(a: JsonElement?, b: JsonElement?): Boolean {
    fun blank(x: JsonElement?) = x == null || x is JsonNull || (isPyBool(x) && !truthy(x)) || (isPyStr(x) && (x as JsonPrimitive).content.isEmpty())
    if (blank(a) || blank(b)) return blank(a) && blank(b)
    return pyEquals(a, b)
}

/**
 * `session_file._fields_to_update`. A change to `hurt` alone copies only
 * `hurt`: the variant and `incomplete` are recomputed from the file with
 * today's code, and a tap on hurt is no reason to re-key an older row.
 */
private fun fieldsToUpdate(h: JsonObject, e: JsonObject): List<String> =
    if (COMPARED.filter { !same(e[it], h[it]) } == listOf("hurt")) listOf("hurt") else UPDATED

/** `session_file._typed`: an added row's name as typed, its line without the rep count in front. */
private fun typed(row: JsonObject): String =
    (row["line"]?.takeIf(::truthy)?.let { pyStrOf(it) } ?: "").replaceFirst(TYPED_PREFIX, "")

// Python's `\d`, which takes every script's digits, not only ASCII's.
private val TYPED_PREFIX = pyRegex("^\\d+ x ")

/** `session_file._identity`: what makes a logged row the same exercise. */
private fun sameIdentity(a: JsonObject, b: JsonObject): Boolean {
    fun line(r: JsonObject) = r["line"]?.takeIf(::truthy) ?: JsonPrimitive("")
    return pyEquals(a["exercise"], b["exercise"]) && pyEquals(line(a), line(b))
}

private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

/**
 * `session_file._import_one`: one file planned, not applied. A logged row
 * changes only when the file is the one it came from (named by the
 * convention, its sheet id agreeing, not backfilled), the row was logged
 * from Carry, and it is still the same exercise. A row blank or missing in
 * the file is reported and left, because a stale or regenerated copy looks
 * exactly like that.
 */
private fun importOne(
    name: String, source: ImportSource, kb: Kb, byRow: Map<String, Slot>, loggedAnotherWay: Set<String>, write: Boolean,
): Pair<String?, FilePlan?> {
    val parsed = try {
        when (source) {
            is ImportSource.Failed ->
                if (source.unreadable) return "$name: not imported, unreadable (${source.error})" to null
                else throw PyException(source.error, false, "reading $name")
            is ImportSource.Text -> pyJsonLoads(source.text)
        }
    } catch (e: PyException) {
        if (!e.valueError) throw e
        return "$name: not imported, unreadable (${e.pyName})" to null
    }
    val problems = validate(parsed)
    if (problems.isNotEmpty()) {
        // The first problem named, so one mistyped row can be found and fixed in Carry.
        val more = if (problems.size > 1) " and ${problems.size - 1} more" else ""
        return "$name: not imported, not a valid session file (${problems[0]}$more)" to null
    }
    var obj = parsed as JsonObject
    val date = (obj["date"] as JsonPrimitive).content
    val klass = (obj["class"] as JsonPrimitive).content
    val sidElement = obj["sheet_id"]
    if (!isPyStr(sidElement) || (sidElement as JsonPrimitive).content != "$date:$klass") {
        return "$name: not imported, its sheet id and contents disagree" to null
    }
    val sid = sidElement.content
    val m = SESSION_NAME.matchEntire(name)
    if (m != null && (date != m.groupValues[1] || klass != m.groupValues[2])) {
        return "$name: not imported, its name and contents disagree" to null
    }
    // An added row with no id is skipped, not the file: the id Carry gives it on the next save would make it a new row.
    val added = (obj["added"] as? JsonArray)?.map { it as JsonObject } ?: emptyList()
    val noId = added.count { !truthy(it["id"]) }
    if (noId > 0) obj = obj.with("added" to JsonArray(added.filter { truthy(it["id"]) }))
    val entries = toHistory(obj, kb).first
    val ids = entries.map { (it["row_id"] as JsonPrimitive).content }
    if (ids.size != ids.toSet().size) return "$name: not imported, two rows share an id; fix it in Carry" to null

    val backfilled = truthy(obj["backfilled"])
    val syncable = m != null && !backfilled
    val updates = mutableListOf<Pair<Slot, JsonObject>>()
    var differ = 0
    for ((e, id) in entries.zip(ids)) {
        val h = byRow[id] ?: continue
        // An added row's name is the owner's too: a rename with every number
        // unchanged still reaches history. What they typed is compared, never
        // what it matched (the match is redone from today's knowledge base),
        // and only where an update can land.
        val formRow = syncable && pyEquals(h.row["source"], JsonPrimitive("form"))
        val renamed = "#added#" in id && formRow && typed(h.row) != typed(e)
        if (!renamed && COMPARED.all { same(e[it], h.row[it]) }) continue
        if ("#added#" !in id && !sameIdentity(h.row, e)) {
            return "$name: not imported, a logged row is now a different exercise in the file; it may have been regenerated" to null
        }
        if (syncable && pyEquals(h.row["source"], JsonPrimitive("form"))) updates += h to e else differ++
    }
    val emitted = ids.toSet()
    if (!backfilled) differ += byRow.count { (rid, h) -> rid.startsWith("$sid#") && rid !in emitted && answered(h.row) }

    var new = entries.filter { (it["row_id"] as JsonPrimitive).content !in byRow }
    val lines = mutableListOf<String>()
    if (noId > 0) lines += "${plural(noId, "added row")} without an id not logged; open it in Carry and save once"
    if (updates.isNotEmpty()) lines += "${if (write) "updated" else "would update"} ${updates.size}"
    if (differ > 0) lines += "${plural(differ, "logged set")} differ from this file and were left as they are"
    if (new.isNotEmpty() && date in loggedAnotherWay && !backfilled) {
        lines += "${new.size} not logged: $date already has sets logged another way; adopt the old history to bring the day into Carry"
        new = emptyList()
    } else if (new.isNotEmpty()) {
        lines += "${if (write) "logged" else "would log"} ${plural(new.size, "set")}"
    }
    return (if (lines.isEmpty()) null else "$name: " + lines.joinToString("; ")) to FilePlan(updates, new)
}

// ------------------------------------------------------------ validate

private const val MAX_WEIGHT = 1000
private const val MAX_REPS = 1000
private val REQUIRED_TOP = listOf("schema", "sheet_id", "date", "class", "sets")
private val REQUIRED_SET = listOf("ref", "line")
private val ISO_DAY = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
private val CLASS_TAG = Regex("[A-Za-z0-9]+")

/**
 * `session_file.validate`: every problem with a loaded session file, worded
 * exactly as the Python words it, values quoted as Python's `repr` writes
 * them. Empty means importable.
 */
internal fun validate(obj: JsonElement): List<String> {
    if (obj !is JsonObject) return listOf("not a JSON object")
    val problems = mutableListOf<String>()
    if (!pyEquals(obj["schema"], JsonPrimitive(SESSION_SCHEMA))) {
        problems += "schema is ${pyRepr(obj["schema"])}, expected ${pyStrRepr(SESSION_SCHEMA)}"
    }
    for (key in REQUIRED_TOP) if (key !in obj) problems += "missing ${pyStrRepr(key)}"
    var sets = obj["sets"]?.takeUnless { it is JsonNull }
    if (sets != null && sets !is JsonArray) {
        problems += "'sets' is not a list"
        sets = null
    }
    for ((i, s) in ((sets as? JsonArray) ?: emptyList()).withIndex()) {
        if (s !is JsonObject) {
            problems += "sets[$i] is not an object"
            continue
        }
        for (key in REQUIRED_SET) if (key !in s) problems += "sets[$i] missing ${pyStrRepr(key)}"
        problems += numberProblems(s, "sets[$i]")
        problems += shapeProblems(s, "sets[$i]")
        // The row id is `<sheet>#<ref>`, so a ref like "added#a1" would make a set pass for an added row.
        if ("ref" in s && (pyIntOf(s["ref"])?.signum() ?: -1) < 0) {
            problems += "sets[$i].ref is ${pyRepr(s["ref"])}, expected a whole number"
        }
    }
    var added = obj["added"]?.takeUnless { it is JsonNull }
    if (added != null && added !is JsonArray) {
        problems += "'added' is not a list"
        added = null
    }
    for ((i, a) in ((added as? JsonArray) ?: emptyList()).withIndex()) {
        if (a !is JsonObject || (if (truthy(a["name"])) pyStrOf(a["name"]) else "").pyStrip().isEmpty()) {
            problems += "added[$i] needs a name"
            continue
        }
        problems += numberProblems(a, "added[$i]")
        problems += shapeProblems(a, "added[$i]")
    }
    // The class is part of every row id, so a `#` in it could make a set's id equal an added row's.
    val klass = obj["class"]
    if ("class" in obj && !(isPyStr(klass) && CLASS_TAG.matches((klass as JsonPrimitive).content))) {
        problems += "class is ${pyRepr(klass)}, expected letters and digits"
    }
    // Strictly YYYY-MM-DD, a real day from year 1, as Python's date.fromisoformat takes it.
    val day = obj["date"]
    val ok = isPyStr(day) && (day as JsonPrimitive).content.let { d ->
        ISO_DAY.matches(d) && try {
            LocalDate.of(d.substring(0, 4).toInt(), d.substring(5, 7).toInt(), d.substring(8).toInt()).year >= 1
        } catch (_: DateTimeException) {
            false
        }
    }
    if (!ok) problems += "date is ${pyRepr(day)}, expected YYYY-MM-DD"
    return problems
}

/** `session_file._number_problems`: a weight or rep count that would reach history and break it. True is refused, and the range refuses NaN and infinity. */
private fun numberProblems(row: JsonObject, where: String): List<String> {
    val out = mutableListOf<String>()
    for ((key, top, whole) in listOf(Triple("weight", MAX_WEIGHT, false), Triple("reps", MAX_REPS, true), Triple("prescribed_reps", MAX_REPS, true))) {
        val v = row[key]?.takeUnless { it is JsonNull } ?: continue
        val n = pyIntOf(v)
        val f = pyFloatOf(v)
        val ok = when {
            n != null -> n.signum() >= 0 && n <= BigInteger.valueOf(top.toLong())
            f != null -> f >= 0 && f <= top && (!whole || f == Math.floor(f))
            else -> false
        }
        if (!ok) out += "$where.$key is ${pyRepr(v)}, expected a ${if (whole) "whole number" else "number"} from 0 to $top, or null"
    }
    val rounds = row["rounds"]?.takeUnless { it is JsonNull }
    if (rounds != null && !(rounds is JsonArray && rounds.size >= 2 &&
            rounds.all { r -> pyIntOf(r)?.let { it.signum() > 0 && it <= BigInteger.valueOf(MAX_REPS.toLong()) } == true })
    ) {
        out += "$where.rounds is ${pyRepr(rounds)}, expected two or more whole numbers, or null"
    }
    return out
}

/** `session_file._shape_problems`: fields copied into history as they stand, of a type the coach can read on every later run. */
private fun shapeProblems(row: JsonObject, where: String): List<String> {
    val out = mutableListOf<String>()
    fun present(key: String) = row[key]?.takeUnless { it is JsonNull }
    for (key in listOf("canonical", "breakdown", "note")) {
        val v = present(key)
        if (v != null && !isPyStr(v)) out += "$where.$key is ${pyRepr(v)}, expected text or null"
    }
    // A marker is stored as bool(marker), so the text "false" would be stored as true.
    for (key in listOf("easy", "hard", "easier", "hurt", "done", "skipped")) {
        val v = present(key)
        if (v != null && !isPyBool(v)) out += "$where.$key is ${pyRepr(v)}, expected true, false or null"
    }
    val comps = present("components")
    if (comps != null && !(comps is JsonArray && comps.all(::isPyStr))) {
        out += "$where.components is ${pyRepr(comps)}, expected a list of names or null"
    }
    val variant = present("variant")
    if (variant != null && variant !is JsonObject) {
        out += "$where.variant is ${pyRepr(variant)}, expected an object or null"
    } else if (variant is JsonObject) {
        for ((k, v) in variant) {
            val scalar = v is JsonNull || isPyStr(v) || pyIntOf(v) != null || pyFloatOf(v) != null
            val words = k == "unknown" && v is JsonArray && v.all(::isPyStr)
            if (!(scalar || words)) out += "$where.variant.$k is ${pyRepr(v)}, expected text, a number or null"
        }
    }
    return out
}
