package com.app.carry

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
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
class CarryViewModel(app: Application) : AndroidViewModel(app) {

    val folder = SessionFolder(app)

    private val _state = mutableStateOf<CarryState>(CarryState.Loading)
    val state: State<CarryState> = _state

    private var reading: Job? = null

    private val _edits = mutableStateOf<Map<Int, SessionPatch.Entry>>(emptyMap())
    val edits: State<Map<Int, SessionPatch.Entry>> = _edits

    private val _added = mutableStateOf<List<AddedSet>>(emptyList())
    val added: State<List<AddedSet>> = _added

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
            (_state.value as? CarryState.Ready)?.let { ready ->
                if (ready.documentId != seededFrom) {
                    seededFrom = ready.documentId
                    _edits.value = SessionPatch.entriesIn(ready.raw)
                    _added.value = SessionPatch.addedIn(ready.raw)
                    _saving.value = SaveState.Idle
                }
            }
        }
    }

    private var seededFrom: String? = null

    fun edit(ref: Int, entry: SessionPatch.Entry) {
        _edits.value = _edits.value + (ref to entry)
        if (_saving.value == SaveState.Saved) _saving.value = SaveState.Idle
    }

    fun addRow() {
        _added.value = _added.value + AddedSet(name = "")
        if (_saving.value == SaveState.Saved) _saving.value = SaveState.Idle
    }

    fun editAdded(index: Int, row: AddedSet) {
        _added.value = _added.value.toMutableList().also { it[index] = row }
        if (_saving.value == SaveState.Saved) _saving.value = SaveState.Idle
    }

    fun save() {
        val ready = _state.value as? CarryState.Ready ?: return
        _saving.value = SaveState.Saving
        viewModelScope.launch {
            // A row the owner started and left nameless is not an answer.
            val rows = _added.value.filter { it.name.isNotBlank() }
            when (val outcome = SessionPatch.apply(ready.raw, _edits.value, rows)) {
                is SessionPatch.Outcome.Refused ->
                    _saving.value = SaveState.Failed(outcome.why, damaged = false)
                is SessionPatch.Outcome.Patched -> {
                    val text = outcome.text
                    if (text == null) {
                        _saving.value = SaveState.Saved      // nothing to write
                        return@launch
                    }
                    val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        folder.save(ready.documentId, text)
                    }
                    _saving.value = when (result) {
                        Saved.YES -> {
                            // Keep the saved bytes as the new baseline, so a
                            // second save patches what is actually on disk.
                            _state.value = ready.copy(raw = text)
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
    }
}
