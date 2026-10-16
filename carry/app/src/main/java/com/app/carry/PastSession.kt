package com.app.carry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One past session, read back.
 *
 * "What did I actually lift on the 27th" is the question a person asks
 * about a day, and until this screen existed nothing answered it: the
 * Today tab shows only the current sheet and the Log tab only the current
 * form, and every band on the Since screen is cut across days rather than
 * along one.
 *
 * **A record, not a form.** There is no way to change anything here, by
 * design: the one editable session is today's, on the Log tab. Two ways to
 * write the same file is how a wrong number gets in, and this app's whole
 * premise is that a wrong number in a training history is worse than a
 * missing one because nothing downstream can tell the difference.
 *
 * Which is also why **a blank row is shown blank**. The rows nobody filled
 * in are half the answer to "what did I do that day", and rendering them
 * as a zero, or leaving them out, would be the app inventing the part it
 * does not know.
 */
@Composable
fun PastSession(day: Growth.Day, onBack: () -> Unit, modifier: Modifier = Modifier) {
    // There is no nav library here -- the app is three tabs and nothing
    // else -- so the system back button has nothing to pop and would
    // leave the app from a screen the owner opened by tapping a row.
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize().background(Look.Slate).padding(horizontal = 14.dp)) {
        Back(onBack)
        Text(
            listOfNotNull(day.classTag, SessionFiles.formatDate(day.date)).joinToString(" · "),
            color = Look.Chalk, fontFamily = Look.Name, fontWeight = FontWeight.Bold,
            fontSize = Look.TitleSize, maxLines = 1, overflow = TextOverflow.Ellipsis,
            // A heading, so TalkBack can jump to it rather than reading
            // the back control and the whole row list to find out where
            // it has landed.
            modifier = Modifier.semantics { heading() },
        )
        Text(
            summary(day), color = Look.ChalkDim, fontFamily = Look.Name,
            fontSize = Look.SmallSize, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
        Text(
            // Said once, here, rather than implied by the absence of
            // boxes: a screen full of numbers that cannot be touched
            // reads as broken until something says it is a record.
            if (day.backfilled) {
                "A record of a day logged before Carry. Nothing here can be edited."
            } else {
                "What you saved that day. The only session you can change is today's, on After."
            },
            color = Look.ChalkFaint, fontFamily = Look.Name, fontSize = Look.SmallSize,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )

        if (day.rows.isEmpty()) {
            // A day in the list whose file holds no rows at all. Not
            // reachable from a session the folder produced, but the file
            // is written on another machine and a blank screen looks
            // exactly like a broken one.
            Text(
                "That session's file lists no exercises.",
                color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.SmallSize,
                modifier = Modifier.padding(top = 14.dp),
            )
            return@Column
        }

        LazyColumn(Modifier.weight(1f)) {
            if (day.rows.none { it.weight != null || it.reps != null }) {
                // Every row either blank or skipped. Saying so beats a
                // column of em dashes the owner has to read to the bottom
                // of before concluding the same thing.
                item {
                    Text(
                        "No numbers were entered for this one.",
                        color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.SmallSize,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                    )
                }
            }
            items(day.rows) { RowLine(it) }
            item { Box(Modifier.height(24.dp)) }
        }
    }
}

/** "4 lifts · heaviest 50 · 11 rows", or as much of it as is true. */
private fun summary(day: Growth.Day): String = listOfNotNull(
    if (day.lifts == 1) "1 lift" else "${day.lifts} lifts",
    day.best?.let { "heaviest ${it.weight.asWeight()}" },
    if (day.rows.size == 1) "1 row" else "${day.rows.size} rows",
).joinToString(" · ")

/**
 * The way back to the list.
 *
 * On screen as well as on the back button: the back gesture is the one
 * most people use, but it is invisible, and this screen is opened by a tap
 * on a row rather than by anything that announces itself as navigation.
 * 48dp because it is a touch target, not a label.
 */
@Composable
private fun Back(onBack: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onBack, role = Role.Button)
            .semantics { contentDescription = "Back to every session" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            "‹  Every session",
            color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.SmallSize,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, Look.RuleStrong, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

/**
 * One row as the owner left it.
 *
 * Two lines, the same shape the Log screen uses, so a session reads the
 * same way back as it did going in: names here get long enough to wrap
 * ("single-arm row · split stance") and must not give up room to the
 * numbers.
 */
@Composable
private fun RowLine(row: Growth.Row) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
            // One node per row, so TalkBack reads "bench press, 45 pounds,
            // 10 reps" as a row rather than as four unrelated fragments
            // the listener has to reassemble.
            .semantics(mergeDescendants = true) { contentDescription = spoken(row) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // A hand-typed row gets the neutral edge the Log screen gives
            // it, never a family colour: nothing parsed that row, so there
            // is no family to show and a guessed one would be Carry
            // inventing an exercise's identity.
            val edge = if (row.added) Look.ChalkFaint else Look.family(row.family)
            edge?.let {
                Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(it))
            }
            Text(
                buildAnnotatedString {
                    row.prescription?.let {
                        withStyle(SpanStyle(color = Look.ChalkDim)) { append("$it ") }
                    }
                    if (row.name.isBlank()) {
                        // A row holding numbers and no name. The save path
                        // refuses to write one, so it can only have come
                        // from elsewhere -- and Carry does not name it.
                        withStyle(SpanStyle(color = Look.ChalkFaint)) { append("(unnamed)") }
                    } else {
                        append(row.name)
                    }
                },
                color = if (row.answered) Look.Chalk else Look.ChalkFaint,
                fontFamily = Look.Name, fontSize = Look.NameSize,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text(
                row.weight.asWeight(),
                color = if (row.weight != null) Look.Chalk else Look.ChalkFaint,
                fontFamily = Look.Figure, fontWeight = FontWeight.SemiBold,
                fontSize = Look.FigureSize, maxLines = 1,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 9.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                detail(row),
                color = if (row.answered) Look.ChalkDim else Look.ChalkFaint,
                fontFamily = Look.Name, fontSize = Look.SmallSize,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            row.marks.forEach { Mark(it) }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Look.Rule))
}

/** What is under the name: the reps, the note, or the fact that there is
 *  nothing — never a zero standing in for a blank. */
private fun detail(row: Growth.Row): String = listOfNotNull(
    row.reps?.let { if (it == 1) "1 rep" else "$it reps" },
    when {
        row.skipped -> "didn't do this one"
        !row.answered -> "blank"
        else -> null
    },
    row.note?.takeIf { it.isNotBlank() }?.readable(),
).joinToString(" · ")

/**
 * A feel marker, as the Log screen's chip looks when it is on.
 *
 * Deliberately not a [androidx.compose.foundation.selection.toggleable]:
 * the shape is the same so the row reads back the way it was filled in,
 * but there is nothing to toggle, and offering a target that does nothing
 * is worse than offering none.
 */
@Composable
private fun Mark(text: String) {
    Text(
        text, maxLines = 1,
        color = accent(text), fontFamily = Look.Figure, fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, accent(text), RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
    )
}

/** The Log screen's colours for the same markers. Brick is a warning and
 *  never sits next to amber on one row; amber here is the Log screen's
 *  "easy", which is the one marker that moves a weight. */
private fun accent(mark: String) = when (mark) {
    "done" -> Look.Chalk
    "easy" -> Look.Amber
    "hurt" -> Look.Brick
    else -> Look.ChalkDim
}

/**
 * What the row is called, out loud.
 *
 * The prescription is normally left out — the numbers carry the content
 * and the label should name the movement — but a `done` row has no numbers
 * by definition and its prescription IS the quantity. "low plank, done" is
 * the same sentence for a 30-second hold and a 90-second one, where the
 * sighted reader can see which. Joined to the name rather than listed
 * beside it, because "45 sec of" is written to sit in front of a name and
 * reads as a dangling fragment on its own.
 */
private fun spokenName(row: Growth.Row): String {
    val name = row.name.ifBlank { "unnamed exercise" }
    val asked = row.prescription?.takeIf { row.done && row.weight == null && row.reps == null }
    return asked?.let { "$it $name" } ?: name
}

/**
 * The row for TalkBack, which gets the units the sighted layout leaves to
 * the column it sits in.
 */
private fun spoken(row: Growth.Row): String = listOfNotNull(
    spokenName(row),
    row.weight?.let { "${it.asWeight()} pounds" },
    row.reps?.let { if (it == 1) "1 rep" else "$it reps" },
    if (row.skipped) "skipped" else null,
    if (!row.answered) "not filled in" else null,
    row.marks.filterNot { it == "skip" }.takeIf { it.isNotEmpty() }?.joinToString(", "),
    row.note?.takeIf { it.isNotBlank() }?.readable(),
    if (row.added) "you added this row" else null,
).joinToString(", ")
