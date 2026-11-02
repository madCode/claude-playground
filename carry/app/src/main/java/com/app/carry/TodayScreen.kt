package com.app.carry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What to carry in. Ordered by what you cannot guess, because the measured
 * budget is about three numbers read once in a lobby:
 *
 *  1. weights that CHANGED, with what they were
 *  2. the other history-backed numbers
 *  3. everything with no number, folded into one tail line
 */
@Composable
fun TodayScreen(
    session: Session,
    // Derived here, from the very date on the line above it, rather than
    // passed in as a verdict someone else reached earlier.
    today: java.time.LocalDate = java.time.LocalDate.now(),
    /** Rows the file lists that this build could not read. Said out loud
     *  rather than silently shown short -- the sheet is what the owner
     *  checks against the board, so a missing line has to be explained
     *  or it reads as the coach not prescribing it. */
    unreadable: Int = 0,
    modifier: Modifier = Modifier,
    /** An older sheet the owner is finishing: "no sheet for today" and
     *  "the most recent" would both be claims about the folder it can't make. */
    older: Boolean = false,
) {
    val day = SessionFiles.dayOf(session.date, today)
    // A pattern note once per movement, on its first row in sheet order, as
    // the coach's own sheet does: the file repeats it on every row of the
    // pattern, and three squats would otherwise say "squats lately" three times.
    val seenNotes = mutableSetOf<String>()
    val sets = session.sets.map { set ->
        val note = set.patternNote?.takeIf { it.isNotBlank() } ?: return@map set
        if (seenNotes.add(note)) set else set.copy(patternNote = null)
    }
    val weighted = sets.filter { it.suggestedWeight != null }
    val changed = weighted.filter { it.change != null }
    val held = weighted.filter { it.change == null }
    val noNumber = sets.filter { it.suggestedWeight == null }
    // A movement with no number of its own, but something the coach found
    // about it, is worth more than its name in a list: "by feel" told the
    // owner nothing they did not already know while the file held "goblet
    // forward lunge 30 lb, 10/10". Capped, because these are rows rather
    // than one concatenated line, and the sheet is read in a single glance.
    // Within the cap a miss comes first, then a floor or cap, since a
    // warning is worth more than a suggestion; they show in sheet order.
    // By position, not by value: two identical rows are still two rows.
    val chosen = noNumber.indices.filter { noNumber[it].coachSaid }
        .sortedBy { i -> noNumber[i].let { if (!it.missNote.isNullOrBlank()) 0 else if (it.claimLines.isNotEmpty()) 1 else 2 } }
        .take(MAX_NOTED).toSet()
    val noted = noNumber.filterIndexed { i, _ -> i in chosen }
    val tail = noNumber.filterIndexed { i, _ -> i !in chosen }

    Column(modifier.fillMaxSize().background(Look.Slate).padding(horizontal = 14.dp)) {
        // On a past sheet the headline is the ANSWER to "why am I looking at
        // this", not the sheet's own name. The owner opens this tab on days
        // they are not training far more often than on days they are, and
        // the screen used to greet them with another day's numbers under the
        // instruction "Carry these in" (owner, 2026-10-15: "I don't really
        // understand why the first page I land on is a non-existent cheat
        // sheet for a past day").
        val stale = day == SessionFiles.Day.PAST
        val sheetName = listOfNotNull(session.classTag, SessionFiles.formatDate(session.date))
            .joinToString(" · ")
        Text(
            if (stale && !older) "No sheet for today" else sheetName,
            color = Look.Chalk, fontFamily = Look.Name, fontWeight = FontWeight.Bold,
            fontSize = Look.TitleSize, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            when {
                older -> "What that sheet suggested"
                stale -> "Showing the most recent: $sheetName"
                else -> "Carry these in"
            },
            color = Look.ChalkDim, fontFamily = Look.Name,
            fontSize = Look.SmallSize, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp, bottom = 10.dp),
        )
        // The other two are genuine anomalies and keep their warning. PAST is
        // not: it is the ordinary state of a rest day, and the old wording
        // ("your folder may not have synced") asserted a cause that is wrong
        // most of the time it appeared.
        when (day) {
            SessionFiles.Day.PAST -> Unit
            SessionFiles.Day.FUTURE -> Warning("This session is dated ahead of today.")
            SessionFiles.Day.UNKNOWN -> Warning("Carry can't tell what day this session is for.")
            SessionFiles.Day.TODAY -> Unit
        }
        SessionFiles.unreadableNote(unreadable)?.let { Warning(it) }
        session.borrowedFrom?.let {
            Text(
                "⚠ No ${session.classTag} intel. This is the $it floor block — check the board.",
                color = Look.BrickInk, fontFamily = Look.Name, fontSize = Look.SmallSize,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            // No key. LogScreen already learned this: two rows sharing a
            // ref throw out of LazyColumn, and SessionPatch's refusal for
            // exactly that file could never be shown because THIS screen
            // renders first -- so the app would crash on every launch with
            // no way to fix it from the phone.
            items(changed + held) { SheetRow(it) }
            items(noted) { NotedRow(it) }
            if (tail.isNotEmpty()) item { TailLine(tail) }
        }
    }
}

@Composable
private fun Warning(text: String) {
    Text(
        "\u26a0 $text", color = Look.BrickInk, fontFamily = Look.Name,
        fontSize = Look.SmallSize, modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun SheetRow(set: ExerciseSet) {
    val change = set.change
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Look.family(set.family)?.let { hue ->
            Box(Modifier.width(3.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(hue))
        }
        NameLine(set, if (change != null) Look.AmberInk else Look.Chalk, Modifier.weight(1f))
        if (change != null) ChangeChip(change) else StreakPips(set)
        Text(
            // One text node, two weights of emphasis: the figure is the
            // content and the unit is a label. They were the same size in
            // the first build and "lb" competed with the number it qualifies.
            buildAnnotatedString {
                append(set.suggestedWeight.asWeight())
                withStyle(SpanStyle(fontSize = 11.5.sp, color = Look.ChalkDim)) { append(" lb") }
            },
            color = if (change != null) Look.Amber else Look.Chalk,
            fontFamily = Look.Figure, fontWeight = FontWeight.SemiBold, fontSize = Look.FigureSize,
        )
    }
    // What the coach said beside the number: a miss last time, and how the
    // movement's pattern has gone. The text sheet always printed both.
    CoachLines(listOfNotNull(set.missNote?.let { it to true }, set.patternNote?.let { it to false }),
        Modifier.padding(start = 11.dp, bottom = 6.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Look.Rule))
}

/** Lines from the coach under a row's name, dim; a warning (a miss) in brick. */
@Composable
private fun CoachLines(lines: List<Pair<String, Boolean>>, modifier: Modifier = Modifier) {
    if (lines.isEmpty()) return
    Column(modifier) {
        lines.filter { it.first.isNotBlank() }.forEach { (text, warning) ->
            Text(
                text.readable(), color = if (warning) Look.BrickInk else Look.ChalkDim, fontFamily = Look.Name,
                fontSize = Look.SmallSize, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

/**
 * `goblet squat · 1 leg · bosu · 20 total`: the name, then the variant
 * words, then the prescription, dim. Beside the name when it all fits on one
 * line; otherwise the qualifiers move, together, to a line under the name.
 *
 * On that line, qualifiers that still don't fit are dropped whole from the
 * end rather than ellipsized: an ellipsis cuts between letters, and "20
 * total" cut to "2…" is a wrong number on a sheet read in one glance. So the
 * prescription gives way first, and the variant words, which explain a
 * weight lighter than the plain move's, last.
 */
@Composable
private fun NameLine(set: ExerciseSet, ink: Color, modifier: Modifier) {
    val name = set.displayName
    val qualifiers = set.variantWords + listOfNotNull(set.sheetPrescription)
    val style = LocalTextStyle.current.merge(TextStyle(fontFamily = Look.Name, fontSize = Look.NameSize))
    val measurer = rememberTextMeasurer()
    // All of it, in pauses: commas where the dots are, which would
    // otherwise be read out as "middle dot". Said once, on the name, however
    // the qualifiers are laid out.
    val spoken = Modifier.semantics {
        contentDescription = (listOf(name.replace(" · ", ", ")) + qualifiers).joinToString(", ")
    }
    BoxWithConstraints(modifier) {
        fun fits(text: AnnotatedString) =
            measurer.measure(text, style, maxLines = 1, softWrap = false).size.width <= constraints.maxWidth

        if (qualifiers.isEmpty() || fits(line(name, qualifiers))) {
            Text(
                line(name, qualifiers), color = ink, style = style,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = spoken,
            )
        } else {
            val shown = (qualifiers.size downTo 1).firstOrNull { n -> fits(below(qualifiers.take(n))) } ?: 1
            Column {
                Text(name, color = ink, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = spoken)
                Text(
                    below(qualifiers.take(shown)), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { hideFromAccessibility() },
                )
            }
        }
    }
}

private fun below(qualifiers: List<String>): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = Look.ChalkDim)) { append(qualifiers.joinToString(" · ")) }
}

private fun line(name: String, qualifiers: List<String>): AnnotatedString = buildAnnotatedString {
    append(name)
    withStyle(SpanStyle(color = Look.ChalkDim)) {
        qualifiers.forEach { append(" · "); append(it) }
    }
}

/** Names the change rather than leaving it to be subtracted. */
@Composable
private fun ChangeChip(change: Double) {
    Text(
        (if (change > 0) "+" else "") + change.asWeight(),
        color = Look.Slate, fontFamily = Look.Figure, fontWeight = FontWeight.Bold, fontSize = 10.5.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp)).background(Look.Amber)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** "hold 40 lb, 1 more clean time before going up", as a picture. */
@Composable
private fun StreakPips(set: ExerciseSet) {
    // Clamped, because this number comes from a file written on another
    // machine. "streak_needed": 2000000000 is sixty bytes of valid JSON
    // and would emit two billion nodes during the first composition of
    // the default tab -- hanging the app on every launch, unfixable from
    // the phone, in the one moment it exists for.
    val needed = set.streakNeeded ?: return
    val done = (set.streakDone ?: 0).coerceAtLeast(0)
    // Past ten, pips cannot tell the truth and are not countable at a
    // glance anyway. Clamping them LIES: `needed` capped at 10 with `done`
    // at 12 draws ten lit pips -- the picture of a bump already earned --
    // for a streak 28 sets from finishing. Say the numbers instead.
    if (needed > MAX_PIPS) {
        Text(
            "$done of $needed", color = Look.ChalkDim,
            fontFamily = Look.Figure, fontSize = 11.sp, maxLines = 1,
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(needed.coerceAtLeast(0)) { i ->
            Box(
                Modifier.size(5.dp).clip(RoundedCornerShape(50))
                    .background(if (i < done) Look.AmberDim else Look.RuleStrong)
            )
        }
    }
}

/**
 * A movement with no weight of its own, under what the coach found about it:
 * `cross-back lunge` over `goblet forward lunge 30 lb, 10/10`, or `hammer
 * curl` over `at least 10 lb (10 lb, single leg, 10/25)` and a miss.
 *
 * Deliberately NOT shaped like [SheetRow]. That row's big right-hand
 * figure is a number earned by this movement's own history; these are
 * guesses sideways from other setups and other movements, and the two must
 * not be mistaken for each other in a lobby. So each number stays inside the
 * sentence that says where it came from, at the size of a note.
 *
 * In the text sheet's order: floors and caps, the miss, the related line,
 * the pattern note.
 */
@Composable
private fun NotedRow(set: ExerciseSet) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Look.family(set.family)?.let { hue ->
                Box(Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(hue))
            }
            Text(
                set.displayName, color = Look.Chalk, fontFamily = Look.Name,
                fontSize = Look.NameSize, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        // Indented to the name, and never squeezed to one line: the weight
        // sits at the END of a related line, so an ellipsis would eat the
        // only part worth carrying in.
        CoachLines(
            set.claimLines.map { it to false } +
                listOfNotNull(set.missNote?.let { it to true }, set.related?.let { it to false },
                    set.patternNote?.let { it to false }),
            Modifier.padding(start = 11.dp),
        )
    }
}

@Composable
private fun TailLine(rest: List<ExerciseSet>) {
    // Capped: these names are concatenated into single Text nodes, and the
    // file comes from another machine. A thousand rows, or one name a
    // megabyte long, would otherwise be laid out as one paragraph on the
    // main thread.
    val (bodyweight, byFeel) = rest.take(MAX_TAIL_NAMES)
        .partition { it.suggested == "no weight needed" }
    Column(Modifier.padding(top = 11.dp, bottom = 20.dp)) {
        if (byFeel.isNotEmpty()) {
            Text(
                "By feel: " + names(byFeel),
                color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.SmallSize,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
        if (bodyweight.isNotEmpty()) {
            Text(
                "No weight: " + names(bodyweight),
                color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.SmallSize,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/**
 * `shoulder press ×2`, not `shoulder press, shoulder press`.
 *
 * A sheet repeats a movement on 42% of days, and measured across the
 * corpus the repeats use the SAME implement 78% of the time and agree on
 * whether a weight is needed at all 84% of the time. So the second mention
 * almost never tells you anything the first did not: what you came to this
 * screen for is which dumbbell to pick up, and that is already answered by
 * which line the name is on. Printing the name twice spends the one thing
 * this screen is short of — space read in a glance — to say nothing.
 *
 * Order is kept, so the names still read in sheet order.
 */
private fun names(sets: List<ExerciseSet>): String =
    sets.map { it.displayName }
        .groupingBy { it }.eachCount()
        .entries.joinToString(", ") { (name, n) -> if (n > 1) "$name ×$n" else name }

/** More than this many clean sets in a row is not a streak anyone is
 *  tracking, and the number arrives from another machine. */
private const val MAX_PIPS = 10

/** Names folded into the tail line; past this nobody is reading them. */
private const val MAX_TAIL_NAMES = 40

/** Three rows of what the coach found is the most a glance can hold beside
 *  the real numbers; the rest of the no-number movements stay in the tail line. */
private const val MAX_NOTED = 3

/** 45.0 reads as "45"; 12.5 keeps its half. Dumbbells come in halves. */
internal fun Double?.asWeight(): String {
    val v = this ?: return "—"
    return if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
