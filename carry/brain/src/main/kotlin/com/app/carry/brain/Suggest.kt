package com.app.carry.brain

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

/**
 * What to lift for one board line, from the history so far: gym-coach's
 * `gym_lib.py` from `Suggestion` to `_suggest`, ported rule for rule. The
 * reasons for each rule are in the Python's docstrings and its PLAN.md and
 * are not repeated here; a comment here says only what the Kotlin cannot.
 * History rows are JSON objects read as the Python reads them (Rows.kt).
 */

internal const val RELATED_LINE_WINDOW_DAYS = 28
internal const val STREAK_THRESHOLD = 3
internal const val EASIER_STREAK = 3
internal const val EASY_STREAK_THRESHOLD = 2
internal const val INCREMENT_LB = 5.0
internal const val EASY_REPS_IN_RESERVE = 2
internal const val EASY_CARRY_DAYS = 28L

/** `gym_lib.IMPLEMENT_LABELS`. */
val IMPLEMENT_LABELS = mapOf(
    "single_dumbbell" to "1 DB", "double_dumbbell" to "2 DB", "kettlebell" to "KB", "ybell" to "YB",
    "medball" to "MB", "band" to "Band", "trx" to "TRX", "bodyweight" to "BW",
)

/** `gym_lib.NO_WEIGHT_IMPLEMENTS`: implements with no weight to track. */
val NO_WEIGHT_IMPLEMENTS = setOf("bodyweight", "band", "trx")

/** `gym_lib.Suggestion`: the sentence for one row, and the numbers behind it. */
data class Suggestion(
    val canonical: String?,
    val components: List<String>?,
    val variant: Variant,
    val muscleGroups: List<String>,
    val implement: String?,
    val head: String,
    val claims: List<String> = emptyList(),
    val related: String? = null,
    val patternNote: String? = null,
    val missNote: String? = null,
    val weight: Double? = null,
    val previousWeight: Double? = null,
    val streakDone: Int? = null,
    val streakNeeded: Int? = null,
)

// ------------------------------------------------------------ reading rows

/** `gym_lib.is_real_set`: not skipped, and a weight that is not 0. */
internal fun isRealSet(h: Row) = !h.flag("skipped") && h.num("weight").let { it != null && it != 0.0 }

/** `gym_lib._is_complete`. */
internal fun isComplete(h: Row) = !h.flag("skipped") && !h.flag("incomplete")

/** `gym_lib._hurt`. */
internal fun hurt(h: Row) = h.flag("hurt")

private val BREAKDOWN_PHRASES = mapOf("fail" to "didn't finish", "knees" to "dropped to knees", "modified" to "modified", "half" to "half")

private fun breakdownPhrase(b: String) = BREAKDOWN_PHRASES[b] ?: b

/** A number as Python's `str()` writes it: the JSON as stored, so 8 stays "8" and 8.0 "8.0". */
private fun Row.raw(key: String): String = (this[key] as? JsonPrimitive)?.content ?: "None"

private fun Row.weight(): Double = num("weight")!!
private fun Row.date(): String = str("date")!!

/** `gym_lib._entry_variant`. */
internal fun entryVariant(h: Row, kb: Kb): Variant = variantFromDict(h.obj("variant"), h.str("exercise"), kb)

/** `gym_lib._entry_variant_for_comparison`: the stored setup at the reps actually done. */
internal fun entryVariantForComparison(h: Row, kb: Kb): Variant =
    entryVariant(h, kb).copy(reps = h.int("reps") ?: h.int("prescribed_reps"))

/** `gym_lib._entry_key`. */
internal fun entryKey(h: Row, kb: Kb): List<Any?> =
    variantKey(h.str("exercise"), h.strings("components")?.takeIf { it.isNotEmpty() }, entryVariant(h, kb))

private fun Row.hasComponents() = flag("components")

/**
 * `gym_lib.read_today`: each row's movement and setup re-read from its own
 * line with today's vocabulary, as copies, then combo parts credited.
 */
fun readToday(history: List<Row>, kb: Kb): List<Row> {
    val matched = HashMap<String, MatchedLine?>()
    val out = mutableListOf<Row>()
    for (h in history) {
        val line = (h.str("line") ?: "").pyStrip().trimStart('-', '*', '•').pyStrip()
        if (line.isEmpty()) {
            out += h
            continue
        }
        if (line !in matched) matched[line] = kb.matchLine(line)
        val ml = matched[line]
        if (ml == null) {
            out += h
            continue
        }
        var variant = variantJson(ml.variant)
        if (h.num("weight").let { it != null && it != 0.0 } && ml.variant.implement in NO_WEIGHT_IMPLEMENTS) {
            variant = variant.with("implement" to JsonNull)
        }
        out += h.with("exercise" to json(ml.canonical), "components" to json(ml.components), "variant" to variant)
    }
    return creditComboParts(out, kb)
}

/**
 * `gym_lib._credit_combo_parts`: a finished, loaded combo set also counts
 * as a set of each weighted part, as a copy right after it, unless it is
 * lighter than that part's last weight at the same setup.
 */
internal fun creditComboParts(history: List<Row>, kb: Kb): List<Row> {
    fun groups(row: Row): List<Any?> {
        val key = entryKey(row, kb)
        return listOf(key, listOf(key, row.num("prescribed_reps"), row.obj("variant")?.str("intensity")))
    }
    val order = history.indices.sortedBy { history[it].str("date") ?: "" }
    val credited = HashMap<Int, MutableList<Row>>()
    val last = HashMap<Any?, Double>()
    for (i in order) {
        val h = history[i]
        if (!h.hasComponents()) {
            if (h.flag("exercise") && h.num("weight") != null && !h.flag("skipped")) {
                for (g in groups(h)) last[g] = h.weight()
            }
            continue
        }
        val v = h.obj("variant") ?: JsonObject(emptyMap())
        val line = (h.str("line") ?: "").pyStrip().trimStart('-', '*', '•').pyStrip()
        val w = h.num("weight")
        val implement = v.str("implement")
        if (line.isEmpty() || w == null || w == 0.0 || !isComplete(h) || h.flag("breakdown") || hurt(h) ||
            implement == null || implement in NO_WEIGHT_IMPLEMENTS
        ) continue
        val components = h.strings("components")!!
        for (part in components) {
            val entry = kb[part]
            if (entry == null || entry.implement in NO_WEIGHT_IMPLEMENTS) continue
            val pv = comboPartVariant(line, components, part, kb) ?: continue
            val row = h.with(
                "exercise" to json(part), "components" to JsonNull, "variant" to variantJson(pv),
                "easy" to json(false), "credited_from_combo" to json(true),
            )
            val gs = groups(row)
            if (gs.any { g -> last[g]?.let { w < it } == true }) continue
            for (g in gs) last[g] = w
            credited.getOrPut(i) { mutableListOf() } += row
        }
    }
    val out = mutableListOf<Row>()
    for ((i, h) in history.withIndex()) {
        out += h
        out += credited[i] ?: emptyList()
    }
    return out
}

/** `gym_lib._entry_reps`: replied reps, else the prescribed ones. */
private fun entryReps(h: Row): Int? = h.int("reps") ?: h.int("prescribed_reps")

/** `gym_lib._usable_reps`: never from a breakdown set. */
private fun usableReps(h: Row): Int? = if (h.flag("breakdown")) null else entryReps(h)

private fun muscleGroupsFor(ml: MatchedLine, kb: Kb): List<String> {
    if (!ml.components.isNullOrEmpty()) {
        val groups = mutableListOf<String>()
        for (c in ml.components) for (g in kb[c]?.muscleGroups ?: emptyList()) if (g !in groups) groups += g
        return groups
    }
    return ml.canonical?.let { kb[it] }?.muscleGroups ?: emptyList()
}

private val DIFFERING_ATTRS = listOf("stability", "stance", "arms", "mode", "tempo", "position", "difficulty", "intensity")

/** `gym_lib._differing_words`: how the logged set differed from today's line, grip left out. */
private fun differingWords(target: Variant, logged: Variant): List<String> =
    DIFFERING_ATTRS.filter { target.attr(it) != logged.attr(it) }
        .map { wordFor(it, logged.attr(it)!!, contrast = target.attr(it)) }

/** The implement's label, when the logged side's is known and differs from today's. */
private fun implementWord(logged: Variant, target: Variant): List<String> =
    logged.implement?.takeIf { it != target.implement }?.let { IMPLEMENT_LABELS[it] }?.let { listOf(it) } ?: emptyList()

/** `gym_lib._citation`: "20 lb, no bosu, Fri". */
private fun citation(entry: Row, target: Variant, kb: Kb, today: LocalDate): String {
    val logged = entryVariantForComparison(entry, kb)
    val parts = mutableListOf("${g(entry.weight())} lb")
    parts += implementWord(logged, target)
    parts += differingWords(target, logged)
    val breakdown = entry.el("breakdown")
    if (truthy(breakdown)) {
        parts += breakdownPhrase((breakdown as JsonPrimitive).content)
    } else if (entry.flag("incomplete") && entry.el("reps") != null) {
        parts += "${entry.raw("reps")} reps, unfinished"
    }
    parts += dateLabel(entry.date(), today)
    return parts.joinToString(", ")
}

// --------------------------------------------------------- no-weight (rule 1)

/** `gym_lib._loaded_before`: the heaviest loaded set of this movement at any setup. */
private fun loadedBefore(ml: MatchedLine, history: List<Row>, today: LocalDate): Row? {
    if (ml.components != null || ml.canonical == null || ml.variant.implement == "trx") return null
    return history.filter {
        it.str("exercise") == ml.canonical && !it.hasComponents() && !it.flag("skipped") && it.flag("weight") &&
            date(it.date()) < today
    }.maxByOrNull { it.weight() }
}

/** `gym_lib._no_weight_head`. */
private fun noWeightHead(ml: MatchedLine, kb: Kb, history: List<Row>): String {
    val key = variantKey(ml.canonical, ml.components, ml.variant)
    val sameKey = history.filter { !it.flag("skipped") && entryKey(it, kb) == key }.sortedByDescending { it.date() }
    if (sameKey.isEmpty()) return "no weight needed"
    val last = sameKey[0]
    if (hurt(last)) return "last time: it hurt"
    if (!isComplete(last)) {
        val breakdown = last.el("breakdown")
        if (truthy(breakdown)) return "last time: ${breakdownPhrase((breakdown as JsonPrimitive).content)}"
        if (last.el("reps") != null && last.el("prescribed_reps") != null) {
            return "last time: ${last.raw("reps")} of ${last.raw("prescribed_reps")}"
        }
        return "last time: didn't finish"
    }
    val recent = sameKey.take(EASIER_STREAK)
    if (recent.size >= EASIER_STREAK && recent.all { isComplete(it) && !hurt(it) }) {
        if (recent.all { it.flag("easier") }) return "${EASIER_STREAK}x clean the easier way -- try one as prescribed"
        if (recent.any { it.flag("easier") }) return "mixed form lately -- hold here until it is clean"
        return "full form 3x in a row, push it: faster pace or the harder option"
    }
    return "no weight needed"
}

/** `gym_lib._last_loaded`: the latest finished, unhurt, loaded set of this movement at any setup. */
private fun lastLoaded(ml: MatchedLine, history: List<Row>, today: LocalDate): Row? =
    history.filter {
        it.str("exercise") == ml.canonical && !it.hasComponents() && it.flag("weight") && isComplete(it) &&
            !hurt(it) && date(it.date()) < today
    }.maxByOrNull { it.date() }

/** `gym_lib._ceiling_set`: the lightest failed or hurt set that a start from [source] must stay under. */
private fun ceilingSet(canonical: String?, source: Row, history: List<Row>, today: LocalDate): Row? =
    history.filter { h ->
        if (h.hasComponents() || !h.flag("weight") || (isComplete(h) && !hurt(h))) return@filter false
        val time = date(h.date())
        if (time >= today) return@filter false
        if (h.str("exercise") == canonical && daysBetween(time, today) <= RELATED_LINE_WINDOW_DAYS) return@filter true
        h.str("exercise").let { it == canonical || it == source.str("exercise") } && h.date() > source.date()
    }.minByOrNull { it.weight() }

private val CITED = pyRegex("\\(last: .*? (\\d+(?:\\.\\d+)?) lb")

/** `gym_lib._pattern_note_limit`: the most a struggle or hold note lets a start or a bare line say. */
private fun patternNoteLimit(note: Triple<String, String, String?>?): Double? {
    if (note == null || note.first !in setOf("back_off", "hold")) return null
    val limit = CITED.find(note.second)?.groupValues?.get(1)?.toDouble() ?: return null
    return if (note.first == "back_off") stepDown(limit) else limit
}

/** `gym_lib._step_down`: one dumbbell under [w], never rounded up. */
private fun stepDown(w: Double): Double = DUMBBELL_LB.lastOrNull { it < w }?.toDouble() ?: w

/** `gym_lib._why_capped`. */
private fun whyCapped(h: Row, today: LocalDate): String {
    val time = dateLabel(h.date(), today)
    return if (hurt(h)) "$time's ${g(h.weight())} lb that hurt" else "$time's unfinished ${g(h.weight())} lb"
}

// ------------------------------------------------- same-variant-key (rule 2)

/** `gym_lib.DUMBBELL_LB`. */
val DUMBBELL_LB = listOf(3, 5, 8, 10, 12, 15, 20, 25, 30, 35, 40, 45, 50)
private const val REP_ESTIMATE_MIN = 3
private const val REP_ESTIMATE_MAX = 12
private const val REP_TARGET_MIN = 1
private const val REP_TARGET_MAX = 30

/** `gym_lib.next_dumbbell_up`: the smallest real dumbbell heavier than [w]. */
fun nextDumbbellUp(w: Double): Double {
    for (d in DUMBBELL_LB) if (d > w) return d.toDouble()
    return (Math.floorDiv(w.toInt(), INCREMENT_LB.toInt()) + 1) * INCREMENT_LB
}

/** `gym_lib._round_down_dumbbell`. */
internal fun roundDownDumbbell(w: Double): Double = when {
    w <= DUMBBELL_LB.first() -> DUMBBELL_LB.first().toDouble()
    w <= DUMBBELL_LB.last() -> DUMBBELL_LB.filter { it <= w }.max().toDouble()
    else -> (Math.floorDiv(w.toInt(), 5) * 5).toDouble()
}

/** `gym_lib._round_nearest_dumbbell`: nearest, the heavier on a tie; banker's rounding above the table. */
internal fun roundNearestDumbbell(w: Double): Double = when {
    w <= DUMBBELL_LB.first() -> DUMBBELL_LB.first().toDouble()
    w <= DUMBBELL_LB.last() -> DUMBBELL_LB.minWith(compareBy<Int>({ Math.abs(it - w) }, { -it })).toDouble()
    else -> (Math.rint(w / 5.0).toInt() * 5).toDouble()
}

private data class Source(val w0: Double, val r0: Int, val easy: Boolean)

/** `gym_lib._epley_source`: an easy set counts the reps it had left. */
private fun epleySource(h: Row): Source? {
    val r0 = usableReps(h) ?: return null
    val w0 = h.num("weight") ?: return null
    if (r0 !in REP_ESTIMATE_MIN..REP_ESTIMATE_MAX) return null
    val easy = h.flag("easy")
    return Source(w0, r0 + if (easy) EASY_REPS_IN_RESERVE else 0, easy)
}

private fun epleyEstimate(w0: Double, r0: Int, t: Int): Double = w0 * (30 + r0) / (30 + t)

private const val ESTIMATE_LOOKBACK = 3

/** `gym_lib._estimate_source`: the strongest clean set of the last few, since the last bad one. */
private fun estimateSource(sameKeySorted: List<Row>, t: Int?): Row? {
    val usable = sameKeySorted.filter { epleySource(it) != null }.take(ESTIMATE_LOOKBACK)
    if (usable.isEmpty()) return null
    if (t != null) {
        val clean = mutableListOf<Row>()
        for (h in sameKeySorted) {
            if (!isComplete(h) || hurt(h)) break
            if (h in usable) clean += h
            if (clean.size == usable.size) break
        }
        if (clean.isNotEmpty()) {
            return clean.maxWith(compareBy<Row>({ shownEstimate(it, t) ?: 0.0 }, { convertedLevel(it, t) ?: 0.0 }, { it.flag("easy") }))
        }
    }
    return if (hurt(usable[0])) null else usable[0]
}

/** `gym_lib._shown_estimate`: the weight one set would print. */
private fun shownEstimate(h: Row, t: Int): Double? {
    val (w0, r0, easy) = epleySource(h) ?: return null
    val raw = epleyEstimate(w0, r0, t)
    return if (easy) roundNearestDumbbell(raw) else roundDownDumbbell(raw)
}

/** `gym_lib._dumbbell_step_below`. */
private fun dumbbellStepBelow(w: Double): Double {
    val below = DUMBBELL_LB.filter { it <= w }
    if (below.size >= 2) return (below[below.size - 1] - below[below.size - 2]).toDouble()
    return (DUMBBELL_LB[1] - DUMBBELL_LB[0]).toDouble()
}

private const val STREAK_TOLERANCE_STEPS = 1.0

private fun reachesLevel(converted: Double, level: Double) =
    converted >= level - dumbbellStepBelow(level) * STREAK_TOLERANCE_STEPS

/** `gym_lib._converted_level`: what one set says about today's reps. */
private fun convertedLevel(h: Row, t: Int): Double? {
    val (w0, r0, _) = epleySource(h) ?: return null
    return epleyEstimate(w0, r0, t)
}

/** `gym_lib._converted_streak`: rule 2a's streak across rep schemes. */
private fun convertedStreak(sameKeySorted: List<Row>, t: Int, level: Double): Int {
    var streak = 0
    for (h in sameKeySorted) {
        val unfinished = !isComplete(h) || hurt(h)
        val converted = convertedLevel(h, t)
        if (converted == null) {
            if (unfinished) break
            continue
        }
        if (unfinished || !reachesLevel(converted, level)) break
        streak++
    }
    return streak
}

/** `gym_lib._logged_ceiling`: the heaviest weight finished at this key. */
private fun loggedCeiling(sameKeySorted: List<Row>): Double? =
    sameKeySorted.filter { !it.flag("skipped") && it.num("weight") != null && isComplete(it) }.maxOfOrNull { it.weight() }

private const val MORE_THAN_LIFTED = " — more than you've lifted"

private fun side(ml: MatchedLine) = if ((ml.variant.repsText ?: "").endsWith("total")) " a side" else ""

/** `gym_lib._rep_estimate`: (head, weight) from the set [estimateSource] picks. */
private fun repEstimate(ml: MatchedLine, sameKeySorted: List<Row>, today: LocalDate): Pair<String, Double>? {
    val t = ml.variant.reps ?: return null
    if (t !in REP_TARGET_MIN..REP_TARGET_MAX) return null
    val h = estimateSource(sameKeySorted, t) ?: return null
    val (w0, r0, easy) = epleySource(h)!!
    val raw = epleyEstimate(w0, r0, t)
    val est = if (easy) roundNearestDumbbell(raw) else roundDownDumbbell(raw)
    val shownR0 = if (easy) r0 - EASY_REPS_IN_RESERVE else r0
    val ceiling = loggedCeiling(sameKeySorted)
    val over = if (ceiling != null && est > ceiling) MORE_THAN_LIFTED else ""
    val head = "~${g(est)} lb for $t reps${side(ml)}$over (est. from ${g(w0)} lb x $shownR0" +
        "${if (easy) " easy" else ""}, ${dateLabel(h.date(), today)})"
    return head to est
}

private fun patternOf(ml: MatchedLine, kb: Kb): String? = ml.canonical?.let { kb[it] }?.pattern

/** `gym_lib._carry_cap`: the lowest weight a carried estimate may not exceed. */
private fun carryCap(ml: MatchedLine, kb: Kb, history: List<Row>): Double? {
    var cap: Double? = null
    val pattern = patternOf(ml, kb)
    for ((h, override) in comparableEntries(ml, history)) {
        if (override == "floor_only") continue
        val direction = compare(ml.variant, entryVariantForComparison(h, kb), pattern)
        if (direction == "cant_say") continue
        val capping = direction == "less" || (direction in setOf("same", "more") && !isComplete(h))
        if (capping && (cap == null || h.weight() < cap)) cap = h.weight()
    }
    return cap
}

/** `gym_lib._carry_allowed`. */
private fun carryAllowed(ml: MatchedLine, carried: Double, kb: Kb, history: List<Row>): Boolean {
    val cap = carryCap(ml, kb, history)
    return cap == null || carried <= cap
}

/** `gym_lib._plain_setup`: today's implement, mode and reps, nothing that makes it harder. */
private fun plainSetup(target: Variant) = target.copy(
    stability = "stable", stance = "both", arms = "both", tempo = "normal",
    position = "none", difficulty = "none", intensity = "none",
)

/** `gym_lib._baseline_head`: what the lifter said they normally use, until they have beaten it. */
private fun baselineHead(ml: MatchedLine, kb: Kb, history: List<Row>, baselines: Map<String, Double>): Pair<String, Double>? {
    if (baselines.isEmpty() || ml.components != null || ml.canonical == null) return null
    val weight = baselines[ml.canonical] ?: return null
    for (h in history) {
        if (h.str("exercise") != ml.canonical || h.hasComponents()) continue
        if (h.flag("skipped") || h.num("weight") == null || !isComplete(h)) continue
        if (h.weight() >= weight) return null
    }
    if (compare(ml.variant, plainSetup(ml.variant), patternOf(ml, kb)) !in setOf("same", "more")) return null
    return "you said you normally use ${g(weight)} lb" to weight
}

/** `gym_lib._carried_estimate`: Epley from a recent complete set of this move at a same-or-harder setup. */
private fun carriedEstimate(ml: MatchedLine, kb: Kb, history: List<Row>, today: LocalDate): Pair<String, Double>? {
    val t = ml.variant.reps
    if (ml.components != null || t == null || t !in REP_TARGET_MIN..REP_TARGET_MAX) return null
    val pattern = patternOf(ml, kb)
    val cutoff = today.minusDays(EASY_CARRY_DAYS)
    val key = variantKey(ml.canonical, ml.components, ml.variant)
    val todayIso = today.toString()
    val after = history.filter {
        hurt(it) && it.str("exercise") == ml.canonical && !it.hasComponents() && it.flag("date") && it.date() < todayIso
    }.maxOfOrNull { it.date() } ?: ""
    val candidates = mutableListOf<Pair<Row, Variant>>()
    for (h in history) {
        if (h.flag("skipped") || h.num("weight") == null || h.hasComponents()) continue
        if (h.str("exercise") != ml.canonical || !isComplete(h)) continue
        if (hurt(h) || h.date() <= after) continue
        if (entryKey(h, kb) == key) continue
        val d = date(h.date())
        if (!(cutoff <= d && d < today)) continue
        val logged = entryVariantForComparison(h, kb)
        if (compare(ml.variant, logged, pattern) !in setOf("same", "more")) continue
        candidates += h to logged
    }
    if (candidates.isEmpty()) return null
    val (h, logged) = candidates.maxWith(compareBy({ it.first.flag("easy") }, { it.first.date() }))
    val (w0, r0, easy) = epleySource(h) ?: return null
    val raw = epleyEstimate(w0, r0, t)
    val est = if (easy) roundNearestDumbbell(raw) else roundDownDumbbell(raw)
    val shownR0 = if (easy) r0 - EASY_REPS_IN_RESERVE else r0
    val words = differingWords(ml.variant, logged).take(2)
    val cite = (listOf("${g(w0)} lb x $shownR0${if (easy) " easy" else ""}") + words + dateLabel(h.date(), today)).joinToString(", ")
    val head = if (est > w0) "try ${g(est)} lb (~est. from $cite)" else "~${g(est)} lb for $t reps (est. from $cite)"
    return head to est
}

/** `gym_lib._Advice`: rule 2's sentence and the numbers it is made of. */
private data class Advice(
    val head: String,
    val weight: Double?,
    val previousWeight: Double? = null,
    val streakDone: Int? = null,
    val streakNeeded: Int? = null,
)

/** `gym_lib._failed_at`: the latest same-key set at this weight, if it did not complete. */
private fun failedAt(sameKey: List<Row>, weight: Double?, today: LocalDate): Row? {
    if (weight == null) return null
    val todayIso = today.toString()
    val attempts = sameKey.filter { it.num("weight") == weight && it.flag("date") && it.date() <= todayIso }
    if (attempts.isEmpty()) return null
    val last = attempts.maxBy { it.date() }
    return if (!isComplete(last)) last else null
}

/** `gym_lib._other_reps_lead`: rule 2a's hold, overruled by a stronger set at other reps. */
private fun otherRepsLead(ml: MatchedLine, recent: List<Row>, atToday: List<Row>, today: LocalDate): Advice? {
    val t = ml.variant.reps ?: return null
    if (t !in REP_TARGET_MIN..REP_TARGET_MAX) return null
    data class Candidate(val est: Double, val date: String, val h: Row, val w0: Double, val done: Int, val easy: Boolean)
    val candidates = mutableListOf<Candidate>()
    var usable = 0
    for (h in recent) {
        if (!isComplete(h) || hurt(h)) break
        val (w0, r0, easy) = epleySource(h) ?: continue
        usable++
        if (usable > ESTIMATE_LOOKBACK) break
        val done = if (easy) r0 - EASY_REPS_IN_RESERVE else r0
        if (h in atToday || done == t) continue
        val raw = epleyEstimate(w0, r0, t)
        val est = if (easy) roundNearestDumbbell(raw) else roundDownDumbbell(raw)
        candidates += Candidate(est, h.date(), h, w0, done, easy)
    }
    if (candidates.isEmpty()) return null
    val c = candidates.maxWith(compareBy({ it.est }, { it.date }))
    val ceiling = loggedCeiling(recent)
    val over = if (ceiling != null && c.est > ceiling) MORE_THAN_LIFTED else ""
    val head = "~${g(c.est)} lb for $t reps${side(ml)}$over (est. from ${g(c.w0)} lb x ${c.done}" +
        "${if (c.easy) " easy" else ""}, ${dateLabel(c.h.date(), today)})"
    val level = convertedLevel(c.h, t)
    val dots = if (level != null) convertedStreak(recent, t, level) else 0
    if (dots == 0) return Advice(head, c.est)
    return Advice(head, c.est, streakDone = dots, streakNeeded = STREAK_THRESHOLD)
}

/** `gym_lib._same_key_sets`: weighted sets at today's exact key. */
private fun sameKeySets(ml: MatchedLine, kb: Kb, history: List<Row>): List<Row> {
    val key = variantKey(ml.canonical, ml.components, ml.variant)
    return history.filter { !it.flag("skipped") && it.num("weight") != null && entryKey(it, kb) == key }
}

/** `gym_lib._at_todays_reps`: prescribed today's reps, at today's intensity. */
private fun atTodaysReps(ml: MatchedLine, h: Row, kb: Kb): Boolean =
    h.num("prescribed_reps") == ml.prescribedReps?.toDouble() &&
        entryVariantForComparison(h, kb).intensity == ml.variant.intensity

/** `gym_lib._rule_2`: the advice from same-key history, or null to fall through to rule 3. */
private fun rule2(ml: MatchedLine, sameKey: List<Row>, kb: Kb, today: LocalDate): Advice? {
    val atToday = sameKey.filter { atTodaysReps(ml, it, kb) }
    if (atToday.isNotEmpty()) {
        val atTodaySorted = atToday.sortedByDescending { it.date() }
        val last = atTodaySorted[0]
        if (!isComplete(last)) return Advice("hold ${g(last.weight())} lb, didn't finish it last time", last.weight())
        if (hurt(last)) return Advice("hold ${g(last.weight())} lb, it hurt last time", last.weight())
        var streak = 0
        var easyStreak = 0
        val w = last.weight()
        for (h in atTodaySorted) {
            if (!isComplete(h) || hurt(h) || h.weight() != w) break
            streak++
            if (h.flag("easy") && easyStreak == streak - 1) easyStreak++
        }
        if (easyStreak >= EASY_STREAK_THRESHOLD) {
            val newW = nextDumbbellUp(w)
            return Advice("try ${g(newW)} lb (up from ${g(w)}, easy ${easyStreak}x)", newW, previousWeight = w)
        }
        if (streak >= STREAK_THRESHOLD) {
            val newW = nextDumbbellUp(w)
            return Advice("try ${g(newW)} lb (up from ${g(w)}, held ${streak}x)", newW, previousWeight = w)
        }
        val recent = sameKey.sortedByDescending { it.date() }
        val lead = otherRepsLead(ml, recent, atToday, today)
        if (lead != null && lead.weight!! > w) return lead
        val more = STREAK_THRESHOLD - streak
        val t = ml.variant.reps
        val level = if (t != null && t != 0) convertedLevel(last, t) else null
        val dots = if (level != null) convertedStreak(recent, t!!, level) else streak
        return Advice("hold ${g(w)} lb, $more more at this weight before going up", w,
            streakDone = dots, streakNeeded = STREAK_THRESHOLD)
    }

    // Rule 2c: no history at today's exact reps, so the streak is counted through Epley and never bumps.
    val sameKeySorted = sameKey.sortedByDescending { it.date() }
    val last = sameKeySorted[0]
    if (hurt(last) && last.flag("weight")) return Advice("hold ${g(last.weight())} lb, it hurt last time", last.weight())
    val (head, est) = repEstimate(ml, sameKeySorted, today) ?: return null
    val t = ml.variant.reps
    val source = estimateSource(sameKeySorted, t)
    val level = if (source != null) convertedLevel(source, t!!) else null
    val streak = if (level != null) convertedStreak(sameKeySorted, t!!, level) else 0
    if (streak == 0) return Advice(head, est)
    return Advice(head, est, streakDone = streak, streakNeeded = STREAK_THRESHOLD)
}

// --------------------------------------------------- cross-variant (rule 3)

/** `gym_lib._comparable_entries`: (entry, null | "ceiling_only" | "floor_only"). */
private fun comparableEntries(ml: MatchedLine, history: List<Row>): List<Pair<Row, String?>> {
    val out = mutableListOf<Pair<Row, String?>>()
    if (!ml.components.isNullOrEmpty()) {
        val wanted = ml.components.sorted()
        for (h in history) {
            if (h.flag("skipped") || h.num("weight") == null) continue
            val comps = h.strings("components")
            if (!comps.isNullOrEmpty() && comps.sorted() == wanted) out += h to null
            else if (comps.isNullOrEmpty() && h.str("exercise") in ml.components) out += h to "ceiling_only"
        }
    } else {
        for (h in history) {
            if (h.flag("skipped") || h.num("weight") == null) continue
            val comps = h.strings("components")
            if (comps.isNullOrEmpty() && h.str("exercise") == ml.canonical) out += h to null
            else if (!comps.isNullOrEmpty() && ml.canonical in comps) out += h to "floor_only"
        }
    }
    return out
}

/** `gym_lib._rule_3`: "at least" and "at most" from comparable setups, or one "your logs disagree". */
private fun rule3(ml: MatchedLine, kb: Kb, history: List<Row>, today: LocalDate): List<String> {
    val floors = mutableListOf<Pair<Double, Row>>()
    val ceilings = mutableListOf<Pair<Double, Row>>()
    val pattern = patternOf(ml, kb)
    for ((h, override) in comparableEntries(ml, history)) {
        val direction = compare(ml.variant, entryVariantForComparison(h, kb), pattern)
        if (direction == "cant_say") continue
        val complete = isComplete(h) && !hurt(h)
        if (override != "ceiling_only" && complete && direction in setOf("same", "more")) floors += h.weight() to h
        if (override != "floor_only" && direction in setOf("same", "less")) ceilings += h.weight() to h
    }
    if (floors.isNotEmpty() && ceilings.isNotEmpty()) {
        val (fw, fh) = floors.maxBy { it.first }
        val (cw, ch) = ceilings.minBy { it.first }
        if (fw > cw) {
            return listOf("your logs disagree: ${g(fw)} lb (${citation(fh, ml.variant, kb, today)}) " +
                "vs ${g(cw)} lb (${citation(ch, ml.variant, kb, today)})")
        }
    }
    val claims = mutableListOf<String>()
    if (floors.isNotEmpty()) {
        val (w, h) = floors.maxBy { it.first }
        claims += "at least ${g(w)} lb (${citation(h, ml.variant, kb, today)})"
    }
    if (ceilings.isNotEmpty()) {
        val (w, h) = ceilings.minBy { it.first }
        claims += "at most ${g(w)} lb (${citation(h, ml.variant, kb, today)})"
    }
    return claims
}

// ------------------------------------------------------ a start (rule 3a)

/** `gym_lib.SAFE_START_STEPS_DOWN`. */
private const val SAFE_START_STEPS_DOWN = 1

/** `gym_lib._SAYS_UNLOADED`: words a board uses for an unweighted row that the variant tables don't read. */
private val SAYS_UNLOADED = pyRegex(
    "\\b(?:bw|b/w|body\\s*weight|un-?weighted|band(?:ed|s)?|air|prisoner|(?:no|without)\\s+weights?" +
        "|holds?|iso(?:metric)?|pulses?|jump(?:ing|s)?|plyo(?:metric)?|trx)\\b",
    ignoreCase = true,
)

private val ONE_IMPLEMENT = setOf("single_dumbbell", "ybell", "kettlebell", "medball")
private val TWO_IMPLEMENT = setOf("double_dumbbell")
private val ISOLATION_PATTERNS = setOf("fly", "raise", "triceps", "curl", "calf")

private fun onRack(w: Double): Double = DUMBBELL_LB.lastOrNull { it <= w }?.toDouble() ?: w

/** `gym_lib._safe_start`: (head, weight, the failed set it was capped under) for a loaded row nothing else answered. */
private fun safeStart(ml: MatchedLine, kb: Kb, history: List<Row>, today: LocalDate): Triple<String, Double, Row?>? {
    if (ml.components != null) {
        val parts = ml.components.map { c ->
            safeStart(ml.copy(canonical = c, components = null), kb, history, today) ?: return null
        }
        return parts.minByOrNull { it.second }
    }
    if (ml.canonical == null || loadIsAGuess(ml.canonical, ml.variant.implement, kb)) return null
    val entry = kb[ml.canonical]
    if (entry != null && "cardio" in entry.muscleGroups) return null
    val loaded = history.filter { h ->
        isComplete(h) && !hurt(h) && !h.hasComponents() && date(h.date()) < today &&
            h.str("exercise")?.let { kb[it] } != null && carriedLoad(h, kb) &&
            !(h.flag("credited_from_combo") && h.obj("variant")?.str("implement") != ml.variant.implement)
    }
    val groups = entry?.muscleGroups?.take(1).orEmpty()
    val pattern = entry?.pattern?.takeIf { it.isNotEmpty() }
    fun of(h: Row) = kb[h.str("exercise")!!]!!
    var tiers = listOf(
        loaded.filter { it.str("exercise") == ml.canonical },
        loaded.filter { pattern != null && of(it).pattern == pattern },
        loaded.filter {
            groups.isNotEmpty() && of(it).muscleGroups.take(1) == groups &&
                (pattern == null || of(it).pattern == null || of(it).pattern in ISOLATION_PATTERNS)
        },
    )
    if (entry != null && entry.pattern == "core" && !entry.held) tiers = tiers.take(1)
    for (tier in tiers) {
        if (tier.isEmpty()) continue
        val recent = tier.filter { daysBetween(date(it.date()), today) <= RELATED_LINE_WINDOW_DAYS }
        val source = recent.ifEmpty { tier }.maxBy { it.date() }
        var weight = source.weight()
        val sourceImpl = (source.obj("variant")?.str("implement") ?: of(source).implement)
            ?.takeIf { it !in NO_WEIGHT_IMPLEMENTS }
        val targetImpl = ml.variant.implement
        if ((sourceImpl in ONE_IMPLEMENT && !(targetImpl in ONE_IMPLEMENT && ml.variant.arms != "single")) ||
            (sourceImpl == null && targetImpl in TWO_IMPLEMENT)
        ) {
            weight = onRack(weight / 2)
        }
        val done = source.int("reps")?.takeIf { it != 0 } ?: source.int("prescribed_reps")?.takeIf { it != 0 }
        val wanted = ml.prescribedReps
        val scaled = done != null && wanted != null && wanted != 0 && wanted > done
        if (scaled) weight = onRack(weight * (30 + done!!) / (30 + wanted!!))
        val ceiling = ceilingSet(ml.canonical, source, history, today)
        var cap = ceiling
        if (cap != null && weight >= cap.weight()) weight = cap.weight() else cap = null
        repeat(if (scaled && cap == null) 0 else SAFE_START_STEPS_DOWN) { weight = stepDown(weight) }
        weight = maxOf(weight, minOf(DUMBBELL_LB[0].toDouble(), source.weight()))
        if (ceiling != null && weight >= ceiling.weight()) return null
        val how = sourceImpl?.takeIf { it != ml.variant.implement }?.let { IMPLEMENT_LABELS[it] }?.let { ", $it" } ?: ""
        val head = "start ~${g(weight)} lb (${source.str("exercise")} ${g(source.weight())} lb$how, " +
            "${dateLabel(source.date(), today)})"
        return Triple(head, weight, cap)
    }
    return null
}

// ---------------------------------------------------------- related (rule 4)

/** `gym_lib._carried_load`: a set that is evidence about a weight, for the lines that cross movements. */
private fun carriedLoad(h: Row, kb: Kb): Boolean =
    h.flag("weight") && !loadIsAGuess(h.str("exercise"), h.obj("variant")?.str("implement"), kb)

/** `gym_lib._load_is_a_guess`: a bodyweight core move that only reads as loaded because the line says "dumbbell". */
private fun loadIsAGuess(canonical: String?, implement: String?, kb: Kb): Boolean {
    val e = canonical?.let { kb[it] } ?: return false
    return e.pattern == "core" && e.implement in NO_WEIGHT_IMPLEMENTS && implement == null && !e.held
}

/** `gym_lib._related_line`: the heaviest recent set of another movement in this pattern, when heavier than the head. */
private fun relatedLine(ml: MatchedLine, kb: Kb, history: List<Row>, headWeight: Double?, today: LocalDate): String? {
    if (ml.components != null || loadIsAGuess(ml.canonical, ml.variant.implement, kb)) return null
    val pattern = patternOf(ml, kb)?.takeIf { it.isNotEmpty() } ?: return null
    val candidates = history.filter { h ->
        isComplete(h) && !hurt(h) && !h.hasComponents() && h.str("exercise") != ml.canonical &&
            h.str("exercise")?.let { kb[it] }?.pattern == pattern && carriedLoad(h, kb)
    }
    if (candidates.isEmpty()) return null
    val recent = candidates.filter { daysBetween(date(it.date()), today) <= RELATED_LINE_WINDOW_DAYS }
    val best = recent.ifEmpty { candidates }.maxWith(compareBy({ it.weight() }, { it.date() }))
    if (headWeight != null && !(best.weight() > headWeight)) return null
    val logged = entryVariantForComparison(best, kb)
    val words = implementWord(logged, ml.variant) + differingWords(ml.variant, logged).take(2)
    return (listOf("related: ${best.str("exercise")} ${g(best.weight())} lb") + words + dateLabel(best.date(), today)).joinToString(", ")
}

// ------------------------------------------------- a big miss on the movement

private const val BIG_MISS_REPS = 3
private const val BIG_MISS_LOOKBACK_DAYS = 28L

/** `gym_lib._big_miss`: a set 3+ reps short on the movement's last outing, at any setup. */
private fun bigMiss(ml: MatchedLine, kb: Kb, history: List<Row>, today: LocalDate): Pair<String, Row>? {
    if (ml.components != null || ml.canonical == null) return null
    val cutoff = today.minusDays(BIG_MISS_LOOKBACK_DAYS)
    val outings = history.filter {
        it.str("exercise") == ml.canonical && !it.hasComponents() && !it.flag("skipped") && it.flag("date") &&
            date(it.date()).let { d -> cutoff <= d && d < today }
    }
    if (outings.isEmpty()) return null
    val lastDay = outings.maxOf { it.date() }
    val misses = outings.filter {
        it.date() == lastDay && isRealSet(it) && it.isInt("prescribed_reps") && it.isInt("reps") &&
            countsUnambiguously(it.str("line") ?: "")
    }.map { it.int("prescribed_reps")!! - it.int("reps")!! to it }
    if (misses.isEmpty()) return null
    val (by, h) = misses.maxBy { it.first }
    if (by < BIG_MISS_REPS) return null
    val logged = entryVariantForComparison(h, kb)
    val words = implementWord(logged, ml.variant) + differingWords(ml.variant.copy(reps = logged.reps), logged).take(2)
    val setup = if (words.isNotEmpty()) " (${words.joinToString(", ")})" else ""
    return "missed $by of ${h.raw("prescribed_reps")} at ${g(h.weight())} lb on ${dateLabel(h.date(), today)}$setup" to h
}

/** `gym_lib._counts_unambiguously`: the reply's reps and the line's count the same thing. */
private fun countsUnambiguously(line: String): Boolean {
    if (withoutTimeCap(line) != withoutLabel(line)) return false
    val tokens = normalize(withoutLabel(line)).pySplit()
    val head = if ("x" in tokens) tokens.subList(0, tokens.indexOf("x")) else tokens
    if ("each" in head) return false
    if (("alt" in tokens || "alternating" in tokens) && "total" !in head) return false
    return true
}

// ------------------------------------------------- pattern trend (rule 5)

private const val PATTERN_LOOKBACK_DAYS = 28L
private const val PATTERN_DAYS = 3
private val PATTERN_LABELS = mapOf(
    "squat" to "squats", "lunge" to "lunges", "hinge" to "hinges", "horizontal_push" to "chest presses",
    "vertical_push" to "overhead presses", "horizontal_pull" to "rows", "vertical_pull" to "pulldowns",
    "pullover" to "pullovers", "curl" to "curls", "triceps" to "triceps", "raise" to "raises",
    "fly" to "flys", "core" to "weighted core", "carry" to "carries",
)

private fun unfinished(h: Row) = h.flag("incomplete") || h.flag("breakdown")

/** `gym_lib._pattern_days`: most recent first, true for a day a weighted set of the pattern did not finish. */
private fun patternDays(pattern: String, kb: Kb, history: List<Row>, today: LocalDate, hurtCounts: Boolean = false): List<Boolean> {
    val cutoff = today.minusDays(PATTERN_LOOKBACK_DAYS)
    val missedByDay = LinkedHashMap<String, Boolean>()
    for (h in history) {
        if (h.flag("skipped") || !carriedLoad(h, kb) || h.hasComponents()) continue
        val e = h.str("exercise")?.let { kb[it] } ?: continue
        if (e.pattern != pattern) continue
        val d = date(h.date())
        if (!(cutoff <= d && d < today)) continue
        missedByDay[h.date()] = (missedByDay[h.date()] ?: false) || unfinished(h) || (hurtCounts && hurt(h))
    }
    return missedByDay.keys.sortedDescending().map { missedByDay.getValue(it) }
}

private fun lastPatternSet(pattern: String, kb: Kb, history: List<Row>, today: LocalDate): Row? =
    history.filter {
        !it.flag("skipped") && carriedLoad(it, kb) && !it.hasComponents() &&
            it.str("exercise")?.let { e -> kb[e] }?.pattern == pattern && date(it.date()) < today
    }.maxByOrNull { it.date() }

private val UP_FROM = pyRegex("""up from (\d+(?:\.\d+)?)""")
private val FROM = pyRegex("""from (\d+(?:\.\d+)?)""")
private val RULE_2_HOLD = pyRegex("""hold \S+ lb, \d+ more at this weight before going up""")

/** `gym_lib._pattern_note`: (direction, note, new head or null) for this movement pattern, or null. */
private fun patternNote(ml: MatchedLine, kb: Kb, history: List<Row>, head: String, headWeight: Double?, today: LocalDate): Triple<String, String, String?>? {
    if (ml.components != null || loadIsAGuess(ml.canonical, ml.variant.implement, kb)) return null
    val pattern = patternOf(ml, kb)?.takeIf { it.isNotEmpty() } ?: return null
    val days = patternDays(pattern, kb, history, today)
    if (days.isEmpty()) return null
    val label = "${PATTERN_LABELS[pattern] ?: pattern.replace('_', ' ')} lately"
    val last = lastPatternSet(pattern, kb, history, today)
    val cite = last?.let { " (last: ${it.str("exercise")} ${g(it.weight())} lb, ${dateLabel(it.date(), today)})" } ?: ""
    val recent = days.take(PATTERN_DAYS)
    val missed = recent.count { it }

    if (days[0] && missed >= 2) {
        val count = if (missed == recent.size) "the last ${recent.size} times" else "$missed of the last ${recent.size} times"
        val easier = easierOption(ml.variant, pattern)
        if (head.startsWith("try ")) {
            val held = UP_FROM.find(head)
            return Triple("back_off", "$label: unfinished $count, so not going up yet", held?.let { "hold ${it.groupValues[1]} lb for now" })
        }
        if (headWeight != null) {
            val option = if (!easier.isNullOrEmpty()) ", or try it $easier" else ""
            return Triple("back_off", "$label: unfinished $count$option", "go lighter than ${g(headWeight)} lb")
        }
        if (last == null) return null
        val option = if (!easier.isNullOrEmpty()) "try it $easier" else "pick the easier option"
        return Triple("back_off", "$label: unfinished $count$cite, go lighter than that or $option", null)
    }

    if (!days[0] && days.drop(1).take(PATTERN_DAYS).count { it } >= 2) {
        if (headWeight != null) return Triple("hold", "$label: finished last time after some misses, stay at ${g(headWeight)} lb for now", null)
        if (last == null) return null
        return Triple("hold", "$label: finished last time after some misses$cite, don't go heavier than that yet", null)
    }

    val marked = patternDays(pattern, kb, history, today, hurtCounts = true)
    val cleanRun = marked.indexOfFirst { it }.let { if (it < 0) marked.size else it }
    if (cleanRun >= PATTERN_DAYS && cleanRun % PATTERN_DAYS == 0 && !head.startsWith("try ")) {
        if (headWeight == null) return null
        val done = "$label: finished everything the last $PATTERN_DAYS times"
        if (RULE_2_HOLD.matches(head)) {
            return Triple("push", done, "hold ${g(headWeight)} lb · if easy: ${g(nextDumbbellUp(headWeight))} lb")
        }
        val early = if (head.startsWith("hold ")) " early" else ""
        return Triple("push", "$done, one dumbbell up from ${g(headWeight)} lb$early is fine if today feels easy", null)
    }
    return null
}

// --------------------------------------------------------------- entry point

/** `gym_lib.LADDER_OPENER`: a ladder's head says its number is for the first round. */
internal const val LADDER_OPENER = "first round: "

/**
 * `gym_lib.suggest`: what to lift for one board line. A ladder is answered
 * for its opener unless sets at its stored (lowest-rung) reps already exist.
 * [today] comes from the caller, never the clock.
 */
fun suggest(ml: MatchedLine, kb: Kb, history: List<Row>, today: LocalDate, baselines: Map<String, Double> = emptyMap()): Suggestion {
    if (!prescribedRounds(ml.line).isNullOrEmpty() && sameKeySets(ml, kb, history).none { atTodaysReps(ml, it, kb) }) {
        val opener = prescribedReps(ml.line, opener = true)
        val aimed = ml.copy(prescribedReps = opener, variant = ml.variant.copy(reps = opener))
        var s = suggestOne(aimed, kb, history, today, baselines).copy(variant = ml.variant)
        if (s.weight != null) s = s.copy(head = LADDER_OPENER + s.head)
        return s
    }
    return suggestOne(ml, kb, history, today, baselines)
}

/** `gym_lib._suggest`. */
private fun suggestOne(ml: MatchedLine, kb: Kb, history: List<Row>, today: LocalDate, baselines: Map<String, Double>): Suggestion {
    val muscleGroups = muscleGroupsFor(ml, kb)
    val implement = ml.variant.implement

    if (implement in NO_WEIGHT_IMPLEMENTS) {
        var head = noWeightHead(ml, kb, history)
        val loaded = loadedBefore(ml, history, today)
        val last = if (loaded != null) lastLoaded(ml, history, today) else null
        if (last != null && ml.components == null && ml.variant.mode != "hold" &&
            statedImplement(ml.line, ml.canonical!!, kb) == null && SAYS_UNLOADED.find(ml.line) == null
        ) {
            var weight = last.weight()
            var bare = "~${g(weight)} lb, as on ${dateLabel(last.date(), today)} (the board didn't say weighted)"
            val cap = ceilingSet(ml.canonical, last, history, today)
            if (cap != null && weight >= cap.weight()) {
                weight = stepDown(cap.weight())
                bare = "~${g(weight)} lb, a step under ${whyCapped(cap, today)}"
            }
            val limitNote = patternNote(ml, kb, history, "no history yet", null, today)
            val limit = patternNoteLimit(limitNote)
            if (limit != null && weight > limit) {
                weight = limit
                bare = if (limitNote!!.first == "back_off") "~${g(limit)} lb, a step under the ${ml.canonical} misses lately"
                else "~${g(limit)} lb, no heavier than lately"
            }
            return Suggestion(ml.canonical, ml.components, ml.variant, muscleGroups,
                last.obj("variant")?.str("implement"), bare, weight = weight)
        }
        if (loaded != null) head = "$head — note: you used ${g(loaded.weight())} lb on ${dateLabel(loaded.date(), today)}"
        return Suggestion(ml.canonical, ml.components, ml.variant, muscleGroups, implement, head)
    }

    val sameKey = sameKeySets(ml, kb, history)
    var head: String? = null
    var headWeight: Double? = null
    var claims = emptyList<String>()
    var advice = if (sameKey.isNotEmpty()) rule2(ml, sameKey, kb, today) else null
    if (advice != null) {
        head = advice.head
        headWeight = advice.weight
        if ("didn't finish" !in head) {
            val failed = failedAt(sameKey, headWeight, today)
            if (failed != null) head = "$head — note: didn't finish ${g(failed.weight())} lb on ${dateLabel(failed.date(), today)}"
        }
    }
    val carried = carriedEstimate(ml, kb, history, today)
    if (carried != null && carryAllowed(ml, carried.second, kb, history)) {
        if (head == null || (head.startsWith("hold") && "didn't finish" !in head && headWeight != null && carried.second > headWeight)) {
            head = carried.first
            headWeight = carried.second
            advice = null
        }
    }
    if (head == null) {
        val based = baselineHead(ml, kb, history, baselines)
        if (based != null) {
            head = based.first
            headWeight = based.second
        }
    }
    var start: Triple<String, Double, Row?>? = null
    if (head == null) {
        head = "no history yet"
        claims = rule3(ml, kb, history, today)
        if (claims.isEmpty()) start = safeStart(ml, kb, history, today)
    }
    var shown: String = head!!

    var related = relatedLine(ml, kb, history, headWeight, today)
    var note = patternNote(ml, kb, history, shown, headWeight, today)
    if (start != null) {
        shown = start.first
        headWeight = start.second
    }
    if (note != null && note.first == "push" && "it hurt" in shown) note = null
    val failed = "didn't finish" in shown
    var found = if (failed || (note != null && note.first == "back_off")) null else bigMiss(ml, kb, history, today)
    if (found != null) {
        val w = "${g(found.second.weight())} lb"
        val label = dateLabel(found.second.date(), today)
        if (claims.any { w in it && label in it }) found = null
    }
    val miss = found?.first
    if (miss != null) related = null
    if ((miss != null || failed) && note != null && note.first == "push") note = null
    val startCap = start?.third
    if (startCap != null) {
        shown = if (found != null && found.second == startCap) "start ~${g(headWeight!!)} lb, a step under the miss"
        else "start ~${g(headWeight!!)} lb, a step under ${whyCapped(startCap, today)}"
        related = null
    }
    val limit = if (start != null) patternNoteLimit(note) else null
    if (limit != null && headWeight!! > limit) {
        headWeight = limit
        shown = if (note!!.first == "back_off") "start ~${g(limit)} lb, a step under the misses"
        else "start ~${g(limit)} lb, no heavier than lately"
    }
    val patternNoteText = note?.second
    if (note?.third != null) {
        shown = note.third!!
        if (note.first != "push") advice = null
    }
    if (miss != null && shown.startsWith("try ")) {
        val held = FROM.find(shown)
        if (held != null) {
            headWeight = held.groupValues[1].toDouble()
            shown = "hold ${g(headWeight)} lb"
            advice = null
        }
    }
    if (note != null) related = null
    return Suggestion(
        canonical = ml.canonical, components = ml.components, variant = ml.variant,
        muscleGroups = muscleGroups, implement = implement, head = shown, claims = claims, related = related,
        patternNote = patternNoteText, missNote = miss, weight = headWeight,
        previousWeight = advice?.previousWeight, streakDone = advice?.streakDone, streakNeeded = advice?.streakNeeded,
    )
}
