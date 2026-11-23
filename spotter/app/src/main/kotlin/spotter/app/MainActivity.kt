package spotter.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import spotter.app.export.ExportStore
import spotter.app.history.HistoryPick
import spotter.app.history.HistoryState
import spotter.app.history.HistoryStore
import spotter.app.export.ExportWorker
import spotter.app.log.EventLog
import spotter.app.log.LogDatabase
import spotter.coach.Journal
import spotter.coach.LogDraft
import spotter.coach.Progress
import spotter.coach.ReviewList
import spotter.coach.UnknownWord
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import spotter.app.today.Boards
import spotter.app.today.Calendar
import spotter.app.today.Today
import spotter.app.today.TodayLoader
import spotter.coach.Identity
import spotter.coach.Vocabulary
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import spotter.app.update.Installer
import spotter.app.update.UpdateStore
import spotter.app.update.UpdateWorker

class MainActivity : ComponentActivity() {
    private val waiting = mutableIntStateOf(0)
    private val today = mutableStateOf<Today>(Today.Loading)
    private val earlier = mutableStateOf<Today.Showing?>(null)
    private val progress = mutableStateOf(Progress())
    private val unknownWords = mutableStateOf(emptyList<UnknownWord>())
    private val backup = mutableStateOf(BackupState())
    private val history = mutableStateOf(HistoryState())
    private val imported by lazy { HistoryStore.shared(filesDir) }
    private var watchingHistory: Job? = null

    // Android's own file picker: Spotter reads the one file picked, once, and keeps its own copy.
    // Read in the store's own scope, not this screen's: a rotation mid-read must not lose the
    // pick, and the screen that is showing when it ends hears of it from the store.
    private val pickHistory = registerForActivityResult(ActivityResultContracts.OpenDocument()) { file ->
        val store = imported
        val ticket = (if (file == null) null else store.begin()) ?: return@registerForActivityResult
        val resolver = applicationContext.contentResolver
        HistoryStore.scope.launch {
            try {
                val bytes = runCatching { resolver.openInputStream(file!!)?.use(HistoryPick::read) }.getOrNull()
                HistoryPick.take(
                    bytes?.takeIf { it.size <= HistoryPick.MAX_BYTES }, tooBig = bytes != null && bytes.size > HistoryPick.MAX_BYTES,
                    store, identity, LocalDate.now(), wanted = { store.current(ticket) },
                )
            } finally {
                store.end(ticket)
            }
        }
    }

    private fun removeHistory() {
        val store = imported
        HistoryStore.scope.launch {
            store.clear()
            store.refused = null
            store.touch()
        }
    }

    private val export by lazy { ExportStore(this) }
    private var stopListeningToExport: () -> Unit = {}

    // Android's own folder picker. The grant is kept, so the export still works after a restart.
    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@registerForActivityResult
        val both = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val old = export.folder
        // A provider that gives no lasting grant can't be a backup folder: what is set stays.
        try {
            contentResolver.takePersistableUriPermission(tree, both)
        } catch (e: SecurityException) {
            return@registerForActivityResult
        }
        // Grants are limited in number: the folder being replaced gives its back.
        if (old != null && old != tree.toString()) runCatching { contentResolver.releasePersistableUriPermission(Uri.parse(old), both) }
        export.folder = tree.toString()
        ExportWorker.runNow(this)
    }

    private fun showBackup() {
        val at = export.lastAt.takeIf { it > 0 }?.let {
            // The phone's own way of writing a date and a time, 12 or 24 hour as it is set.
            android.text.format.DateUtils.formatDateTime(
                this, it, android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_SHOW_TIME or android.text.format.DateUtils.FORMAT_ABBREV_MONTH,
            )
        }
        backup.value = BackupState(export.folder?.let { export.folderName ?: "the folder you chose" }, at, export.lastOk)
    }
    private var stopListening: () -> Unit = {}
    private val calendar by lazy { Calendar(this) }
    private val askCalendar = registerForActivityResult(ActivityResultContracts.RequestPermission()) { calendarAnswered(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UpdateWorker.schedule(this)
        ExportWorker.schedule(this)
        val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        setContent {
            HomeScreen(
                version = version,
                waitingBuild = waiting.intValue.takeIf { it > BuildConfig.CI_BUILD && BuildConfig.CI_BUILD > 0 },
                onInstall = ::install,
                onCopyVersion = { copy(version) },
                today = today.value,
                onAllowCalendar = ::allowCalendar,
                onSaveLog = ::saveLog,
                earlier = earlier.value,
                progress = progress.value,
                unknownWords = unknownWords.value,
                backup = backup.value,
                onChooseFolder = { pickFolder.launch(null) },
                history = history.value,
                // Any type: a ".jsonl" file has none the picker knows.
                onImportHistory = { pickHistory.launch(arrayOf("*/*")) },
                onRemoveHistory = ::removeHistory,
                onStopHistory = { imported.stop() },
            )
        }
    }

    // Watched while on screen: the check started on opening finds a build a moment later, and
    // the box should appear then, not on the next visit.
    override fun onStart() {
        super.onStart()
        load()
        val store = UpdateStore(this)
        waiting.intValue = store.waiting
        stopListening = store.listen { waiting.intValue = store.waiting }
        showBackup()
        stopListeningToExport = export.listen { showBackup() }
        // Here and not with the sheet: the history is said also while the calendar isn't allowed.
        // Each change in the store (a pick taken or refused, the history removed) is shown, and
        // the sheet made again from it.
        watchingHistory = lifecycleScope.launch {
            var first = true
            imported.changed.collect {
                history.value = withContext(Dispatchers.IO) { HistoryPick.state(imported, identity, LocalDate.now()) }
                if (!first && !imported.reading) load()
                first = false
            }
        }
    }

    override fun onStop() {
        stopListening()
        stopListeningToExport()
        watchingHistory?.cancel()
        super.onStop()
    }

    // Asked once, the first time Spotter opens (madCode's choice for Carry: "once at setup").
    // Afterwards the button asks again. When Android answers at once without showing its dialog,
    // it has stopped asking ("don't ask again"), so the button opens Spotter's settings instead.
    // Timing the answer is the one sign that holds across versions: the rationale flag is also
    // false after a dismissed dialog or an auto-reset permission, when Android would still ask.
    private var askedAt = 0L

    private fun allowCalendar() {
        getSharedPreferences("spotter", MODE_PRIVATE).edit().putBoolean(ASKED_CALENDAR, true).apply()
        askedAt = SystemClock.elapsedRealtime()
        askCalendar.launch(android.Manifest.permission.READ_CALENDAR)
    }

    private fun calendarAnswered(granted: Boolean) {
        if (!granted && SystemClock.elapsedRealtime() - askedAt < NO_DIALOG_MS) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        load()
    }

    private var loading: Job? = null

    private fun load() {
        if (!calendar.allowed()) {
            today.value = Today.NeedsCalendar
            earlier.value = null
            if (!getSharedPreferences("spotter", MODE_PRIVATE).getBoolean(ASKED_CALENDAR, false)) allowCalendar()
            return
        }
        if (today.value == Today.NeedsCalendar) today.value = Today.Loading
        // One load at a time: a rotation or a return from settings starts afresh.
        loading?.cancel()
        loading = lifecycleScope.launch {
            val boards = Boards(File(filesDir, "boards"))
            val loader = TodayLoader(boards, calendar::events)
            val entries = withContext(Dispatchers.IO) { log.entries() }
            val read = withContext(Dispatchers.IO) { imported.result(identity, LocalDate.now()) }
            val beside = HistoryPick.beside(read?.logged.orEmpty(), entries)
            val logged = Today.loggedClasses(entries)
            // From everything logged or imported, as the next sheet would read it. Beside the
            // sheet, not before it: a long history takes a moment the door doesn't have.
            launch {
                progress.value = withContext(Dispatchers.Default) {
                    Progress.of((beside + Journal.history(entries, vocabulary)).sortedBy { it.date }, LocalDate.now())
                        .copy(advice = Progress.advice(entries, vocabulary, LocalDate.now()))
                }
            }
            // The kept board first, then the fetched one.
            val kept = withContext(Dispatchers.IO) { loader.load(LocalDateTime.now(), fetch = false, logged) }
            today.value = Today.afterKept(today.value, kept?.let { Today.of(it, entries, identity, vocabulary, beside) }, logged)
            // From the boards already on the phone, then again once today's has been fetched:
            // weak signal mustn't hold the card back, or drop it in mid-read.
            val fromKept = launch { unknownWords.value = withContext(Dispatchers.IO) { ReviewList.of(boards.all(), identity) } }
            earlier.value = withContext(Dispatchers.IO) { loader.earlier(LocalDateTime.now(), logged) }
                ?.let { Today.of(it, entries, identity, vocabulary, beside) as? Today.Showing }
            val fetched = withContext(Dispatchers.IO) { loader.load(LocalDateTime.now(), logged = logged) }
            if (fetched != null) today.value = Today.of(fetched, entries, identity, vocabulary, beside)
            if (fetched != null) {
                // The earlier reading mustn't land after this one and take today's board away.
                fromKept.cancel()
                unknownWords.value = withContext(Dispatchers.IO) { ReviewList.of(boards.all(), identity) }
            }
        }
    }

    private val log by lazy { EventLog(LogDatabase.get(this)) }

    // Not cancelled by a rotation or leaving the app mid-write: the save happens once, whole.
    // What to write is worked out against the log as stored now, so saving twice writes nothing
    // the second time.
    private fun saveLog(shown: Today.Showing, draft: LogDraft) {
        lifecycleScope.launch {
            withContext(NonCancellable + Dispatchers.IO) {
                saving.withLock { log.save(draft, shown.sheet, shown.date, shown.className, System.currentTimeMillis()) }
                // Here, so the copy is asked for also when the screen went away during the write.
                ExportWorker.runNow(applicationContext)
            }
            load()
        }
    }

    private fun install() {
        if (!Installer.canInstall(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android asks once to let Spotter install apps; after that, updates need no tap.
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        UpdateWorker.installNow(this)
        Toast.makeText(this, "Downloading the update", Toast.LENGTH_SHORT).show()
    }

    private fun copy(version: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Spotter version", version))
        // Android 13 and later show their own notice of a copy.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(this, "Copied $version", Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val ASKED_CALENDAR = "asked_calendar"

        // Faster than any person can read a dialog and tap.
        const val NO_DIALOG_MS = 300L
        val vocabulary by lazy { Vocabulary.starting() }
        val identity by lazy { Identity(vocabulary) }

        // One save at a time, across activities recreated by a rotation.
        val saving = Mutex()
    }
}
