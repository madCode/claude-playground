package spotter.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spotter.app.today.Today
import spotter.coach.BoardLine
import spotter.coach.Count
import spotter.coach.JournalEntry
import spotter.coach.LogDraft
import spotter.coach.Rack
import spotter.coach.Reading
import spotter.coach.RowLog
import spotter.coach.Scheme
import spotter.coach.SheetRow


/**
 * Logging a class: the sheet's rows in board order, each "✓ as listed" or "changed" (the heaviest
 * dumbbell and the reps), with how it felt one tap away. Rows left alone stay unlogged.
 */
@Composable
fun LogView(today: Today.Showing, draft: LogDraft, onChange: (LogDraft) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Log · ${today.title}",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text("Leave a row alone if it wasn't done.", fontSize = 15.sp, color = palette.muted)
        var block: String? = null
        today.sheet.rows.forEach { row ->
            if (row.block != block) {
                block = row.block
                row.block?.let {
                    Text(
                        it.replaceFirstChar { c -> c.uppercase() },
                        fontSize = 14.sp,
                        color = palette.muted,
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                    )
                }
            }
            // Keyed, so that removing a row doesn't hand its open rack to the row below.
            key(row.id) {
                LogRowView(
                    row, today.sheet.glance(row), draft.rows[row.id],
                    // Only a line added on this screen and not yet saved: a saved one is cleared.
                    onRemove = if (row.id in draft.added) ({ onChange(draft.removing(row.id)) }) else null,
                ) { onChange(draft.with(row.id, it)) }
            }
        }
        AddExercise(today, draft, onChange)
        if (today.strays.isNotEmpty()) {
            Text(
                "Logged, but not on this board",
                fontSize = 14.sp,
                color = palette.muted,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() },
            )
            Text("The board changed after you logged these. They still count unless you clear them.", fontSize = 15.sp, color = palette.muted)
            today.strays.forEachIndexed { i, stray ->
                val cleared = stray.id in draft.cleared
                // Said apart from a board row with the same words, and from a twin beside it.
                val twins = today.strays.count { it.line == stray.line }
                val nth = if (twins > 1) " ${today.strays.take(i + 1).count { it.line == stray.line }} of $twins" else ""
                StrayView(stray, cleared, "${stray.line}, not on this board$nth: clear") { onChange(draft.copy(cleared = if (cleared) draft.cleared - stray.id else draft.cleared + stray.id)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = palette.blue),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Save") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    }
}

/**
 * An exercise the board missed, typed as the board would have written it and read the same way:
 * a line Spotter can't name in full, or one with no weight, isn't added, and says why.
 */
@Composable
private fun AddExercise(today: Today.Showing, draft: LogDraft, onChange: (LogDraft) -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    var refused by rememberSaveable { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text("Did one the board missed?", fontSize = 15.sp, color = palette.muted)
        fun add() {
            if (typed.isBlank()) return
            val next = draft.adding(typed.trim(), today.sheet, System.currentTimeMillis())
            val row = next.added.entries.last().let { (id, line) -> today.read(id, line) }
            refused = when {
                // A line with no reps or time is refused too: logged, it would read as a timed
                // set and say nothing to a counted row.
                row == null || row.line.scheme == null -> "That doesn't read as an exercise. Start with the reps, as the board does: 8 x chest press."
                row.reading is Reading.NotSure -> "Spotter isn't sure what that is (${row.understood.removePrefix("not sure: ")}), so it can't be logged yet."
                // A slip of the thumb ("800 x"): logged, it would leave the next sheet with no number.
                (listOfNotNull(row.target?.first, row.target?.last) + (row.line.scheme as? Scheme.Ladder)?.perSide().orEmpty()).any { it !in 1..MAX_REPS } -> "That many reps doesn't look right. Check the number and add it again."
                else -> null
            }
            if (refused == null) {
                typed = ""
                onChange(next)
                // Out of the way of the new row, which is above the field.
                keyboard?.hide()
            }
        }
        OutlinedTextField(
            value = typed,
            // A board line is never longer; nothing is read until "Add".
            onValueChange = { typed = it.take(BoardLine.MAX_LENGTH).replace("\n", " "); refused = null },
            label = { Text("Exercise, as the board says it") },
            placeholder = { Text("8 x chest press") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.fillMaxWidth(),
        )
        refused?.let {
            Text(it, fontSize = 15.sp, color = palette.ink, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        OutlinedButton(onClick = ::add, enabled = typed.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) { Text("Add an exercise") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogRowView(row: SheetRow, glance: String, log: RowLog?, onRemove: (() -> Unit)?, onChange: (RowLog?) -> Unit) {
    val counts = LogDraft.counts(row)
    // On a range, as listed at whichever of its counts was tapped.
    val asListed = LogDraft.asListed(row)?.let { listed -> log?.reps?.takeIf { counts?.contains(it) == true }?.let { listed.copy(reps = it) } ?: listed }
    // As listed still, when all that was added is how many dumbbells the sheet didn't know, or
    // that it felt easy: the weight and the reps are the sheet's.
    // On a row with no weight the word beside the tick is "felt hard": there is nothing to raise.
    val bare = LogDraft.bare(row)
    val plain = log?.copy(easy = false, hard = if (bare) false else log.hard)
    // Where the count is the person's to say, theirs stands over the lift's usual number: a
    // row ticked again, or told "one" after the tick, is still as listed.
    val free = LogDraft.asks(row)
    fun said(listed: RowLog) = if (free) log?.count ?: listed.count else listed.count
    val listed = plain != null && asListed != null &&
        (plain == asListed || (free && plain.copy(count = asListed.count) == asListed))
    // Open while being changed; a row ticked as listed needs nothing more.
    var open by rememberSaveable(row.id) { mutableStateOf(log != null && !listed) }
    Surface(color = palette.card, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(row.shown, fontSize = 17.sp, color = palette.ink, modifier = Modifier.weight(1f))
                Text(glance, fontSize = 15.sp, color = palette.muted, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 150.dp))
            }
            if (!LogDraft.loggable(row)) {
                Text(if (row.reading is Reading.NotSure) "Not read, so not logged here" else "No weight to log", fontSize = 14.sp, color = palette.muted)
                // Logged when the line still read: it counts until cleared, so it is shown.
                if (log != null) {
                    Text("Logged: " + summary(log), fontSize = 15.sp, color = palette.ink, fontWeight = FontWeight.SemiBold)
                    Quiet("Clear", "${row.shown}: clear") { onChange(null) }
                }
                return@Column
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // A range has no tick: a button for each count, so the reps saved are ones that were seen.
                counts?.forEach { reps ->
                    val label = LogDraft.countLabel(row, reps)
                    Choice(label, log?.reps == reps, "${row.shown}: did $label") {
                        // On a row being changed it is that set's reps: the weight picked and
                        // what was said about it stay. Otherwise it was done at the sheet's weight.
                        if (log != null && !listed) onChange(log.copy(reps = reps))
                        else if (bare) onChange(asListed!!.copy(reps = reps, hard = listed && log?.hard == true))
                        else onChange(asListed!!.copy(reps = reps, count = said(asListed), easy = listed && log?.easy == true))
                    }
                }
                if (asListed != null && counts == null) {
                    Choice("✓ as listed", listed, "${row.shown}: done as listed") {
                        open = false
                        // Ticked again, it keeps what was said beside it.
                        onChange(if (bare) asListed.copy(hard = listed && log?.hard == true) else asListed.copy(count = said(asListed), easy = listed && log?.easy == true))
                    }
                }
                // Opens the rack, and closes it again. Nothing is logged until a weight is picked.
                Choice("Changed", open && !listed, "${row.shown}: changed") { open = !open }
                // One tap beside the tick: it is what lets a weight go up after one session.
                if (listed && !open) {
                    if (bare) Flag(row, "Felt hard", log!!.hard) { onChange(log.copy(hard = it)) }
                    else Flag(row, "Felt easy", log!!.easy) { onChange(log.copy(easy = it)) }
                }
                if (log != null) {
                    Quiet("Clear", "${row.shown}: clear") { open = false; onChange(null) }
                }
                if (onRemove != null) {
                    Quiet("Remove", "${row.shown}: remove", onRemove)
                }
            }
            if (open) Editor(row, log, onChange)
            // Asked whenever it isn't known, ticked or changed. Where the lift has a usual
            // number it is chosen already, and stays on show to be changed in one tap.
            val guessed = LogDraft.guessed(row)
            // Only of something held: a push-up ticked as listed has no dumbbells to count.
            if (log != null && log.held && (open || log.count == null || guessed)) {
                if (log.count == null || guessed) Text("One dumbbell or two?", fontSize = 15.sp, color = palette.ink)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("One dumbbell", log.count == 1, "${row.shown}: one dumbbell") { onChange(log.copy(count = 1)) }
                    Choice("Two", log.count == 2, "${row.shown}: two dumbbells") { onChange(log.copy(count = 2)) }
                }
            }
            if (open && log != null) Details(row, log, onChange)
            // Last, so the first pick doesn't move the rack under the thumb.
            log?.let { Text("Logged: " + summary(it), fontSize = 15.sp, color = palette.ink, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun StrayView(stray: JournalEntry, cleared: Boolean, spoken: String, onToggle: () -> Unit) {
    Surface(color = palette.card, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stray.line, fontSize = 17.sp, color = palette.ink)
            Text(
                if (cleared) "Removed when you save." else "Logged: " + summary(RowLog.of(stray)),
                fontSize = 15.sp,
                color = palette.ink,
                fontWeight = FontWeight.SemiBold,
            )
            Choice("Clear", cleared, spoken, onToggle)
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, spoken: String, onClick: () -> Unit) {
    // One button whose look changes, not two swapped: TalkBack keeps its place on a tap, and
    // hears "selected" as a state.
    OutlinedButton(
        onClick = onClick,
        colors = if (selected) ButtonDefaults.buttonColors(containerColor = palette.blue) else ButtonDefaults.outlinedButtonColors(),
        modifier = Modifier.heightIn(min = 48.dp).semantics {
            contentDescription = spoken
            this.selected = selected
        },
    ) { Text(label) }
}

// Undoing is rarer than doing: no outline, so it doesn't compete with the choices beside it.
@Composable
private fun Quiet(label: String, spoken: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = palette.muted),
        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = spoken },
    ) { Text(label) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Editor(row: SheetRow, log: RowLog?, onChange: (RowLog) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val last = row.lastHeld?.let { " Last time: ${it.count?.let { c -> "$c×" }.orEmpty()}${it.lb}." }.orEmpty()
        val rounds = log?.rounds
        if (rounds != null) {
            Text("Each round's dumbbell, lb.$last", fontSize = 15.sp, color = palette.muted)
            // The board's own numbers beside each round; a carried log may have other rounds
            // than this row, and then they go unnumbered.
            val ladder = (row.line.scheme as? Scheme.Ladder)?.takeIf { it.rounds.size == rounds.size }
            val unit = if (ladder?.count == Count.TOTAL) "total" else "reps"
            rounds.forEachIndexed { i, lb ->
                Stepper(
                    "Round ${i + 1}" + ladder?.let { " · ${it.rounds[i]} $unit" }.orEmpty(),
                    "$lb",
                    "${row.shown}: round ${i + 1}, $lb pounds",
                    onDown = Rack.down(lb)?.let { { onChange(log.round(i, it)) } },
                    onUp = Rack.up(lb)?.let { { onChange(log.round(i, it)) } },
                )
            }
            OutlinedButton(
                onClick = { onChange(log.at(log.lb)) },
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "${row.shown}: one weight" },
            ) { Text("One weight") }
            return@Column
        }
        Text("Heaviest dumbbell, lb.$last", fontSize = 15.sp, color = palette.muted)
        // The whole rack, one tap each: thirteen weights are quicker to hit than to step to.
        // Narrow keys, as many across as stay wide enough for a thumb: two rows on most phones.
        BoxWithConstraints {
            val fit = ((maxWidth + RACK_GAP) / (48.dp + RACK_GAP)).toInt().coerceIn(1, RACK_ACROSS)
            // Even rows, not full ones and an orphan: thirteen as 5, 5, 3 where 6 would fit.
            val rows = (Rack.dumbbells.size + fit - 1) / fit
            val across = (Rack.dumbbells.size + rows - 1) / rows
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Rack.dumbbells.chunked(across).forEach { some ->
                    Row(horizontalArrangement = Arrangement.spacedBy(RACK_GAP)) {
                        some.forEach { lb ->
                            val picked = log?.lb == lb
                            OutlinedButton(
                                onClick = { onChange(log?.at(lb) ?: LogDraft.picked(row, lb)) },
                                colors = if (picked) ButtonDefaults.buttonColors(containerColor = palette.blue) else ButtonDefaults.outlinedButtonColors(),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                                    contentDescription = "${row.shown}: $lb pounds"
                                    selected = picked
                                },
                            ) { Text("$lb", maxLines = 1, softWrap = false) }
                        }
                        repeat(across - some.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        // Only once a weight is picked: round one starts from it, and the rest copy it.
        val ladder = row.line.scheme as? Scheme.Ladder
        if (ladder != null && log != null && log.held) {
            OutlinedButton(
                onClick = { onChange(log.eachRound(ladder.rounds.size)) },
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "${row.shown}: a weight for each round" },
            ) { Text("+ Each round") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(row: SheetRow, log: RowLog, onChange: (RowLog) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        log.reps?.let { reps ->
            Stepper(
                "Reps",
                "$reps",
                "${row.shown}: $reps reps",
                onDown = if (reps > 1) ({ onChange(log.copy(reps = reps - 1)) }) else null,
                onUp = if (reps < MAX_REPS) ({ onChange(log.copy(reps = reps + 1)) }) else null,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Flag(row, "Felt easy", log.easy) { onChange(log.copy(easy = it, hard = if (it) false else log.hard)) }
            Flag(row, "Felt hard", log.hard) { onChange(log.copy(hard = it, easy = if (it) false else log.easy)) }
            Flag(row, "Twinge", log.hurt) { onChange(log.copy(hurt = it)) }
            Flag(row, "Had to stop", log.stopped) { onChange(log.copy(stopped = it)) }
            Flag(row, "Easier version", log.easier) { onChange(log.copy(easier = it)) }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, spoken: String, onDown: (() -> Unit)?, onUp: (() -> Unit)?) {
    // The label above, so the buttons and the number have the row's width at any text size.
    Text(label, fontSize = 15.sp, color = palette.muted)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onDown?.invoke() },
            enabled = onDown != null,
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "less, $spoken" },
        ) { Text("−", fontSize = 20.sp) }
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.ink, modifier = Modifier.widthIn(min = 72.dp), textAlign = TextAlign.Center)
        OutlinedButton(
            onClick = { onUp?.invoke() },
            enabled = onUp != null,
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "more, $spoken" },
        ) { Text("+", fontSize = 20.sp) }
    }
}

// The same pill as a choice, tinted when on: something said about the set, not the set itself.
@Composable
private fun Flag(row: SheetRow, label: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    OutlinedButton(
        onClick = { onToggle(!on) },
        colors = if (on) ButtonDefaults.outlinedButtonColors(containerColor = palette.tint, contentColor = palette.onTint) else ButtonDefaults.outlinedButtonColors(contentColor = palette.ink),
        modifier = Modifier.heightIn(min = 48.dp).semantics {
            contentDescription = "${row.shown}: ${label.lowercase()}"
            selected = on
        },
    ) { Text(if (on) "✓ $label" else label) }
}

private const val RACK_ACROSS = 7
// A hair: at 411dp wide, seven keys of 48dp fit with one dp between and not with two.
private val RACK_GAP = 1.dp
private const val MAX_REPS = 99

internal fun summary(log: RowLog): String = listOfNotNull(
    if (log.held) "${log.count?.let { "$it×" } ?: ""}${log.rounds?.joinToString(" / ") ?: log.lb}" else "done",
    log.reps?.let { "$it reps" },
    "felt easy".takeIf { log.easy },
    "felt hard".takeIf { log.hard },
    "twinge".takeIf { log.hurt },
    "had to stop".takeIf { log.stopped },
    "easier version".takeIf { log.easier },
).joinToString(" · ")
