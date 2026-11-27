package spotter.coach

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate

/**
 * What the phone writes out for safekeeping and for the scorecard (DESIGN.md, "Backup" and "The
 * scorecard decides"), from the event log alone: each event carries the board's words and what
 * the sheet showed, so no board has to be fetched or kept to export a class.
 */
object Export {
    const val LOG_FILE = "spotter-log.jsonl"
    const val LOG_SCHEMA = "spotter-log/1"

    /**
     * Every file to write, by name: one scorecard session per class that has rows logged, and
     * the whole log, an event a line, corrections and removals included, in the order written.
     */
    fun files(entries: List<JournalEntry>, unreadable: Int = 0): Map<String, String> {
        val written = entries.filter { it.id > 0 }.sortedBy { it.id }
        // Rows the coach itself would refuse (a weight or count no load can have, no exercise)
        // aren't graded either.
        val classes = Journal.live(written).filter(::sound).groupBy { it.date to it.className }
        val sessions = classes.map { (cls, rows) ->
            // Against the day's other classes only, so a file's name never changes later.
            val sameDay = classes.keys.filter { it.first == cls.first }.map { it.second }.toSet()
            sessionFile(cls.first, cls.second, sameDay) to session(cls.first, cls.second, rows).toJson()
        }
        // Said in the file when the store couldn't read some events: a missing correction or
        // removal changes what the sessions say, and the reader should know they may.
        val header = if (unreadable > 0) """{"schema":"$LOG_SCHEMA","unreadable":$unreadable}""" + "\n" else ""
        return sessions.toMap() + (LOG_FILE to header + written.joinToString("") { line(it) + "\n" })
    }

    // Nor a weight added to a row the sheet gives none (a push-up): the sheet never offers a
    // number there, so it would be graded as a blank the coach was never going to fill.
    private fun sound(e: JournalEntry) = e.parts.isNotEmpty() && runCatching { Load(e.lb, e.count) }.isSuccess &&
        Journal.exercise(e, lists)?.let(Sheet::wantsWeight) == true

    // The app's own lists: whether a movement is done without a weight isn't in the entry.
    private val lists by lazy { Vocabulary.starting() }

    private fun plain(className: String) = className.replace(Regex("""[^A-Za-z0-9._-]+"""), "_").trim('_', '.').take(40).ifEmpty { "class" }

    /**
     * "session-2026-11-21-2G.json". A class name is a calendar title when it names no format, so
     * it is cut down to plain characters; when that makes two of [all] the names alike (also by
     * case, for folders that ignore it), or changes the name at all, a mark from the full name
     * keeps one class's file from overwriting another's.
     */
    fun sessionFile(date: LocalDate, className: String, all: Set<String> = setOf(className)): String {
        val name = plain(className)
        val alike = all.count { plain(it).equals(name, ignoreCase = true) } > 1
        val mark = if (alike || name != className) "-" + "%08x".format(className.hashCode()) else ""
        return "session-$date-$name$mark.json"
    }

    /**
     * A class's session in the scorecard's shape from its live [rows], in the order logged. Only
     * logged rows are in it: the scorecard grades logged weighted sets, and a row left alone was
     * never "skipped".
     */
    fun session(date: LocalDate, className: String, rows: List<JournalEntry>): ScorecardSession = ScorecardSession(
        sheetId = "$date:$className",
        date = date.toString(),
        className = className,
        sets = rows.mapIndexed { i, e ->
            val scheme = BoardLine.read(e.line).scheme
            val shown = e.shownLb?.let { lb -> runCatching { Load(lb, e.shownCount) }.getOrNull() }
            ScorecardRow(
                ref = i,
                key = e.key,
                line = e.line,
                canonical = e.parts.singleOrNull()?.movement,
                components = e.parts.takeIf { it.size > 1 }?.map { it.movement },
                // Carry reads one key here: the implement, to tell a weighted row from one that isn't.
                variant = JsonObject(mapOf("implement" to JsonPrimitive(e.parts.firstNotNullOfOrNull { it.variations["implement"] }))),
                prescribedReps = e.ladder?.firstOrNull() ?: e.targetHigh,
                prescribedDurationSec = (scheme as? Scheme.Timed)?.seconds,
                // The board's rounds, as the sheet's session writes them ("6 / 8 / 10 total"); the
                // log keeps them a side each.
                rounds = (scheme as? Scheme.Ladder)?.rounds ?: e.ladder,
                suggested = shown?.label ?: "no number",
                suggestedWeight = shown?.perHand?.toDouble(),
                weight = e.lb.toDouble(),
                reps = e.reps.minOrNull(),
                easy = e.easy,
                hard = e.hard,
                easier = e.easier,
                done = e.targetLow == null && e.ladder == null,
                hurt = e.hurt,
                suggestedCount = shown?.count,
                count = e.count,
                setReps = e.reps.takeIf { it.isNotEmpty() },
            )
        },
    )

    /** One event as a line of the full log: every field, under plain names. */
    fun line(e: JournalEntry): String = raw(e)
        // Valid in JSON, but line breaks to some readers: a line stays a line.
        .replace("\u0085", "\\u0085").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")

    private fun raw(e: JournalEntry): String = Json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("schema", LOG_SCHEMA)
            put("id", e.id)
            put("at", e.at)
            put("kind", e.kind.name)
            put("target", e.target)
            put("date", e.date.toString())
            put("class", e.className)
            put("row", e.row)
            put("line", e.line)
            put("key", e.key)
            putJsonArray("parts") {
                e.parts.forEach { p ->
                    add(buildJsonObject {
                        put("movement", p.movement)
                        putJsonObject("variations") { p.variations.forEach { (k, v) -> put(k, v) } }
                    })
                }
            }
            put("lb", e.lb)
            put("count", e.count)
            put("target_low", e.targetLow)
            put("target_high", e.targetHigh)
            put("ladder", e.ladder?.let { l -> buildJsonArray { l.forEach { add(it) } } } ?: kotlinx.serialization.json.JsonNull)
            putJsonArray("reps") { e.reps.forEach { add(it) } }
            put("hurt", e.hurt)
            put("form_broke", e.formBroke)
            put("stopped", e.stopped)
            put("easy", e.easy)
            put("hard", e.hard)
            put("easier", e.easier)
            put("heaviest_only", e.heaviestOnly)
            put("shown_lb", e.shownLb)
            put("shown_count", e.shownCount)
            put("round_lb", e.roundLb?.let { l -> buildJsonArray { l.forEach { add(it) } } } ?: kotlinx.serialization.json.JsonNull)
        },
    )
}
