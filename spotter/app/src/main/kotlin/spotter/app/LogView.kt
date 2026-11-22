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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spotter.app.today.Today
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
    // Open while being changed; a row ticked as listed needs nothing more.
    var open by rememberSaveable(row.id) { mutableStateOf(log != null && log != asListed) }
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
            log?.let { Text("Logged: " + summary(it), fontSize = 15.sp, color = ink, fontWeight = FontWeight.SemiBold) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (asListed != null) {
                    Choice("✓ as listed", log == asListed, "${row.shown}: done as listed") {
                        open = false
                        onChange(asListed)
                    }
                }
                // Opens the rack. Nothing is logged until a weight is picked there.
                Choice("Changed", open, "${row.shown}: changed") { open = true }
                if (log != null) {
                    OutlinedButton(
                        onClick = { open = false; onChange(null) },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "${row.shown}: clear" },
                    ) { Text("Clear") }
                }
            }
            if (open) Editor(row, log, onChange)
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, spoken: String, onClick: () -> Unit) {
    val colors = if (selected) ButtonDefaults.buttonColors(containerColor = blue) else ButtonDefaults.outlinedButtonColors()
    if (selected) {
        Button(onClick = onClick, colors = colors, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "$spoken, selected" }) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = spoken }) { Text(label) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Editor(row: SheetRow, log: RowLog?, onChange: (RowLog) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val last = row.lastHeld?.let { " Last time: ${it.label}." }.orEmpty()
        Text("Heaviest dumbbell, lb.$last", fontSize = 15.sp, color = muted)
        // The whole rack, one tap each: thirteen weights are quicker to hit than to step to.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Rack.dumbbells.forEach { lb ->
                Choice("$lb", log?.lb == lb, "${row.shown}: $lb pounds") {
                    onChange(log?.copy(lb = lb) ?: LogDraft.picked(row, lb))
                }
            }
        }
        if (log == null) return@Column
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("One dumbbell", log.count == 1, "${row.shown}: one dumbbell") { onChange(log.copy(count = 1)) }
            Choice("Two", log.count == 2, "${row.shown}: two dumbbells") { onChange(log.copy(count = 2)) }
        }
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
