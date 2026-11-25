package spotter.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import spotter.app.history.HistoryState
import spotter.app.today.Today
import spotter.coach.LogDraft
import spotter.coach.Progress
import spotter.coach.UnknownWord
import spotter.coach.Movement
import spotter.coach.WordAnswer
import spotter.coach.WordAnswers

/** Where the log is copied to, and how the last copy went. [at] is ready to show ("22 Nov, 14:30"). */
data class BackupState(val folder: String? = null, val at: String? = null, val ok: Boolean = true)

// The log is the person's: where it is kept is said in plain words, with the one thing to do.
@Composable
private fun BackupView(backup: BackupState, onChooseFolder: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val status = when {
            backup.folder == null -> "Your log is only on this phone."
            !backup.ok -> "Couldn't copy your log to ${backup.folder}. Choose the folder again."
            backup.at == null -> "Your log will be copied to ${backup.folder}."
            else -> "Log copied to ${backup.folder} · ${backup.at}"
        }
        Text(status, fontSize = 14.sp, color = if (backup.ok) Color(0xFF5B606B) else Color(0xFF8A4B00))
        OutlinedButton(onClick = onChooseFolder, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (backup.folder == null) "Choose a backup folder" else "Change folder")
        }
    }
}

// What the sheet's numbers are made from besides the log, and what the import didn't take.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryView(history: HistoryState, onImport: () -> Unit, onRemove: () -> Unit, onStop: () -> Unit, onCounts: () -> Unit) {
    var removing by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val grey = Color(0xFF5B606B)
        // Said aloud when it changes: a pick ends on its own time, with nothing tapped.
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (history.read == null) {
                Text("No history imported. Until you log an exercise, its row says \"new: go light\".", fontSize = 14.sp, color = grey)
            } else {
                Text("History imported: ${history.read} rows read.", fontSize = 14.sp, color = grey)
                if (history.left.isNotEmpty()) Text("Left out: ${history.left.joinToString(", ")}.", fontSize = 14.sp, color = grey)
                if (history.countAssumed > 0) {
                    Text("${history.countAssumed} ${if (history.countAssumed == 1) "row" else "rows"} didn't say how many dumbbells: read as the lift's usual number.", fontSize = 14.sp, color = grey)
                }
                history.counts?.let {
                    Text("Your dumbbell counts: $it ${if (it == 1) "lift" else "lifts"}; ${history.ruled} ${if (history.ruled == 1) "row" else "rows"} took the count from them.", fontSize = 14.sp, color = grey)
                }
            }
            if (history.countsUnread) Text("This version can't read the dumbbell counts you added, so none are used. Add them again.", fontSize = 14.sp, color = Color(0xFF8A4B00))
            history.refused?.let { Text(it, fontSize = 14.sp, color = Color(0xFF8A4B00)) }
            if (history.reading) Text("Reading the file…", fontSize = 14.sp, color = Color(0xFF16181D))
        }
        when {
            // No second pick while a file is read: it would race the first. Stopping is offered,
            // since a file on a drive that never answers would otherwise never end.
            history.reading -> OutlinedButton(onClick = onStop, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Stop reading the file" }) { Text("Stop") }
            removing -> {
                Text(
                    "Remove the imported history? Your own log stays.",
                    fontSize = 14.sp,
                    color = Color(0xFF16181D),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                // Wrapping, so both buttons stay whole at large text on a narrow phone.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { removing = false; onRemove() },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Remove the imported history" },
                    ) { Text("Remove") }
                    OutlinedButton(
                        onClick = { removing = false },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Keep the imported history" },
                    ) { Text("Keep") }
                }
            }
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImport, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (history.read == null) "Import a history file" else "Replace the history")
                }
                if (history.read != null) {
                    OutlinedButton(onClick = onCounts, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (history.counts == null) "Add your dumbbell counts" else "Replace the dumbbell counts")
                    }
                    OutlinedButton(onClick = { removing = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove the history") }
                }
            }
        }
    }
}

/**
 * The app's screen: today's sheet in the middle, a newer build waiting when there is one, and the
 * version, copyable in one tap.
 */
@Composable
fun HomeScreen(
    version: String,
    waitingBuild: Int?,
    onInstall: () -> Unit,
    onCopyVersion: () -> Unit,
    today: Today = Today.Loading,
    onAllowCalendar: () -> Unit = {},
    onSaveLog: (Today.Showing, LogDraft) -> Unit = { _, _ -> },
    earlier: Today.Showing? = null,
    backup: BackupState = BackupState(),
    onChooseFolder: () -> Unit = {},
    history: HistoryState = HistoryState(),
    onImportHistory: () -> Unit = {},
    onRemoveHistory: () -> Unit = {},
    onStopHistory: () -> Unit = {},
    onPickCounts: () -> Unit = {},
    progress: Progress = Progress(),
    unknownWords: List<UnknownWord> = emptyList(),
    wordAnswers: List<WordAnswer> = emptyList(),
    movements: List<Movement> = emptyList(),
    lineReads: (String, WordAnswer) -> WordAnswers.SayBack = { _, _ -> WordAnswers.SayBack("", true) },
    onAnswerWord: (WordAnswer) -> Unit = {},
    onUndoWord: (String) -> Unit = {},
) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF7F7F5)) {
            Column(
                Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Spotter",
                    fontSize = 28.sp,
                    color = Color(0xFF16181D),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                if (waitingBuild != null) {
                    Surface(color = Color(0xFFE8EEFC), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "A newer build, $waitingBuild, is ready.",
                                fontSize = 16.sp,
                                color = Color(0xFF1E3A8A),
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = onInstall,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text("Install") }
                        }
                    }
                }
                TodayView(today, onAllowCalendar, onSaveLog, earlier)
                ProgressView(progress)
                UnknownWordsView(unknownWords, wordAnswers, movements, lineReads, onAnswerWord, onUndoWord)
                Spacer(Modifier.heightIn(min = 8.dp))
                HistoryView(history, onImportHistory, onRemoveHistory, onStopHistory, onPickCounts)
                BackupView(backup, onChooseFolder)
                Text(
                    "Version $version · tap to copy",
                    fontSize = 14.sp,
                    color = Color(0xFF5B606B),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClickLabel = "copy the version", onClick = onCopyVersion)
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}
