package com.app.carry

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
)

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

    /** How the folder is read. Replaced in tests so the stall path and the
     *  keep-last-good path can be exercised without a real provider; there
     *  is no other way in, because the read is deliberately not injectable
     *  through the constructor a ViewModelProvider has to call. */
    internal var read: suspend () -> CarryState = { SessionLoader(folder).load() }

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
            _state.value = when {
                // A provider can block forever -- openInputStream has no
                // timeout and runCatching never fires. Without this the
                // owner sits on "Reading" with no button and no way out but
                // force-quitting.
                next == null -> CarryState.Stalled
                else -> keepLastGood(_state.value, next)
            }
            // Seed what the file already records, so a session finished in
            // two sittings opens showing the first sitting's numbers rather
            // than blank boxes the owner would fill in from memory. Only on
            // a NEW file: re-seeding would throw away edits in progress.
            (_state.value as? CarryState.Ready)?.let { reconcile(it) }
            // The folder is the history, so this is every file in it. Off
            // the main thread and after the sheet: the lobby screen must
            // not wait on a year of sessions to show today's three numbers.
            _since.value = withContext(kotlinx.coroutines.Dispatchers.IO) { readFolder() }
        }
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
    private suspend fun reconcile(ready: CarryState.Ready) {
        val draft = savedDraft()
        when {
            // Not ours. A draft for another session must never be shown
            // against this one, let alone written into it.
            draft == null || draft.sheetId != ready.session.sheetId -> {
                // Two full parses of the document. Small for a real sheet,
                // but this runs on every resume and the file comes from
                // somewhere else -- so it goes where the read already is.
                val (entries, added) = withContext(kotlinx.coroutines.Dispatchers.Default) {
                    SessionPatch.entriesIn(ready.raw) to SessionPatch.addedIn(ready.raw)
                }
                _edits.value = entries
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
        _saving.value = SaveState.Idle
        (_state.value as? CarryState.Ready)?.let { ready ->
            _edits.value = SessionPatch.entriesIn(ready.raw)
            _added.value = named(SessionPatch.addedIn(ready.raw))
        }
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

    /**
     * Pins the draft to the bytes these numbers were typed against -- the
     * EXISTING pin if there is one, so it keeps pointing at the file the
     * first keystroke saw. Re-pinning to "whatever is current" let the
     * identity check be skipped entirely: edits typed against one file
     * would be patched blind into another.
     */
    private fun rememberTyping() {
        val ready = _state.value as? CarryState.Ready ?: return
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
                        return@launch
                    }
                    val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        folder.save(ready.documentId, text)
                    }
                    _saving.value = when (result) {
                        Saved.YES -> {
                            // Only if nothing newer arrived while we wrote:
                            // overwriting a fresher read with this one used
                            // to put an older sheet back on screen.
                            (_state.value as? CarryState.Ready)?.let { now ->
                                if (now.documentId == ready.documentId && now.raw == ready.raw) {
                                    _state.value = ready.copy(raw = text)
                                }
                            }
                            handle.remove<String>(DRAFT)
                            _fileMovedUnderEdits.value = false
                            // Re-seed from what was actually written, so the
                            // next refresh cannot blank the form by reading
                            // a state captured before the save.
                            _edits.value = SessionPatch.entriesIn(text)
                            _added.value = named(SessionPatch.addedIn(text))
                            // The folder is the history, and it just
                            // changed. Only `refresh` recomputed this, and
                            // that runs on resume -- so the owner could
                            // save, tap Since, open today, and read back
                            // the blank rows they had just filled in, under
                            // the words "what you saved that day". Nothing
                            // on that screen tells "not saved" from "not
                            // re-read".
                            _since.value = withContext(kotlinx.coroutines.Dispatchers.IO) { readFolder() }
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
                }
            }
        }
    }

    private companion object {
        const val STALL_AFTER_MS = 15_000L
        const val DRAFT = "draft"
    }
}
