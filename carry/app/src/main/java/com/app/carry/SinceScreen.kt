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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What the folder adds up to. Read at home, on the couch, not in a hurry —
 * the opposite of the other two screens, which is why this one may spend
 * space on context they cannot.
 *
 * **It does not open with a chart.** Measured against the owner's real
 * history, 26 distinct movements across 32 sets and exactly one of them
 * done twice at the same variant: a weight-over-time line would be a row
 * of disconnected dots, and would read as "you are not progressing" when
 * the truth is that the studio almost never repeats a movement. So the
 * screen leads with what is nearly earned, then with the axis the data is
 * actually dense on — what a movement trains, rather than which movement
 * it was — and then says plainly why there is not more. Without that last
 * part every empty row looks like a broken app.
 */
@Composable
fun SinceScreen(since: Growth.Since, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(Look.Slate).padding(horizontal = 14.dp)) {
        Text(
            if (since.sessions == 1) "1 session" else "${since.sessions} sessions",
            color = Look.Chalk, fontFamily = Look.Name, fontWeight = FontWeight.Bold,
            fontSize = 19.sp, maxLines = 1, modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            span(since), color = Look.ChalkDim, fontFamily = Look.Name,
            fontSize = Look.SmallSize, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp, bottom = 4.dp),
        )

        if (since.empty) {
            Text(
                "Nothing logged yet. This fills in as you save sessions.",
                color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.SmallSize,
                modifier = Modifier.padding(top = 14.dp),
            )
            return@Column
        }

        LazyColumn(Modifier.weight(1f)) {
            if (since.nearBump.isNotEmpty()) {
                item { Band("Close to a bump") }
                items(since.nearBump) { BumpRow(it) }
            }
            if (since.peaks.isNotEmpty()) {
                item { Band("Heaviest, by what it trains") }
                items(since.peaks) { PeakRow(it) }
            }
            if (since.days.isNotEmpty()) {
                item { Band("Every session") }
                items(since.days) { DayRow(it) }
            }
            item { Quiet(since) }
        }
    }
}

private fun span(since: Growth.Since): String {
    val from = since.from?.let(SessionFiles::formatDate)
    val to = since.to?.let(SessionFiles::formatDate)
    val dates = when {
        from == null -> null
        from == to -> from
        else -> "$from – $to"
    }
    val sets = if (since.sets == 1) "1 set" else "${since.sets} sets"
    return listOfNotNull(dates, sets).joinToString(" · ")
}

@Composable
private fun Band(text: String) {
    Text(
        text.uppercase(), color = Look.ChalkFaint, fontFamily = Look.Figure,
        fontSize = 9.5.sp, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

/** The one actionable thing in the data: another clean set moves it. */
@Composable
private fun BumpRow(bump: Growth.Bump) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Look.family(bump.family)?.let { hue ->
            Box(Modifier.width(3.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(hue))
        }
        Text(
            bump.name, color = Look.Chalk, fontFamily = Look.Name, fontSize = Look.NameSize,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(bump.needed.coerceAtMost(MAX_PIPS)) { i ->
                Box(
                    Modifier.size(5.dp).clip(RoundedCornerShape(50))
                        .background(if (i < bump.done) Look.AmberDim else Look.RuleStrong)
                )
            }
        }
        Text(figure(bump.weight), color = Look.Chalk, fontFamily = Look.Figure,
            fontWeight = FontWeight.SemiBold, fontSize = Look.FigureSize)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Look.Rule))
}

/**
 * The heaviest in a family, with the weights that led to it.
 *
 * Bars, not a line: a line between two points a fortnight apart draws a
 * trend that the points do not support, and this app's rule is that a
 * wrong number is worse than a missing one.
 */
@Composable
private fun PeakRow(peak: Growth.Peak) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Look.family(peak.family)?.let { hue ->
            Box(Modifier.width(3.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(hue))
        }
        Text(
            buildAnnotatedString {
                append(peak.family)
                withStyle(SpanStyle(color = Look.ChalkDim)) { append(" · ${peak.best.name}") }
            },
            color = Look.Chalk, fontFamily = Look.Name, fontSize = Look.NameSize,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Trail(peak.trail)
        Text(figure(peak.best.weight), color = Look.Chalk, fontFamily = Look.Figure,
            fontWeight = FontWeight.SemiBold, fontSize = Look.FigureSize)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Look.Rule))
}

/**
 * One past session, named by its heaviest lift.
 *
 * "What did I do on the 27th" is a question about a day, and nothing else
 * on this screen can answer it — the bands above are all cut across days.
 * A session is remembered by its hardest thing, so that is what it is
 * labelled with rather than by a count alone.
 */
@Composable
private fun DayRow(day: Growth.Day) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Look.family(day.best?.family)?.let { hue ->
            Box(Modifier.width(3.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(hue))
        }
        Text(
            buildAnnotatedString {
                append(listOfNotNull(day.classTag, SessionFiles.formatDate(day.date)).joinToString(" · "))
                day.best?.let {
                    withStyle(SpanStyle(color = Look.ChalkDim)) { append("  ${it.name}") }
                }
            },
            color = Look.Chalk, fontFamily = Look.Name, fontSize = Look.NameSize,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text(
            if (day.lifts == 1) "1 lift" else "${day.lifts} lifts",
            color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.SmallSize,
        )
        day.best?.let {
            Text(figure(it.weight), color = Look.Chalk, fontFamily = Look.Figure,
                fontWeight = FontWeight.SemiBold, fontSize = Look.FigureSize)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Look.Rule))
}

@Composable
private fun Trail(weights: List<Double>) {
    val top = weights.maxOrNull() ?: return
    if (top <= 0.0) return
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        weights.forEachIndexed { i, w ->
            val tall = (MIN_BAR + (MAX_BAR - MIN_BAR) * (w / top)).dp
            Box(
                Modifier.width(4.dp).height(tall).clip(RoundedCornerShape(1.dp))
                    .background(if (i == weights.lastIndex) Look.AmberDim else Look.RuleStrong)
            )
        }
    }
}

/**
 * Why there is not more here.
 *
 * Load-bearing, not an apology. The owner has seen rows with no number on
 * them all week; without this they would reasonably conclude the app is
 * broken rather than that the studio never repeats a movement.
 */
@Composable
private fun Quiet(since: Growth.Since) {
    // Withheld rather than guessed. These counts turn on what the runner
    // calls the same exercise, and on files that predate it saying so
    // there is no honest number — on the one band whose whole job is to
    // explain why the numbers are thin, a wrong one would be worse than
    // none.
    val movements = since.movements ?: return
    val repeated = since.repeated ?: return
    Column(Modifier.padding(top = 18.dp, bottom = 24.dp)) {
        Band("Why the coach is often quiet")
        Stat("$movements", "different movements in ${since.sets} sets")
        Stat("$repeated", if (repeated == 1) "you have done twice at the same variant"
             else "you have done more than once at the same variant")
        Text(
            "A bump needs three. Until then the sheet shows the nearest thing you have lifted instead.",
            color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.SmallSize,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Stat(number: String, label: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            number, color = Look.Chalk, fontFamily = Look.Figure, fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp, modifier = Modifier.width(30.dp),
        )
        Text(label, color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.SmallSize,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun figure(weight: Double): String = weight.asWeight()

private const val MAX_PIPS = 10
private const val MIN_BAR = 5.0
private const val MAX_BAR = 20.0
