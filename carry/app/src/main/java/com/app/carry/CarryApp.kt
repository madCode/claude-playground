package com.app.carry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What the app is showing, decided once and passed down. */
sealed interface CarryState {
    /** Before the first read finishes. Reading the folder is IPC to another
     *  app's provider and can block on a running sync, so it never happens
     *  on the main thread and there is always a moment with nothing yet. */
    data object Loading : CarryState

    /** No folder chosen, or one whose grant is gone -- told apart by the
     *  system's own record, never guessed from an empty folder. */
    data class NeedsFolder(
        val lostGrant: Boolean = false,
        /** They picked a whole volume, which this app will not hold. */
        val tooWide: Boolean = false,
        /** The folder currently held, if any, so it can be seen and dropped. */
        val holding: String? = null,
    ) : CarryState

    /** A folder we can read, with no session in it yet. */
    data object NoSession : CarryState

    /** The folder stopped answering. A sync mid-scan or a wedged provider
     *  can block a read indefinitely, and there is no timeout to set on it,
     *  so this exists to give the owner a way out. */
    data object Stalled : CarryState

    /** A session file that is there but unreadable -- truncated mid-sync, or
     *  written by a runner newer than this app. Different from absent, and
     *  worth different words: one means wait, the other means update. */
    data class Unreadable(val name: String, val wrongSchema: Boolean) : CarryState

    /**
     * Note what is NOT here: how fresh the sheet is. That was a field once,
     * computed when the file was read -- and keepLastGood then put a sheet
     * read on Friday back on screen on Saturday still carrying "this is
     * today's", which is exactly the failure the comparison exists to
     * prevent. A verdict stored next to the thing it judges can only get
     * more wrong as it ages, so freshness is derived where it is shown.
     */
    data class Ready(
        val session: Session,
        /** The file exactly as it was read. Patching works on these bytes,
         *  never on a re-encoding of [session] -- see SessionPatch. */
        val raw: String,
        val documentId: String,
    ) : CarryState
}

/**
 * Reads the folder and decides what to show. Kept apart from the screens so
 * the decision is testable without Android's storage framework: the only
 * thing [SessionFolder] is asked for is a list of names and the text of one
 * file.
 */
fun carryState(folder: Folder): CarryState {
    if (folder.grantLost) return CarryState.NeedsFolder(lostGrant = true, holding = folder.label)
    if (!folder.chosen) return CarryState.NeedsFolder()
    val files = folder.list()
    // An empty folder is NOT a lost grant. The likeliest reason by far is
    // that the coach has not written today's session yet, and telling
    // someone they lost a folder they just picked sends them round the
    // picker for nothing.
    val newest = SessionFiles.newest(files.map { it.second }) ?: return CarryState.NoSession
    val id = files.first { it.second == newest }.first
    val text = folder.read(id) ?: return CarryState.Unreadable(newest, wrongSchema = false)
    val session = SessionFiles.parse(text)
        ?: return CarryState.Unreadable(newest, wrongSchema = SessionFiles.looksLikeNewerSchema(text))
    return CarryState.Ready(session, text, id)
}

/**
 * Keeps the last session on screen when a refresh comes back empty.
 *
 * Syncthing replaces a file rather than editing it, so there is a window
 * where it is absent, zero length or half written. Resuming inside that
 * window used to throw away a sheet that was on screen a second earlier and
 * leave no way back but the picker. A sheet that is briefly stale beats one
 * that vanishes while you are looking at it.
 */
fun keepLastGood(previous: CarryState, next: CarryState): CarryState = when {
    next is CarryState.Ready -> next
    next is CarryState.NeedsFolder -> next          // the folder itself is gone; say so
    previous is CarryState.Ready -> previous
    else -> next
}

/** Everything the Log half needs, so CarryApp stays renderable from a test
 *  without a ViewModel behind it. */
data class LogBinding(
    val entries: Map<Int, SessionPatch.Entry>,
    val added: List<AddedSet>,
    val saving: SaveState,
    val fileMoved: Boolean = false,
    val onDiscard: () -> Unit = {},
    val onEdit: (Int, SessionPatch.Entry) -> Unit,
    val onAdd: () -> Unit,
    val onEditAdded: (Int, AddedSet) -> Unit,
    val onSave: () -> Unit,
)

@Composable
fun CarryApp(
    state: CarryState,
    onChooseFolder: () -> Unit,
    modifier: Modifier = Modifier,
    log: LogBinding? = null,
    /** Named on every screen that offers the picker, and droppable there:
     *  a persistent recursive grant that cannot be seen cannot be audited. */
    holding: String? = null,
    onForget: () -> Unit = {},
) {
    when (state) {
        CarryState.Loading -> Message(
            title = "Reading",
            body = "Looking in your folder for today's session.",
            action = null,
            onAction = {},
            modifier = modifier,
        )
        is CarryState.NeedsFolder -> Message(
            title = when {
                state.tooWide -> "That's your whole phone"
                state.lostGrant -> "Lost the folder"
                else -> "Pick your folder"
            },
            body = when {
                // A tree grant is recursive and persistent. Carry needs one
                // folder, so it will not hold a whole volume.
                state.tooWide ->
                    "Carry only wants the one folder your sync keeps, not everything on the phone. Pick the folder itself."
                state.lostGrant ->
                    "Carry can't read the folder it had. Reinstalling can do that. Pick it again."
                else ->
                    "Choose the folder your sync keeps. Carry reads the day's session from it and writes your log back."
            },
            action = "Choose folder",
            onAction = onChooseFolder,
            holding = state.holding,
            onForget = onForget,
            modifier = modifier,
        )
        CarryState.Stalled -> Message(
            title = "Folder isn't answering",
            body = "Carry asked your folder for today's session and got nothing back. A sync part way through can do that.",
            action = "Pick a different folder",
            onAction = onChooseFolder,
            modifier = modifier,
        )
        CarryState.NoSession -> Message(
            holding = holding,
            onForget = onForget,
            title = "Nothing to carry",
            body = "No session in that folder yet. It appears once the coach has the day's workout.",
            action = "Pick a different folder",
            onAction = onChooseFolder,
            modifier = modifier,
        )
        is CarryState.Unreadable -> Message(
            title = if (state.wrongSchema) "Carry is out of date" else "Can't read that session",
            body = if (state.wrongSchema) {
                "${state.name} was written by a newer coach than this app understands. Update Carry."
            } else {
                "${state.name} is there but didn't read cleanly. If a sync is running, it should settle in a moment."
            },
            action = "Pick a different folder",
            onAction = onChooseFolder,
            modifier = modifier,
        )
        is CarryState.Ready -> if (log == null) {
            TodayScreen(state.session, modifier = modifier)
        } else {
            var onLog by rememberSaveable { mutableStateOf(false) }
            Column(modifier.fillMaxSize().background(Look.Slate)) {
                Row(Modifier.fillMaxWidth()) {
                    Tab("Before", !onLog, Modifier.weight(1f)) { onLog = false }
                    Tab("After", onLog, Modifier.weight(1f)) { onLog = true }
                }
                if (onLog) {
                    LogScreen(
                        session = state.session, entries = log.entries, added = log.added,
                        onEdit = log.onEdit, onAdd = log.onAdd, onEditAdded = log.onEditAdded,
                        onSave = log.onSave, saving = log.saving, fileMoved = log.fileMoved, onDiscard = log.onDiscard,
                    )
                } else {
                    TodayScreen(state.session)
                }
            }
        }
    }
}

/** Two words, because there are two moments: the lobby and the car. */
@Composable
private fun Tab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text,
            color = if (selected) Look.Chalk else Look.ChalkFaint,
            fontFamily = Look.Name, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.padding(top = 14.dp, bottom = 9.dp),
        )
        Box(
            Modifier.fillMaxWidth().height(2.dp)
                .background(if (selected) Look.Amber else Look.Rule)
        )
    }
}

@Composable
private fun Message(
    title: String, body: String, action: String?, onAction: () -> Unit,
    modifier: Modifier = Modifier,
    /** The folder currently held, named so the owner can see the scope of
     *  what they granted -- and drop it. */
    holding: String? = null,
    onForget: () -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().background(Look.Slate).padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title, color = Look.Chalk, fontFamily = Look.Name,
            fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center,
        )
        Text(
            body, color = Look.ChalkDim, fontFamily = Look.Name, fontSize = 14.sp,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp),
        )
        if (holding != null) {
            Text(
                "Holding: $holding",
                color = Look.ChalkFaint, fontFamily = Look.Figure, fontSize = 11.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                "Forget this folder",
                color = Look.ChalkDim, fontFamily = Look.Name, fontSize = 12.sp,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .border(1.dp, Look.RuleStrong, RoundedCornerShape(4.dp))
                    .clickable(onClick = onForget)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (action != null) Text(
            action, color = Look.Slate, fontFamily = Look.Name, fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier
                .padding(top = 22.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Look.Amber)
                .clickable(onClick = onAction)
                .padding(horizontal = 18.dp, vertical = 11.dp),
        )
    }
}


/**
 * Reads the folder somewhere that is not the main thread.
 *
 * [carryState] is a `ContentResolver.query` plus a whole-file read against
 * another app's provider. For a folder a sync service is writing to, that
 * can block for as long as it likes and there is no timeout to set. It used
 * to run in `onResume`, which is an ANR at exactly the moment the app
 * exists for -- a glance in the lobby before class.
 */
class SessionLoader(
    private val folder: Folder,
    private val io: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
) {
    suspend fun load(): CarryState = kotlinx.coroutines.withContext(io) { carryState(folder) }
}
