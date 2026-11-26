package spotter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.text.font.FontWeight
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
import spotter.coach.Past
import spotter.coach.PastClass
import spotter.coach.Reading
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
private fun HistoryView(history: HistoryState, onImport: () -> Unit, onRemove: () -> Unit, onStop: () -> Unit) {
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
            }
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
@OptIn(ExperimentalLayoutApi::class)
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
    progress: Progress = Progress(),
    unknownWords: List<UnknownWord> = emptyList(),
    wordAnswers: List<WordAnswer> = emptyList(),
    movements: List<Movement> = emptyList(),
    lineReads: (String, WordAnswer) -> WordAnswers.SayBack = { _, _ -> WordAnswers.SayBack("", true) },
    onAnswerWord: (WordAnswer) -> Unit = {},
    onUndoWord: (String) -> Unit = {},
    /** Every class done, the newest first, for History. */
    past: List<PastClass> = emptyList(),
    /** Where the app opens: the sheet. */
    startTab: Tab = Tab.TODAY,
) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF7F7F5)) {
            var tab by rememberSaveable { mutableStateOf(startTab) }
            // Turning the phone keeps the tab. Coming back later doesn't: at the studio door the
            // app must open on the sheet, whether Android kept the screen alive or rebuilt it
            // from what it saved. So when the screen was last left is saved with it.
            // What History shows: the list, a class, or a lift. Kept here, above the tabs, so
            // the sheet can open a lift there.
            var openClass by rememberSaveable { mutableStateOf<String?>(null) }
            var openLift by rememberSaveable { mutableStateOf<String?>(null) }
            var classesShown by rememberSaveable { mutableStateOf(CLASSES_SHOWN) }
            val onSheet = (today as? Today.Showing)?.sheet?.rows.orEmpty().mapNotNull { it.reading as? Reading.Known }
            // Where the list of classes was scrolled to when one was opened, to come back to; and
            // what History last showed, so only a step between the three moves the page.
            var listAt by rememberSaveable { mutableStateOf(0) }
            var shownPast by remember { mutableStateOf(openClass to openLift) }
            var leftAt by rememberSaveable { mutableStateOf<Long?>(null) }
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { leftAt = now() }
            LifecycleEventEffect(Lifecycle.Event.ON_START) {
                if (leftAt?.let { now() - it > TAB_KEPT_MS } == true) { tab = startTab; openClass = null; openLift = null }
                leftAt = null
            }
            val scope = rememberCoroutineScope()
            // Each tab keeps what was open on it (a half-logged class, a word being sorted)
            // while another is shown.
            val kept = rememberSaveableStateHolder()
            Column(Modifier.safeDrawingPadding()) {
                Box(Modifier.weight(1f)) {
                    kept.SaveableStateProvider(tab.name) {
                        val scroll = rememberScrollState()
                        Column(
                            Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            when (tab) {
                                Tab.TODAY -> {
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
                                    // The sheet's button is under its rows; the log opens at its top.
                                    TodayView(
                                        today, onAllowCalendar, onSaveLog, earlier,
                                        onStartLog = { scope.launch { scroll.scrollTo(0) } },
                                        // Only a lift that has been done has a past to show.
                                        onPast = { known -> openClass = null; openLift = known.key; tab = Tab.HISTORY },
                                        hasPast = { known -> Past.lift(known.key, past, listOf(known)).isNotEmpty() },
                                    )
                                }
                                Tab.HISTORY -> {
                                    // Only what is still there counts as open: a class or lift that went
                                    // with a removed history mustn't leave the tab empty.
                                    val looking = past.any { it.id == openClass } || openLift?.let { Past.lift(it, past, onSheet).isNotEmpty() } == true
                                    // A class or a lift starts at its top; the list comes back where it was.
                                    LaunchedEffect(openClass, openLift) {
                                        if (shownPast != (openClass to openLift)) {
                                            val to = if (openClass == null && openLift == null) listAt else 0
                                            shownPast = openClass to openLift
                                            scroll.scrollTo(to)
                                        }
                                    }
                                    // Noted while the list shows, not on leaving it: by then the page
                                    // is a shorter one, or this tab isn't on screen to ask.
                                    LaunchedEffect(Unit) {
                                        snapshotFlow { scroll.value }.collect {
                                            if (openClass == null && openLift == null && shownPast == (null to null)) listAt = it
                                        }
                                    }
                                    if (!looking && progress == Progress() && past.isEmpty()) {
                                        Text(
                                            "Nothing logged yet. What you lift, and where it is heading, shows here.",
                                            fontSize = 17.sp, color = Color(0xFF5B606B), modifier = Modifier.semantics { heading() },
                                        )
                                    }
                                    if (!looking) ProgressView(progress)
                                    PastView(past, openClass, openLift, classesShown, onClass = { openClass = it }, onLift = { openLift = it }, onMore = { classesShown += CLASSES_SHOWN }, sheet = onSheet)
                                }
                                Tab.SETTINGS -> {
                                    UnknownWordsView(unknownWords, wordAnswers, movements, lineReads, onAnswerWord, onUndoWord)
                                    Section("Imported history")
                                    HistoryView(history, onImportHistory, onRemoveHistory, onStopHistory)
                                    Section("Backup")
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
                }
                // Not over the keyboard: a field being typed in needs the room more.
                if (!WindowInsets.isImeVisible) Tabs(tab) { tab = it }
            }
        }
    }
}

// A label over a part of Settings, and a heading to reach it by.
@Composable
private fun Section(text: String) {
    Text(text, fontSize = 14.sp, color = Color(0xFF5B606B), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

/** How long a tab other than the first is come back to: past it, the app opens on the sheet. */
internal const val TAB_KEPT_MS = 10 * 60 * 1000L

/** The time; a test's own clock. */
internal var now: () -> Long = System::currentTimeMillis

/** The app's three places: the sheet for the door and the car, looking back, and what is set once. */
enum class Tab(val label: String) { TODAY("Today"), HISTORY("History"), SETTINGS("Settings") }

// Along the bottom, under the thumb. Words, not icons: three places need no pictures.
@Composable
private fun Tabs(selected: Tab, onSelect: (Tab) -> Unit) {
    Surface(color = Color.White, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.selectableGroup()) {
            Tab.entries.forEach { t ->
                val on = t == selected
                Column(
                    Modifier.weight(1f).heightIn(min = 56.dp)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(t) })
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Text(t.label, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) Color(0xFF1D4ED8) else Color(0xFF5B606B))
                    Box(Modifier.width(28.dp).height(3.dp).background(if (on) Color(0xFF1D4ED8) else Color.Transparent, RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}
