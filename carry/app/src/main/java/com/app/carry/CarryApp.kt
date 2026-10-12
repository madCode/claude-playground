package com.app.carry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
    data class NeedsFolder(val lostGrant: Boolean = false) : CarryState

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
    data class Ready(val session: Session) : CarryState
}

/**
 * Reads the folder and decides what to show. Kept apart from the screens so
 * the decision is testable without Android's storage framework: the only
 * thing [SessionFolder] is asked for is a list of names and the text of one
 * file.
 */
fun carryState(folder: Folder): CarryState {
    if (folder.grantLost) return CarryState.NeedsFolder(lostGrant = true)
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
    return CarryState.Ready(session)
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

@Composable
fun CarryApp(state: CarryState, onChooseFolder: () -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        CarryState.Loading -> Message(
            title = "Reading",
            body = "Looking in your folder for today's session.",
            action = null,
            onAction = {},
            modifier = modifier,
        )
        is CarryState.NeedsFolder -> Message(
            title = if (state.lostGrant) "Lost the folder" else "Pick your folder",
            body = if (state.lostGrant) {
                "Carry can't read the folder it had. Reinstalling can do that. Pick it again."
            } else {
                "Choose the folder your sync keeps. Carry reads the day's session from it and writes your log back."
            },
            action = "Choose folder",
            onAction = onChooseFolder,
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
        is CarryState.Ready -> TodayScreen(state.session, modifier = modifier)
    }
}

@Composable
private fun Message(
    title: String, body: String, action: String?, onAction: () -> Unit, modifier: Modifier = Modifier,
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
