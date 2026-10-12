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
            fontSize = 19.sp, modifier = Modifier.padding(top = 16.dp),
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
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(changed + held, key = { it.ref }) { SheetRow(it) }
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
                withStyle(SpanStyle(fontSize = 10.sp, color = Look.ChalkDim)) { append(" lb") }
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
        color = Look.Slate, fontFamily = Look.Figure, fontWeight = FontWeight.Bold, fontSize = 9.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp)).background(Look.Amber)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** "hold 40 lb, 1 more clean time before going up", as a picture. */
@Composable
private fun StreakPips(set: ExerciseSet) {
    val needed = set.streakNeeded ?: return
    val done = set.streakDone ?: 0
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
    val (bodyweight, byFeel) = rest.partition { it.suggested == "no weight needed" }
    Column(Modifier.padding(top = 11.dp, bottom = 20.dp)) {
        if (byFeel.isNotEmpty()) {
            Text(
                "By feel: " + byFeel.joinToString(", ") { it.displayName },
                color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.SmallSize,
            )
        }
        if (bodyweight.isNotEmpty()) {
            Text(
                "No weight: " + bodyweight.joinToString(", ") { it.displayName },
                color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.SmallSize,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/** 45.0 reads as "45"; 12.5 keeps its half. Dumbbells come in halves. */
internal fun Double?.asWeight(): String {
    val v = this ?: return "—"
    return if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
