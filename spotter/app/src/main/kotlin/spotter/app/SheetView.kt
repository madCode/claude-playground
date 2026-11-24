package spotter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.json.Json
import spotter.app.today.Today
import spotter.coach.LogDraft
import spotter.coach.Sheet
import spotter.coach.SheetRow
import spotter.coach.Change
import spotter.coach.Suggestion

private val ink = Color(0xFF16181D)
private val muted = Color(0xFF5B606B)
private val warn = Color(0xFF8A4B00)
private val warnBg = Color(0xFFFFF1DB)

/**
 * The middle of the home screen: today's sheet, or why there isn't one. Rows run in the board's
 * order under their blocks, warnings first; a tap on a row shows why its number is what it is.
 */
@Composable
fun TodayView(
    today: Today,
    onAllowCalendar: () -> Unit,
    onSaveLog: (Today.Showing, LogDraft) -> Unit = { _, _ -> },
    /** A class already started that can still be logged, after the sheet has moved on. */
    earlier: Today.Showing? = null,
) {
    // Which class is being logged, if any, and what has been entered. Both survive rotation and
    // the app being killed, so a half-logged class isn't lost. Tied to the class by name: should
    // another class replace it mid-log, its rows aren't saved under the other class's date.
    var loggingClass by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by rememberSaveable(stateSaver = draftSaver) { mutableStateOf(LogDraft()) }
    fun name(s: Today.Showing) = "${s.date} ${s.className}"
    fun start(s: Today.Showing) {
        draft = LogDraft.from(s.sheet, s.logged)
        loggingClass = name(s)
    }
    // The earlier one first: just after a save both can briefly be the same class, and it is
    // the one that carries the fresh log.
    val logging = listOfNotNull(earlier, today as? Today.Showing).firstOrNull { name(it) == loggingClass }
    if (logging != null) {
        // The lines added on the screen are rows of the sheet it shows, and of the one it saves against.
        val shown = logging.with(draft.added)
        LogView(
            shown,
            draft,
            onChange = { draft = it },
            onSave = {
                loggingClass = null
                onSaveLog(shown, draft)
            },
            onCancel = { loggingClass = null },
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Only a class with something to log is worth the top of the screen.
        earlier?.takeIf { it.sheet.rows.any(LogDraft::loggable) || it.strays.isNotEmpty() || it.logged.isNotEmpty() }?.let { EarlierClass(it) { start(it) } }
        TodayBody(today, onAllowCalendar) { start(it) }
    }
}

// First on the screen: the class just done matters more than the next one's sheet until it is logged.
@Composable
private fun EarlierClass(earlier: Today.Showing, onLog: () -> Unit) {
    val logged = earlier.sheet.rows.count { it.id in earlier.logged } + earlier.strays.size
    if (logged == 0) {
        Column(
            Modifier.fillMaxWidth().background(warnBg, RoundedCornerShape(8.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("${earlier.title} isn't logged yet.", fontSize = 15.sp, color = warn)
            Button(
                onClick = onLog,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Log ${earlier.title}" },
            ) { Text("Log it") }
        }
    } else {
        OutlinedButton(onClick = onLog, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Change the log for ${earlier.title}")
        }
    }
}

@Composable
private fun TodayBody(today: Today, onAllowCalendar: () -> Unit, onLog: (Today.Showing) -> Unit) {
    when (today) {
        Today.Loading -> Text("Reading today's board…", fontSize = 17.sp, color = muted)
        Today.NeedsCalendar -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Spotter reads your calendar to know which class is next. It reads only the titles and times of entries from two days ago to tomorrow, and keeps nothing.",
                fontSize = 17.sp,
                color = ink,
            )
            Button(
                onClick = onAllowCalendar,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Allow the calendar") }
        }
        is Today.Nothing -> Text(today.reason, fontSize = 17.sp, color = ink)
        is Today.Showing -> SheetView(today) { onLog(today) }
    }
}

private val draftSaver = Saver<LogDraft, String>(save = { Json.encodeToString(it) }, restore = { Json.decodeFromString(it) })

@Composable
private fun SheetView(today: Today.Showing, onLog: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            today.title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            modifier = Modifier.semantics { heading() },
        )
        (today.notes + today.sheet.warnings).forEach { Note(it) }
        // Also with nothing to log on this board, when there is something logged to see or clear.
        if (today.sheet.rows.any(LogDraft::loggable) || today.strays.isNotEmpty() || today.logged.isNotEmpty()) {
            val logged = today.sheet.rows.count { it.id in today.logged } + today.strays.size
            val label = if (logged == 0) "Log this class" else "Change the log ($logged logged)"
            Button(
                onClick = onLog,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(label) }
        }
        var block: String? = null
        today.sheet.rows.forEachIndexed { i, row ->
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
            SheetRowView(i, row, today.sheet)
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        color = warn,
        modifier = Modifier.fillMaxWidth().background(warnBg, RoundedCornerShape(8.dp)).padding(10.dp),
    )
}

@Composable
private fun SheetRowView(index: Int, row: SheetRow, sheet: Sheet) {
    // Survives rotation, so an open "why" stays open.
    var open by rememberSaveable(index, row.shown) { mutableStateOf(false) }
    val glance = sheet.glance(row)
    val weighted = row.suggestion is Suggestion.Weight
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = if (open) "hide why" else "show why") { open = !open }
            .semantics { stateDescription = if (open) "why shown" else "why hidden" },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(row.shown, fontSize = 17.sp, color = ink, modifier = Modifier.weight(1f))
                // Capped, so a long reason ("no sound conversion") wraps under itself rather than
                // squeezing the board's words to a few letters a line, at any font size.
                Text(
                    glance,
                    fontSize = if (weighted) 20.sp else 15.sp,
                    fontWeight = if (weighted) FontWeight.Bold else FontWeight.Normal,
                    color = if (weighted) ink else muted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(max = 150.dp).semantics { contentDescription = spoken(row, glance) },
                )
            }
            Text(row.understood, fontSize = 13.sp, color = muted)
            if (open) row.why.forEach { Text("· $it", fontSize = 15.sp, color = ink) }
        }
    }
}

// What TalkBack says for the number: words, not "black up-pointing triangle".
internal fun spoken(row: SheetRow, glance: String): String {
    val s = row.suggestion as? Suggestion.Weight ?: return glance
    val held = when (s.load.count) {
        2 -> "two ${s.load.lb} pound dumbbells"
        1 -> "one ${s.load.lb} pound dumbbell"
        else -> "${s.load.lb} pounds each, count not known"
    }
    val change = when (s.change) {
        Change.UP -> "up from last time"
        Change.DOWN -> "down from last time"
        Change.SAME -> "same as last time"
        Change.NEW -> "new"
        Change.EST -> "an estimate"
    }
    return listOfNotNull(held, change, s.test?.let { "try ${it.lb} if set 1 felt easy" }).joinToString(", ")
}
