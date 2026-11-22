package spotter.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spotter.app.today.Today
import spotter.coach.JournalEntry
import spotter.coach.LogDraft
import spotter.coach.Rack
import spotter.coach.Reading
import spotter.coach.RowLog
import spotter.coach.SheetRow

private val ink = Color(0xFF16181D)
private val muted = Color(0xFF5B606B)
private val blue = Color(0xFF1D4ED8)

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
            color = ink,
            modifier = Modifier.semantics { heading() },
        )
        Text("Leave a row alone if it wasn't done.", fontSize = 15.sp, color = muted)
        var block: String? = null
        today.sheet.rows.forEach { row ->
            if (row.block != block) {
                block = row.block
                row.block?.let {
                    Text(
                        it.replaceFirstChar { c -> c.uppercase() },
                        fontSize = 14.sp,
                        color = muted,
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                    )
                }
            }
            LogRowView(row, today.sheet.glance(row), draft.rows[row.id]) { onChange(draft.with(row.id, it)) }
        }
        if (today.strays.isNotEmpty()) {
            Text(
                "Logged, but not on this board",
                fontSize = 14.sp,
                color = muted,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() },
            )
            Text("The board changed after you logged these. They still count unless you clear them.", fontSize = 15.sp, color = muted)
            today.strays.forEach { stray ->
                val cleared = stray.id in draft.cleared
                StrayView(stray, cleared) { onChange(draft.copy(cleared = if (cleared) draft.cleared - stray.id else draft.cleared + stray.id)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = blue),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Save") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogRowView(row: SheetRow, glance: String, log: RowLog?, onChange: (RowLog?) -> Unit) {
    val asListed = LogDraft.asListed(row)
    // As listed still, when all that was added is how many dumbbells the sheet didn't know.
    val listed = log != null && asListed != null &&
        (log == asListed || (asListed.count == null && log.copy(count = null) == asListed))
    // Open while being changed; a row ticked as listed needs nothing more.
    var open by rememberSaveable(row.id) { mutableStateOf(log != null && !listed) }
    Surface(color = Color.White, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(row.shown, fontSize = 17.sp, color = ink, modifier = Modifier.weight(1f))
                Text(glance, fontSize = 15.sp, color = muted, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 150.dp))
            }
            if (!LogDraft.loggable(row)) {
                Text(if (row.reading is Reading.NotSure) "Not read, so not logged here" else "No weight to log", fontSize = 14.sp, color = muted)
                return@Column
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (asListed != null) {
                    Choice("✓ as listed", listed, "${row.shown}: done as listed") {
                        open = false
                        onChange(asListed.copy(count = asListed.count ?: log?.count))
                    }
                }
                // Opens the rack, and closes it again. Nothing is logged until a weight is picked.
                Choice("Changed", open && !listed, "${row.shown}: changed") { open = !open }
                if (log != null) {
                    OutlinedButton(
                        onClick = { open = false; onChange(null) },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "${row.shown}: clear" },
                    ) { Text("Clear") }
                }
            }
            if (open) Editor(row, log, onChange)
            // Asked whenever it isn't known, ticked or changed: the count is never guessed.
            if (log != null && (open || log.count == null)) {
                if (log.count == null) Text("One dumbbell or two?", fontSize = 15.sp, color = ink)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("One dumbbell", log.count == 1, "${row.shown}: one dumbbell") { onChange(log.copy(count = 1)) }
                    Choice("Two", log.count == 2, "${row.shown}: two dumbbells") { onChange(log.copy(count = 2)) }
                }
            }
            if (open && log != null) Details(row, log, onChange)
            // Last, so the first pick doesn't move the rack under the thumb.
            log?.let { Text("Logged: " + summary(it), fontSize = 15.sp, color = ink, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun StrayView(stray: JournalEntry, cleared: Boolean, onToggle: () -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stray.line, fontSize = 17.sp, color = ink)
            Text(
                if (cleared) "Removed when you save." else "Logged: " + summary(RowLog.of(stray)),
                fontSize = 15.sp,
                color = ink,
                fontWeight = FontWeight.SemiBold,
            )
            Choice("Clear", cleared, "${stray.line}: clear", onToggle)
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, spoken: String, onClick: () -> Unit) {
    // One button whose look changes, not two swapped: TalkBack keeps its place on a tap, and
    // hears "selected" as a state.
    OutlinedButton(
        onClick = onClick,
        colors = if (selected) ButtonDefaults.buttonColors(containerColor = blue) else ButtonDefaults.outlinedButtonColors(),
        modifier = Modifier.heightIn(min = 48.dp).semantics {
            contentDescription = spoken
            this.selected = selected
        },
    ) { Text(label) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Editor(row: SheetRow, log: RowLog?, onChange: (RowLog) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val last = row.lastHeld?.let { " Last time: ${it.count?.let { c -> "$c×" }.orEmpty()}${it.lb}." }.orEmpty()
        Text("Heaviest dumbbell, lb.$last", fontSize = 15.sp, color = muted)
        // The whole rack, one tap each: thirteen weights are quicker to hit than to step to.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Rack.dumbbells.forEach { lb ->
                Choice("$lb", log?.lb == lb, "${row.shown}: $lb pounds") {
                    onChange(log?.copy(lb = lb) ?: LogDraft.picked(row, lb))
                }
            }
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
    Text(label, fontSize = 15.sp, color = muted)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onDown?.invoke() },
            enabled = onDown != null,
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "less, $spoken" },
        ) { Text("−", fontSize = 20.sp) }
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ink, modifier = Modifier.widthIn(min = 72.dp), textAlign = TextAlign.Center)
        OutlinedButton(
            onClick = { onUp?.invoke() },
            enabled = onUp != null,
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "more, $spoken" },
        ) { Text("+", fontSize = 20.sp) }
    }
}

@Composable
private fun Flag(row: SheetRow, label: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    FilterChip(
        selected = on,
        onClick = { onToggle(!on) },
        label = { Text(label) },
        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "${row.shown}: ${label.lowercase()}" },
    )
}

private const val MAX_REPS = 99

internal fun summary(log: RowLog): String = listOfNotNull(
    "${log.count?.let { "$it×" } ?: ""}${log.lb}",
    log.reps?.let { "$it reps" },
    "felt easy".takeIf { log.easy },
    "felt hard".takeIf { log.hard },
    "twinge".takeIf { log.hurt },
    "had to stop".takeIf { log.stopped },
    "easier version".takeIf { log.easier },
).joinToString(" · ")
