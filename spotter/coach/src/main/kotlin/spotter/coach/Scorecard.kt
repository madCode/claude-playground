package spotter.coach

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

/**
 * One class as a session file in Carry's format (`gym-session/1`), which gym-coach's
 * `scorecard.py` already grades (DESIGN.md, "The scorecard decides"): every field Carry writes,
 * with Carry's types, and weights per dumbbell, never a sum. Spotter's own extras (`count`,
 * `suggested_count`, `set_reps`) are fields Carry's reader ignores.
 */
@Serializable
data class ScorecardSession(
    val schema: String = SCHEMA,
    @SerialName("sheet_id") val sheetId: String,
    val date: String,
    @SerialName("class") val className: String,
    @SerialName("borrowed_from") val borrowedFrom: String? = null,
    val sets: List<ScorecardRow>,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val SCHEMA = "gym-session/1"
        private val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = true }

        /**
         * The session for a class: every exercise row of its [sheet], with what was [logged] for
         * it, by row index. A row with nothing logged keeps a null weight and isn't skipped: on a
         * borrowed board, rows that weren't in the class are left, not missed (trap 9).
         */
        fun from(date: LocalDate, className: String, sheet: Sheet, logged: Map<Int, Logged>, skipped: Set<Int> = emptySet()): ScorecardSession {
            logged.forEach { (i, l) ->
                val row = requireNotNull(sheet.rows.getOrNull(i)) { "no row $i on the sheet" }
                // A set logged against another row would be graded as this row's miss or hit.
                require((row.reading as? Reading.Known)?.key == l.key) { "row $i is ${row.shown}, not ${l.key}" }
            }
            return ScorecardSession(
                sheetId = "$date:$className",
                date = date.toString(),
                className = className,
                borrowedFrom = sheet.borrowedFrom,
                sets = sheet.rows.mapIndexed { i, row -> ScorecardRow.from(i, row, logged[i], i in skipped) },
            )
        }
    }
}

/** One row, with Carry's field names and types. */
@Serializable
data class ScorecardRow(
    val ref: Int,
    /** The exercise's identity, or null when the line was "not sure". */
    val key: String? = null,
    /** The board's own words. */
    val line: String,
    val canonical: String? = null,
    val components: List<String>? = null,
    val variant: JsonObject? = null,
    @SerialName("prescribed_reps") val prescribedReps: Int? = null,
    @SerialName("prescribed_text") val prescribedText: String? = null,
    @SerialName("prescribed_duration_sec") val prescribedDurationSec: Int? = null,
    val rounds: List<Int>? = null,
    /** The sheet's words for the row: the number with its reasons, or why there was none. */
    val suggested: String? = null,
    /** The weight of one dumbbell the sheet showed; null when it showed no number. */
    @SerialName("suggested_weight") val suggestedWeight: Double? = null,
    @SerialName("previous_weight") val previousWeight: Double? = null,
    val related: String? = null,
    val claims: List<String>? = null,
    @SerialName("pattern_note") val patternNote: String? = null,
    @SerialName("miss_note") val missNote: String? = null,
    val family: String? = null,
    @SerialName("streak_done") val streakDone: Int? = null,
    @SerialName("streak_needed") val streakNeeded: Int? = null,
    /** The weight of one dumbbell lifted; null when nothing was logged. */
    val weight: Double? = null,
    /** Reps a side every set reached: the fewest of [setReps]. */
    val reps: Int? = null,
    val easy: Boolean = false,
    val hard: Boolean = false,
    val easier: Boolean = false,
    val done: Boolean = false,
    val hurt: Boolean = false,
    val skipped: Boolean = false,
    val note: String? = null,
    // Spotter's own: how many dumbbells, and the reps of each set.
    @SerialName("suggested_count") val suggestedCount: Int? = null,
    val count: Int? = null,
    @SerialName("set_reps") val setReps: List<Int>? = null,
) {
    companion object {
        fun from(ref: Int, row: SheetRow, logged: Logged?, skipped: Boolean): ScorecardRow {
            val s = row.suggestion
            val known = row.reading as? Reading.Known
            val scheme = row.line.scheme
            return ScorecardRow(
                ref = ref,
                key = known?.key,
                line = row.shown,
                canonical = known?.parts?.singleOrNull()?.movement?.name,
                components = known?.parts?.takeIf { it.size > 1 }?.map { it.movement.name },
                variant = known?.let { k ->
                    // Carry reads one key here: the implement, to tell a weighted row from one that isn't.
                    JsonObject(mapOf("implement" to JsonPrimitive(k.parts.firstNotNullOfOrNull { it.implement })))
                },
                prescribedReps = row.target?.last,
                prescribedDurationSec = (scheme as? Scheme.Timed)?.seconds,
                rounds = (scheme as? Scheme.Ladder)?.rounds,
                suggested = when (s) {
                    is Suggestion.Weight -> (listOf(s.load.label) + s.reasons).joinToString("; ")
                    is Suggestion.NoNumber -> s.reason
                    null -> if (row.reading is Reading.NotSure) "not sure what this is" else "no weight"
                },
                suggestedWeight = (s as? Suggestion.Weight)?.load?.perHand?.toDouble(),
                weight = logged?.load?.perHand?.toDouble(),
                reps = logged?.reps?.minOrNull(),
                done = logged != null && row.target == null,
                hurt = logged?.hurt ?: false,
                skipped = skipped,
                suggestedCount = (s as? Suggestion.Weight)?.load?.count,
                count = logged?.load?.count,
                setReps = logged?.reps?.takeIf { it.isNotEmpty() },
            )
        }
    }
}
