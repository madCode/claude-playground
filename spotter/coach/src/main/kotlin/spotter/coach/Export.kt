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
    fun files(entries: List<JournalEntry>): Map<String, String> {
        val written = entries.filter { it.id > 0 }.sortedBy { it.id }
        val sessions = Journal.live(written).groupBy { it.date to it.className }.map { (cls, rows) ->
            sessionFile(cls.first, cls.second) to session(cls.first, cls.second, rows).toJson()
        }
        return sessions.toMap() + (LOG_FILE to written.joinToString("") { line(it) + "\n" })
    }

    /** "session-2026-11-21-2G.json": a class name is a calendar title when it names no format. */
    fun sessionFile(date: LocalDate, className: String): String =
        "session-$date-${className.replace(Regex("""[^A-Za-z0-9._-]+"""), "_").trim('_').take(40).ifEmpty { "class" }}.json"

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
                rounds = e.ladder,
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
    fun line(e: JournalEntry): String = Json.encodeToString(
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
        },
    )
}
