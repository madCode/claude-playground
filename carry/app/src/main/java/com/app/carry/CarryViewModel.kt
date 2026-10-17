package com.app.carry

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Holds what is on screen across Activity recreation.
 *
 * The first version kept this in an Activity field, which was fine while
 * the read was synchronous -- the real state was computed in onResume,
 * before the first frame. Making the read asynchronous broke that: every
 * rotation, font-size change, multi-window resize or process death
 * recreated the Activity and drew "Reading" over a sheet that had been
 * there a moment earlier, for as long as a sync-blocked folder took to
 * answer. That is the one thing the app must not do.
 *
 * It also holds the Application rather than the Activity, so an abandoned
 * read cannot keep a destroyed Activity alive.
 */
/**
 * A half-filled form, kept so the process dying does not take it.
 *
 * [sheetId] is the point of the whole thing. Without it a draft is just
 * "some numbers", and the first version would load Saturday's weights onto
 * Tuesday's sheet the moment it synced in -- then write them into Tuesday's
 * file if the two happened to share an exercise at the same index, which
 * the same studio running the same template does all the time. A draft
 * belongs to one session and is never shown against another.
 *
 * [fromRaw] is the file exactly as it was when the FIRST of these numbers
 * was typed, not whatever is current. Keeping the bytes rather than a hash
 * lets a save check each edited row still describes the same exercise.
 */
@kotlinx.serialization.Serializable
data class Draft(
    val sheetId: String,
    /** A fingerprint of the file as it was when the FIRST of these numbers
     *  was typed, and the identities of its rows. The whole file text used
     *  to live here, which meant a per-keystroke JSON escape of the entire
     *  document through saved instance state -- and past about half a
     *  megabyte, a TransactionTooLargeException that destroyed exactly the
     *  typing this exists to protect. A hash and the identities answer both
     *  questions it was ever asked. */
    val fromFingerprint: Int,
    val fromIdentities: Map<Int, String>,
    val edits: Map<Int, SessionPatch.Entry>,
    val added: List<AddedSet>,
    /** The file the numbers were typed into, so it can be looked up
     *  directly when another session is what the folder now shows. */
    val documentId: String? = null,
)

data class UpdateProblem(val why: String, val offerLink: Boolean)

/**
 * Held numbers in words, for a draft whose sheet is gone: each row named
 * by the line the sheet printed, since there is no sheet left to show
 * them against. Only what was typed; untouched rows are left out.
 */
internal fun Draft.summary(): String {
    val rows = edits.entries.sortedBy { it.key }.filterNot { it.value.blank }.map { (ref, e) ->
        val line = fromIdentities[ref]?.substringBefore('\u0000')
            ?.let { runCatching { Session.json.parseToJsonElement(it).jsonPrimitive.content }.getOrNull() }
            ?: "row ${ref + 1}"
        listOfNotNull(
            line,
            e.weight?.let { "${it.asWeight()} lb" },
            (e.reps?.toString() ?: e.fractionalReps?.asWeight())?.let { "$it reps" },
            "done".takeIf { e.done }, "easy".takeIf { e.easy }, "mod".takeIf { e.easier },
            "hurt".takeIf { e.hurt }, e.note?.takeIf { it.isNotBlank() },
        ).joinToString(" ")
    } + added.filterNot { it.blank }.map { a ->
        listOfNotNull(
            a.name.ifBlank { "unnamed row" },
            a.weight?.let { "${it.asWeight()} lb" },
            (a.reps?.toString() ?: a.fractionalReps?.asWeight())?.let { "$it reps" },
        ).joinToString(" ")
    }
    val (day, cls) = sheetId.split(':', limit = 2).let { it[0] to it.getOrNull(1) }
    return "Not saved: what you typed for ${listOfNotNull(cls, day).joinToString(" on ")}, " +
        "a session no longer in the folder. ${rows.joinToString("; ").ifEmpty { "(nothing)" }}."
}

class CarryViewModel(app: Application, private val handle: SavedStateHandle) : AndroidViewModel(app) {

    val folder = SessionFolder(app)

    private val _state = mutableStateOf<CarryState>(CarryState.Loading)
    val state: State<CarryState> = _state

    private var reading: Job? = null

    /** True when the file changed under numbers that have not been saved.
     *  The typing is kept -- it is the only thing here that cannot be
     *  re-derived -- but it must not be written blind. */
    private val _fileMovedUnderEdits = mutableStateOf(false)
    val fileMovedUnderEdits: State<Boolean> = _fileMovedUnderEdits

    /** True while the sheet on screen is kept for the typing on it, though
     *  the folder's newest read is another session. */
    private val _anotherSession = mutableStateOf(false)
    val anotherSession: State<Boolean> = _anotherSession

    /** Unsaved typing for sessions no longer in the folder, kept apart
     *  until the owner drops it. */
    private val _held = mutableStateOf<List<Draft>>(emptyList())
    val held: State<List<Draft>> = _held

    private val _edits = mutableStateOf<Map<Int, SessionPatch.Entry>>(emptyMap())
    val edits: State<Map<Int, SessionPatch.Entry>> = _edits

    private val _added = mutableStateOf<List<AddedSet>>(emptyList())
    val added: State<List<AddedSet>> = _added

    /** What the whole folder adds up to. Null until it has been read once;
     *  the tab is not offered before then, rather than offered empty. */
    private val _since = mutableStateOf<Growth.Since?>(null)
    val since: State<Growth.Since?> = _since

    private val _saving = mutableStateOf<SaveState>(SaveState.Idle)
    val saving: State<SaveState> = _saving

    /** Whether a newer debug build is out. Null on a release or local build, which never asks. */
    val updates = DebugUpdates(
        app.getSharedPreferences("debug-updates", android.content.Context.MODE_PRIVATE),
        DebugUpdates.buildOf(BuildConfig.VERSION_NAME),
    )
    private var checking: Job? = null

    /** On every resume; [DebugUpdates] decides whether it is due. One at a time, so two quick
     *  resumes can't both ask before the first has recorded that it did. */
    fun checkForUpdate() {
        if (checking?.isActive == true) return
        checking = viewModelScope.launch { updates.checkIfDue() }
    }

    /** True while typing for the session on screen hasn't been saved: its draft is kept from
     *  the first keystroke until a save or a discard clears it. A draft left from another
     *  session isn't on this form, so it doesn't count. */
    val unsaved: Boolean get() {
        if (_saving.value == SaveState.Saving) return true
        if (savedHeld().isNotEmpty()) return true
        val draft = savedDraft() ?: return false
        val showing = (_state.value as? CarryState.Ready)?.session?.sheetId ?: return true
        return draft.sheetId == showing
    }

    /** True from the tap until Android's installer has answered. Process-wide, because the
     *  answer arrives at a receiver, not here. */
    val installing: kotlinx.coroutines.flow.StateFlow<Boolean> = UpdateInstaller.installing

    /** Why the last attempt didn't install, for the screen to say once; [UpdateProblem.offerLink]
     *  when the download link in a browser would still help. */
    private val _updateProblem = mutableStateOf<UpdateProblem?>(null)
    val updateProblem: State<UpdateProblem?> = _updateProblem
    fun updateProblemShown() { _updateProblem.value = null }

    // Seams for the tests: the network, the archive check and the hand-off to Android.
    internal var download: (String, java.io.File) -> Unit = { url, dest -> UpdateInstaller.download(url, dest) }
    internal var isNewer: (java.io.File) -> Boolean =
        { UpdateInstaller.isNewerSelf(getApplication(), it, BuildConfig.VERSION_CODE.toLong()) }
    internal var stage: (java.io.File) -> Int = { UpdateInstaller.stage(getApplication(), it) }
    internal var commit: (Int) -> Unit = { UpdateInstaller.commit(getApplication(), it) }
    internal var abandon: (Int) -> Unit = { UpdateInstaller.abandon(getApplication(), it) }

    /**
     * Downloads the newest build and hands it to Android's installer. The owner can type while
     * it downloads, so unsaved typing is checked again on the main thread just before the
     * hand-off, not only at the tap: an install started over it would lose it.
     */
    fun installUpdate() {
        if (!UpdateInstaller.start()) return
        val app = getApplication<Application>()
        var handedOver = false
        viewModelScope.launch {
            val problem = try {
                val apk = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val dir = java.io.File(app.cacheDir, "updates").apply { mkdirs() }
                    java.io.File(dir, "carry-debug.apk").apply { delete(); download(DebugUpdates.DOWNLOAD, this) }
                }
                if (!withContext(kotlinx.coroutines.Dispatchers.IO) { isNewer(apk) }) {
                    UpdateProblem("The download isn't a newer Carry, so it wasn't installed.", offerLink = false)
                } else {
                    val id = try {
                        withContext(kotlinx.coroutines.Dispatchers.IO) { stage(apk) }
                    } catch (e: java.io.IOException) {
                        throw InstallFailed(e)
                    }
                    // Checked here, on the main thread, after the slow parts and immediately
                    // before the commit, so nothing typed during them can be installed over.
                    if (unsaved) {
                        abandon(id)
                        UpdateProblem("Save the After tab first, then tap the notice again. Updating restarts Carry.", offerLink = false)
                    } else {
                        commit(id)
                        handedOver = true
                        null
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Once committed, Android's answer still comes; only an attempt that never got
                // there is over.
                if (!handedOver) UpdateInstaller.finish()
                throw e
            } catch (e: InstallFailed) {
                UpdateProblem("Android couldn't take the update: ${e.cause?.message ?: "no room?"}.", offerLink = true)
            } catch (e: java.io.IOException) {
                UpdateProblem("Couldn't download the update: ${e.message ?: "no connection"}.", offerLink = true)
            } catch (e: Exception) {
                // Anything Android's installer or an archive parser throws: said, never a crash,
                // because a crash takes unsaved typing with it.
                UpdateProblem("Android wouldn't take the update: ${e.message ?: e.javaClass.simpleName}.", offerLink = true)
            }
            if (problem != null) {
                UpdateInstaller.finish()
                _updateProblem.value = problem
            }
        }
    }

    private class InstallFailed(cause: Throwable) : Exception(cause)

    /** Why a save stopped before writing, from the file as it is on disk. */
    private enum class Gate { GONE, NOT_OURS, EMPTY, UNDECODABLE, UNSETTLED, OTHER_SESSION, CHANGED }

    private class Check(val gate: Gate?, val onDisk: String?, val newRaw: String?, val newer: Session?, val result: Saved?)

    /** The unreadable file the last save refused, and when, to tell a
     *  sync still landing from a file that will stay this way. */
    private var unreadableSeen: Seen? = null

    private class Seen(val documentId: String, val text: String, val at: Long)

    /** Monotonic, for [SETTLED_AFTER_MS]; a seam so tests needn't wait. */
    internal var clock: () -> Long = { android.os.SystemClock.elapsedRealtime() }

    /** How the folder is read. Replaced in tests so the stall path and the
     *  keep-last-good path can be exercised without a real provider; there
     *  is no other way in, because the read is deliberately not injectable
     *  through the constructor a ViewModelProvider has to call. */
    internal var read: suspend () -> CarryState = { SessionLoader(folder).load() }

    /**
     * The same seam for the write.
     *
     * `folder` itself stays a [SessionFolder] because the Activity needs
     * its SAF-only parts, so this is the one call the save makes into it
     * and the one a test has to be able to stand in for. Everything below
     * it -- the three outcomes, the "nothing newer arrived" guard, the
     * draft clear and the re-seed from what was written -- had never
     * executed in a test until this existed (2026-10-16), on the path
     * whose stated worst outcome is a wrong number in a training history.
     */
    internal var write: (String, String) -> Saved = { id, text -> folder.save(id, text) }

    /** The file as it is on disk now, read just before a write. */
    internal var readNow: (String) -> String? = { folder.read(it) }

    /** The same seam for the folder's history, which is read on its own
     *  schedule: on resume with the sheet, and again whenever a save has
     *  changed what the folder holds. */
    internal var readSince: suspend () -> Growth.Since =
        { withContext(kotlinx.coroutines.Dispatchers.IO) { readFolder() } }

    private var counting: Job? = null

    /**
     * Cancelling only discards the RESULT: the read underneath is a
     * blocking ContentResolver call and cannot be interrupted, so an
     * abandoned read carries on against the provider until it returns.
     * That is worth cancelling anyway -- a stale answer landing after a
     * fresh one would put an older sheet on screen -- but it is not
     * "one read at a time", and an earlier comment here claimed it was.
     */
    fun refresh() {
        reading?.cancel()
        reading = viewModelScope.launch {
            val next = withTimeoutOrNull(STALL_AFTER_MS) { read() }
            val previous = _state.value
            val candidate = when {
                // A provider can block forever -- openInputStream has no
                // timeout and runCatching never fires. Without this the
                // owner sits on "Reading" with no button and no way out but
                // force-quitting.
                next == null -> CarryState.Stalled
                else -> keepLastGood(previous, next)
            }
            // Seed what the file already records, so a session finished in
            // two sittings opens showing the first sitting's numbers rather
            // than blank boxes the owner would fill in from memory. Only on
            // a NEW file: re-seeding would throw away edits in progress.
            // A Ready read goes on screen through `reconcile`, which may keep
            // another sheet there instead.
            if (candidate is CarryState.Ready) reconcile(candidate, previous) else _state.value = candidate
        }
        // The folder is the history, so this is every file in it. Off the
        // main thread and beside the sheet rather than behind it: the lobby
        // screen must not wait on a year of sessions to show today's three
        // numbers.
        rereadHistory()
    }

    /**
     * Decides what to do with the owner's typing when a new read lands.
     *
     * A draft only exists once somebody has typed: seeding the form from a
     * file does NOT create one. The first version wrote a draft whenever
     * the file already held numbers, so a session finished in two sittings
     * was indistinguishable from one being filled in, and every later file
     * triggered the keep-and-warn path with nothing actually typed.
     */
    private suspend fun reconcile(ready: CarryState.Ready, previous: CarryState? = null) {
        _held.value = savedHeld()
        val pending = savedDraft()
        val forOther = pending?.takeIf { it.sheetId != ready.session.sheetId }
        if (forOther != null) {
            // Typing for a session that isn't the one this read found: the
            // day's other class arrived, or its file now holds another. Its
            // own file decides. Until it answers, nothing editable for the
            // other session goes on screen, since typing there would start
            // a draft over this one.
            val found = lookUpInTime(forOther)
            // Saved while the lookup ran: it is nobody's typing now.
            if (savedDraft()?.sheetId != forOther.sheetId) return reconcile(ready)
            val kept = (previous as? CarryState.Ready)?.takeIf { it.session.sheetId == forOther.sheetId }
            val showing = (found as? Lookup.Found)?.ready ?: kept.takeIf { found == Lookup.Unknown }
            if (showing != null) {
                // Still its own file (or no answer, and it was on screen):
                // it stays, and saving it is honest.
                _state.value = showing
                _anotherSession.value = true
                _edits.value = forOther.edits
                _added.value = forOther.added
                _fileMovedUnderEdits.value = forOther.fromFingerprint != showing.raw.hashCode()
                return
            }
            if (found == Lookup.Unknown) {
                // No answer, and nothing to keep on screen: say the folder
                // isn't answering, and keep the draft for the next try.
                _state.value = CarryState.Stalled
                return
            }
            // Its file holds another session now: no save can reach it.
            // The numbers are set aside as text, never overwritten.
            if (savedDraft()?.sheetId == forOther.sheetId) hold(forOther)
        }
        _state.value = ready
        // Held numbers whose own file shows their sheet again go back to
        // being a draft, which can be saved.
        if (savedDraft() == null) {
            savedHeld().firstOrNull { it.sheetId == ready.session.sheetId && it.documentId == ready.documentId }
                ?.let { back ->
                    runCatching { handle[HELD] = Session.json.encodeToString(savedHeld() - back) }
                    runCatching { handle[DRAFT] = Session.json.encodeToString(back) }
                    _held.value = savedHeld()
                }
        }
        val draft = savedDraft()
        _anotherSession.value = false
        when {
            // Nothing typed for this session.
            draft == null -> {
                // Two full parses of the document. Small for a real sheet,
                // but this runs on every resume and the file comes from
                // somewhere else -- so it goes where the read already is.
                val (entries, added) = withContext(kotlinx.coroutines.Dispatchers.Default) {
                    SessionPatch.entriesIn(ready.raw) to SessionPatch.addedIn(ready.raw)
                }
                _edits.value = ready.session.onScreen(entries)
                // Rows from a file written before ids arrive with a
                // positional stand-in, which is the old index identity
                // under a new name: drop a blank row and the next row
                // inherits its id, its screen slot and its text box. They
                // get a real id here, once, and the next save writes it
                // into the file -- after which the row has a name of its
                // own for good. Nothing else promotes them, so without
                // this the fix only ever applied to rows added on this
                // phone.
                _added.value = named(added)
                _fileMovedUnderEdits.value = false
                // "Saved" describes the file that WAS on screen. Left
                // standing, it offers to post a session nobody filled in:
                // the sync drops the day's second class in, this reseeds
                // from it, and the button is still there.
                _saving.value = SaveState.Idle
            }
            // Same session, same bytes: the draft is the truth, and a fresh
            // ViewModel after process death starts empty.
            draft.fromFingerprint == ready.raw.hashCode() -> {
                _edits.value = draft.edits
                _added.value = draft.added
                _fileMovedUnderEdits.value = false
            }
            // Same session, rewritten underneath. Keep the typing -- it is
            // the only thing here that cannot be re-derived -- and say so.
            else -> {
                _edits.value = draft.edits
                _added.value = draft.added
                _fileMovedUnderEdits.value = true
            }
        }
    }

    /** Throw away what was typed and take the file as it now stands. The
     *  only way out of a rewritten session, and it has to be the owner's
     *  choice because their numbers are what gets discarded. */
    /** Hands the folder back. The grant is recursive and survives reboots,
     *  so there has to be a way out of it that is not "uninstall". */
    fun forgetFolder() {
        folder.forget()
        handle.remove<String>(DRAFT)
        _edits.value = emptyMap()
        _added.value = emptyList()
        _fileMovedUnderEdits.value = false
        _state.value = CarryState.NeedsFolder()
    }

    /** The owner picked a whole volume; say so rather than holding it. */
    fun rejectedTooWide() {
        _state.value = CarryState.NeedsFolder(tooWide = true, holding = folder.label)
    }

    fun discardEdits() {
        handle.remove<String>(DRAFT)
        _fileMovedUnderEdits.value = false
        _anotherSession.value = false
        _saving.value = SaveState.Idle
        (_state.value as? CarryState.Ready)?.let { ready ->
            _edits.value = ready.session.onScreen(SessionPatch.entriesIn(ready.raw))
            _added.value = named(SessionPatch.addedIn(ready.raw))
        }
        // Dropping the numbers for the new sheet can follow a refusal that
        // left another session off screen; with the draft gone, a read can
        // show it.
        refresh()
    }

    /**
     * Gives a real id to any row that arrived without one.
     *
     * A file written before ids hands back a positional stand-in, which
     * is the old index identity under a new name: drop a blank row and
     * the next row inherits its id, its screen slot and the text box
     * keyed to it. Every place that seeds from a file goes through here,
     * because the one that didn't would be the one that reintroduced it.
     * The next save writes these into the file, after which the rows have
     * names of their own for good.
     */
    private fun named(rows: List<AddedSet>): List<AddedSet> =
        rows.map { if (SessionPatch.isStandIn(it.id)) it.copy(id = AddedSet.newId()) else it }

    /**
     * Every session file in the folder, as text.
     *
     * One read per file, deliberately: there is no index and no cache to
     * go stale, and a sync that drops a file in is picked up on the next
     * resume with nothing to invalidate. If this ever gets slow the answer
     * is a cache keyed on the folder's own listing, not a second copy of
     * the history that could disagree with the files.
     */
    private fun readFolder(): Growth.Since = runCatching { growthOf(folder) }
        .getOrDefault(Growth.of(emptyList()))

    private fun savedDraft(): Draft? = handle.get<String>(DRAFT)?.let {
        runCatching { Session.json.decodeFromString<Draft>(it) }.getOrNull()
    }

    private fun savedHeld(): List<Draft> = handle.get<String>(HELD)?.let {
        runCatching { Session.json.decodeFromString<List<Draft>>(it) }.getOrNull()
    }.orEmpty()

    /** The owner's choice to let held numbers go. Nothing else clears them. */
    fun dropHeld() {
        handle.remove<String>(HELD)
        _held.value = emptyList()
    }

    /** Where the sheet a draft was typed against stands now, from its own file. */
    internal sealed interface Lookup {
        data class Found(val ready: CarryState.Ready) : Lookup
        /** Its file holds another session, or is gone from the folder. */
        data object Gone : Lookup
        data object Unknown : Lookup
    }

    internal var lookUp: suspend (Draft) -> Lookup = { draft ->
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val id = draft.documentId ?: return@withContext Lookup.Gone
            val text = readNow(id)
                ?: return@withContext if (folder.list().let { it.isNotEmpty() && it.none { (doc, _) -> doc == id } }) Lookup.Gone else Lookup.Unknown
            val session = SessionFiles.parse(text) ?: return@withContext Lookup.Unknown
            if (session.sheetId == draft.sheetId) Lookup.Found(CarryState.Ready(session, text, id, SessionFiles.unreadableRows(text, session)))
            else Lookup.Gone
        }
    }

    /** How long [lookUp] gets; a seam so a test of a slow one needn't wait. */
    internal var lookUpTimeoutMs = STALL_AFTER_MS

    /**
     * [lookUp], given up on after [lookUpTimeoutMs] on the wall clock. A
     * provider read blocks and can't be cancelled, so the wait is what
     * stops; the read finishes on its own and is ignored. A throw or a
     * timeout is [Lookup.Unknown], never [Lookup.Gone].
     */
    private suspend fun lookUpInTime(draft: Draft): Lookup {
        val answer = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
            .async { runCatching { lookUp(draft) }.getOrNull() }
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            withTimeoutOrNull(lookUpTimeoutMs) { answer.await() }
        } ?: Lookup.Unknown
    }

    /** Sets [draft] aside as held numbers and clears it as the draft. */
    private fun hold(draft: Draft) {
        val held = savedHeld() + draft
        runCatching { handle[HELD] = Session.json.encodeToString(held) }
        if (savedDraft()?.sheetId == draft.sheetId) handle.remove<String>(DRAFT)
        _held.value = held
    }

    /**
     * Pins the draft to the bytes these numbers were typed against -- the
     * EXISTING pin if there is one, so it keeps pointing at the file the
     * first keystroke saw. Re-pinning to "whatever is current" let the
     * identity check be skipped entirely: edits typed against one file
     * would be patched blind into another.
     */
    private fun rememberTyping() {
        val ready = _state.value as? CarryState.Ready ?: return
        // Typing for another session is never overwritten: set aside first.
        savedDraft()?.takeIf { it.sheetId != ready.session.sheetId }?.let(::hold)
        val existing = savedDraft()?.takeIf { it.sheetId == ready.session.sheetId }
        // This runs on the main thread on EVERY keystroke, so a throw
        // here costs the typing immediately -- the opposite of the
        // point. It guards the ENCODE only: a draft too big for the
        // transaction fails later, when the system writes saved
        // instance state, and nothing here can catch that.
        runCatching {
            handle[DRAFT] = Session.json.encodeToString(
                Draft(
                    sheetId = ready.session.sheetId,
                    fromFingerprint = existing?.fromFingerprint ?: ready.raw.hashCode(),
                    fromIdentities = existing?.fromIdentities ?: SessionPatch.identitiesOf(ready.raw),
                    edits = _edits.value,
                    added = _added.value,
                    documentId = ready.documentId,
                )
            )
        }
    }

    private fun touched() {
        rememberTyping()
        // A refusal the owner is now acting on should not stay on screen.
        if (_saving.value !is SaveState.Saving) _saving.value = SaveState.Idle
    }

    fun edit(ref: Int, entry: SessionPatch.Entry) {
        _edits.value = _edits.value + (ref to entry)
        touched()
    }

    fun addRow() {
        _added.value = _added.value + AddedSet(id = AddedSet.newId())
        touched()
    }

    fun editAdded(index: Int, row: AddedSet) {
        _added.value = _added.value.toMutableList().also { it[index] = row }
        touched()
    }

    fun save() {
        val ready = _state.value as? CarryState.Ready ?: return
        // Two taps in one frame used to start two writes against the same
        // document, which could undo each other: the composable captured
        // `enabled` before this ever ran.
        if (_saving.value == SaveState.Saving) return
        // By row name, every row at once: the importer refuses the whole
        // day over any one of these, and one refusal per tap hides the rest.
        LogCheck.summary(LogCheck.problems(ready.session, _edits.value, _added.value))?.let {
            _saving.value = SaveState.Failed(it, damaged = false)
            return
        }
        val typedAgainst = savedDraft()?.takeIf { it.sheetId == ready.session.sheetId }
        if (typedAgainst != null && typedAgainst.fromFingerprint != ready.raw.hashCode() &&
            !SessionPatch.identityHolds(typedAgainst.fromIdentities, ready.raw, _edits.value.keys)
        ) {
            _saving.value = SaveState.Failed(
                "The session file changed while you were filling this in, and the exercises no longer line up. Nothing was written.",
                damaged = false,
            )
            return
        }
        _saving.value = SaveState.Saving
        viewModelScope.launch {
            // Every row goes down, nameless ones included. This used to
            // filter them out here, which meant a row the owner typed a
            // weight into but never named was dropped on save -- silently,
            // under a "Saved" message, with no way to tell afterwards. The
            // patch decides instead: an untouched row is left out, and one
            // holding numbers is refused with something to do about it.
            val outcome = withContext(kotlinx.coroutines.Dispatchers.Default) {
                SessionPatch.apply(ready.raw, _edits.value, _added.value)
            }
            when (outcome) {
                is SessionPatch.Outcome.Refused ->
                    _saving.value = SaveState.Failed(outcome.why, damaged = false)
                is SessionPatch.Outcome.Patched -> {
                    val text = outcome.text
                    if (text == null) {
                        // Already recorded. Clear the draft too, or it stays
                        // pinned to old bytes and poisons the next file.
                        handle.remove<String>(DRAFT)
                        _fileMovedUnderEdits.value = false
                        _saving.value = SaveState.Saved
                        // Reached when the file already holds these
                        // numbers, which means something else wrote it --
                        // so the history is as out of date here as it is
                        // after a write of our own.
                        rereadHistory()
                        return@launch
                    }
                    // The patch was built from the bytes last read, so any
                    // other content on disk now -- a re-run, a sync, a file
                    // gone -- would be overwritten blind. Compared by
                    // content, not timestamp, since sync services touch
                    // mtimes, and ignoring a BOM, line endings and JSON
                    // formatting, which change nothing. After a save of our
                    // own `ready.raw` is what we wrote, so Carry never trips
                    // this on itself.
                    val check = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val onDisk = readNow(ready.documentId)
                        val label = onDisk?.let(SessionFiles::labelGuess)
                        val newRaw = onDisk?.let { if (SessionFiles.parse(it) != null) it else SessionFiles.normalised(it) }
                        val newer = newRaw?.let(SessionFiles::parse)
                        val gate = when {
                            onDisk == null -> Gate.GONE
                            label != null && label != SessionFiles.Label.OURS -> Gate.NOT_OURS
                            SessionFiles.sameContent(onDisk, ready.raw) -> null
                            // A sync part way through: never written over.
                            onDisk.isBlank() -> Gate.EMPTY
                            // JSON this build can't decode may still be a
                            // session the coach's importer reads, so it is
                            // never written over either.
                            newer == null && SessionFiles.isJson(onDisk) -> Gate.UNDECODABLE
                            // Not JSON at all, so no reader can use it. Once
                            // the same bytes have sat in the same file for
                            // [SETTLED_AFTER_MS] it isn't a sync landing, and
                            // writing over it loses no readable session.
                            newer == null -> {
                                val seen = unreadableSeen
                                if (seen != null && seen.documentId == ready.documentId && seen.text == onDisk &&
                                    clock() - seen.at >= SETTLED_AFTER_MS
                                ) null else Gate.UNSETTLED
                            }
                            newer.sheetId != ready.session.sheetId -> Gate.OTHER_SESSION
                            else -> Gate.CHANGED
                        }
                        Check(gate, onDisk, newRaw, newer, if (gate == null) write(ready.documentId, text) else null)
                    }
                    val gate = check.gate
                    // The first sighting's time is kept while the bytes
                    // stay the same, so tapping again doesn't restart it.
                    unreadableSeen = if (gate != Gate.UNSETTLED) null else unreadableSeen
                        ?.takeIf { it.documentId == ready.documentId && it.text == check.onDisk }
                        ?: Seen(ready.documentId, check.onDisk!!, clock())
                    _saving.value = when (gate) {
                        Gate.GONE -> SaveState.Failed(
                            "Couldn't read the session file just before saving. It may have been moved or deleted. Nothing was written; your numbers are still here.",
                            damaged = false,
                        )
                        Gate.NOT_OURS -> SaveState.Failed(
                            "The session file was replaced by one whose \"schema\" label is missing or isn't one Carry knows. Nothing was written.",
                            damaged = false,
                        )
                        Gate.EMPTY -> SaveState.Failed(
                            "The session file is empty right now, most likely a sync part way through. Nothing was written; your numbers are still here. Try again in a moment.",
                            damaged = false,
                        )
                        Gate.UNDECODABLE -> SaveState.Failed(
                            "The session file changed into one this version of Carry can't read, though the coach may still be able to. Nothing was written over it; your numbers are still here. Check the file on the laptop, or update Carry.",
                            damaged = false,
                        )
                        Gate.UNSETTLED -> SaveState.Failed(
                            "The session file changed and can't be read, perhaps a sync part way through. Nothing was written; your numbers are still here. If it is still unreadable in two minutes, Try again writes your session over it.",
                            damaged = false,
                        )
                        Gate.OTHER_SESSION -> SaveState.Failed(
                            "This file now holds a different session, so your numbers weren't written into it. They're kept at the top, to enter again or drop.",
                            damaged = false,
                        )
                        Gate.CHANGED -> SaveState.Failed(
                            "This session changed since you opened it, so nothing was written. Your numbers are still here.",
                            damaged = false, newSheet = true,
                        )
                        null -> null
                    } ?: when (check.result!!) {
                        Saved.YES -> {
                            // ALL of this is "only if nothing newer arrived
                            // while we wrote". It used to guard the one
                            // line, and the rest ran regardless -- so when
                            // the sync dropped the day's other class in
                            // mid-write (a refresh runs on every resume),
                            // the form kept Saturday's numbers against
                            // Tuesday's rows: `text` is the OLD session's
                            // bytes, and seeding the form from it puts a
                            // weight the owner never typed on a movement
                            // they have not done. A wrong number on screen
                            // in a one-glance app, and `Saved` is still
                            // tappable, so one tap wrote it into the newer
                            // file. Found by the review of the tests that
                            // first drove this path, 2026-10-16.
                            //
                            // When it IS someone else's sheet now, nothing
                            // here applies: `reconcile` has already seeded
                            // the form from the file that arrived, and the
                            // warning flag it may have set is its to clear.
                            val now = _state.value as? CarryState.Ready
                            if (now != null && now.documentId == ready.documentId && now.raw == ready.raw) {
                                val waiting = _anotherSession.value
                                _state.value = ready.copy(raw = text)
                                handle.remove<String>(DRAFT)
                                _fileMovedUnderEdits.value = false
                                _anotherSession.value = false
                                // The typing kept this sheet on screen over
                                // a newer session; saved, it can show.
                                if (waiting) refresh()
                                // Re-seed from what was actually written, so
                                // the next refresh cannot blank the form by
                                // reading a state captured before the save.
                                _edits.value = ready.session.onScreen(SessionPatch.entriesIn(text))
                                _added.value = named(SessionPatch.addedIn(text))
                            }
                            // True either way: the bytes reached the file
                            // they were typed against. What is on screen is
                            // a separate question, answered above.
                            SaveState.Saved
                        }
                        Saved.NO_FILE_INTACT -> SaveState.Failed(
                            "Couldn't save. Your session file is unchanged, so nothing was lost.",
                            damaged = false,
                        )
                        Saved.NO_FILE_DAMAGED -> SaveState.Failed(
                            "Couldn't save, and the file on disk may be damaged. Don't let it sync before checking it.",
                            damaged = true,
                        )
                    }
                    // AFTER the state above, never inside it. The folder is
                    // the history and it just changed, so the Since tab
                    // must not go on showing the file as it was before --
                    // but this reads every file in the folder, against a
                    // provider that can block for as long as it likes. Done
                    // inside the assignment, the button goes on saying
                    // "Saving..." over a file that is already written, with
                    // the retry guard refusing another tap.
                    if (_saving.value == SaveState.Saved) rereadHistory()
                    // The same session, changed: put it on screen. With a
                    // draft, `reconcile` keeps the typing and raises the
                    // rewritten-session warning, and the next save is
                    // checked against these bytes. Not a whole refresh: that
                    // takes the newest file in the folder, and one for
                    // another session would hide the typing under it.
                    // A different session stays off screen for the same
                    // reason, until the owner chooses to drop their numbers.
                    if (gate == Gate.CHANGED || gate == Gate.OTHER_SESSION) {
                        val now = _state.value as? CarryState.Ready
                        if (now != null && now.documentId == ready.documentId && now.raw == ready.raw) {
                            val refused = _saving.value
                            // Another session: no save can reach the typed-
                            // against one, so its numbers are set aside as
                            // text rather than kept on a sheet whose Save
                            // would only refuse again.
                            if (gate == Gate.OTHER_SESSION) savedDraft()?.takeIf { it.sheetId == ready.session.sheetId }?.let(::hold)
                            val newRaw = check.newRaw!!
                            val newer = check.newer!!
                            val adopted = CarryState.Ready(
                                newer, newRaw, ready.documentId, SessionFiles.unreadableRows(newRaw, newer),
                            )
                            _state.value = adopted
                            reconcile(adopted)
                            _saving.value = refused
                        }
                    }
                }
            }
        }
    }

    /**
     * Re-reads what the whole folder adds up to.
     *
     * One at a time, like the sheet read and for the same reason: an older
     * snapshot landing after a newer one would put the history back where
     * it was. Separate from the sheet read because it is far slower -- a
     * year of files, one provider call each -- and the lobby screen must
     * not wait on it.
     */
    private fun rereadHistory() {
        counting?.cancel()
        counting = viewModelScope.launch { _since.value = readSince() }
    }

    internal companion object {
        /**
         * How long any provider call gets before it is treated as wedged.
         *
         * Internal rather than private because [SessionWatchWorker] needs
         * the same number for the same reason, and two timeouts for one
         * hazard would drift.
         */
        const val STALL_AFTER_MS = 15_000L
        const val DRAFT = "draft"
        const val HELD = "held"

        /**
         * How long an unreadable file must sit unchanged before a save may
         * write over it. A sync writing in place leaves partial bytes for
         * as long as the transfer takes, which on a phone's connection is
         * seconds; two minutes is well past that, short enough that the
         * owner in a lobby isn't stuck, and it only applies to bytes that
         * aren't JSON at all.
         */
        const val SETTLED_AFTER_MS = 120_000L
    }
}

/**
 * Only the rows the owner can SEE.
 *
 * `entriesIn` reads the raw JSON, so it finds every ref in the file --
 * including a row this build could not decode and therefore does not
 * show. Seeding those does two wrong things: the header counts them as
 * filled, so an untouched form reads "1 of 1 filled" over an empty box
 * and the owner leaves thinking they logged it; and the save's identity
 * check tests them, so a rerun that changed only an INVISIBLE row refuses
 * a save they can do nothing about.
 *
 * Nothing is lost by leaving them out. A row with no edit is copied
 * through the patch untouched, so a value an earlier sitting wrote to a
 * row this build cannot show stays in the file.
 *
 * A function rather than a line at the call site because there are THREE
 * seeds -- the read, the discard, and the re-seed after a save -- and
 * fixing only the read left the discard showing the bug verbatim and the
 * post-save one showing it worse: "2 of 1 filled".
 */
internal fun Session.onScreen(entries: Map<Int, SessionPatch.Entry>): Map<Int, SessionPatch.Entry> {
    val refs = sets.map { it.ref }.toSet()
    return entries.filterKeys { it in refs }
}
