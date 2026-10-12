package com.app.carry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
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
    modifier: Modifier = Modifier,
) {
    val day = SessionFiles.dayOf(session.date, today)
    val weighted = session.sets.filter { it.suggestedWeight != null }
    val changed = weighted.filter { it.change != null }
    val held = weighted.filter { it.change == null }
    val noNumber = session.sets.filter { it.suggestedWeight == null }

    Column(modifier.fillMaxSize().background(Look.Slate).padding(horizontal = 14.dp)) {
        Text(
            listOfNotNull(session.classTag, SessionFiles.formatDate(session.date))
                .joinToString(" · "),
            color = Look.Chalk, fontFamily = Look.Name, fontWeight = FontWeight.Bold,
            fontSize = Look.TitleSize, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "Carry these in", color = Look.ChalkDim, fontFamily = Look.Name,
            fontSize = Look.SmallSize, modifier = Modifier.padding(top = 3.dp, bottom = 10.dp),
        )
        // Above the numbers, in the same place and the same voice as the
        // borrowed-block warning: a sheet that is not today's is the one
        // way this screen can put a wrong weight in someone's head, and the
        // likeliest cause is simply that the sync has not run.
        when (day) {
            SessionFiles.Day.PAST -> Warning("This is an older session, not today's. Your folder may not have synced.")
            SessionFiles.Day.FUTURE -> Warning("This session is dated ahead of today.")
            SessionFiles.Day.UNKNOWN -> Warning("Carry can't tell what day this session is for.")
            SessionFiles.Day.TODAY -> Unit
        }
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
            if (noNumber.isNotEmpty()) item { TailLine(noNumber) }
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
        Text(
            set.displayName, color = if (change != null) Look.AmberInk else Look.Chalk,
            fontFamily = Look.Name, fontSize = Look.NameSize, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
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
    Box(Modifier.fillMaxWidth().height(1.dp).background(Look.Rule))
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
    val needed = (set.streakNeeded ?: return).coerceIn(0, MAX_PIPS)
    val done = (set.streakDone ?: 0).coerceIn(0, needed)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(needed) { i ->
            Box(
                Modifier.size(5.dp).clip(RoundedCornerShape(50))
                    .background(if (i < done) Look.AmberDim else Look.RuleStrong)
            )
        }
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

/** 45.0 reads as "45"; 12.5 keeps its half. Dumbbells come in halves. */
internal fun Double?.asWeight(): String {
    val v = this ?: return "—"
    return if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
