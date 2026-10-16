package com.app.carry

import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.activity.viewModels

/**
 * Edge to edge, with insets applied to the CONTENT and not to the
 * background: slate runs behind the status and navigation bars so the app
 * reads as one surface, while no row can slide under the clock.
 *
 * This was wrong in the first build and no test caught it. A Robolectric
 * screenshot draws the composable with no system bars at all, so it came
 * back clean and convincing; the owner's device showed the title colliding
 * with the status bar immediately. Insets are a device-only check, and the
 * same will be true of every screen added here.
 */
class MainActivity : ComponentActivity() {
    // In a ViewModel, not an Activity field: it has to survive recreation,
    // or every rotation draws "Reading" over the sheet while a slow folder
    // is read again from scratch.
    private val model: CarryViewModel by viewModels()

    /**
     * Asked for once, and only when there is a folder for a session file to
     * land in — a permission dialog on first launch, before the owner has
     * chosen anything, is a question about a feature they have not met.
     */
    private val askNotify =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Marked here, on an answer, rather than before launching. The
            // launch is inside a runCatching because it can throw, and
            // marking first meant a throw burned the only ask the app ever
            // makes: no dialog, no notifications, and nothing anywhere that
            // says so.
            SessionWatchWorker.markAsked(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // KEEP, so this is safe on every launch: it will not restart the
        // period and leave the check never actually running.
        SessionWatchWorker.schedule(this)

        setContent {
            val pick = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { tree: Uri? ->
                // remember() returns false when the grant could not be
                // persisted; carryState then reports NeedsFolder on its own,
                // because `chosen` asks the system rather than the prefs.
                //
                // No refresh here on purpose: onResume always runs after
                // this callback, so refreshing would start a second read of
                // the same folder and throw the first one's answer away.
                if (tree != null && model.folder.isWholeVolume(tree)) {
                    model.rejectedTooWide()
                } else if (tree != null) {
                    model.folder.remember(tree)
                }
            }
            Box(Modifier.fillMaxSize().background(Look.Slate)) {
                CarryApp(
                    state = model.state.value,
                    onChooseFolder = { pick.launch(null) },
                    holding = model.folder.label,
                    onForget = model::forgetFolder,
                    modifier = Modifier.safeDrawingPadding(),
                    log = LogBinding(
                        entries = model.edits.value,
                        added = model.added.value,
                        saving = model.saving.value,
                        fileMoved = model.fileMovedUnderEdits.value,
                        onDiscard = model::discardEdits,
                        onEdit = model::edit,
                        onAdd = model::addRow,
                        onEditAdded = model::editAdded,
                        onSave = model::save,
                        since = model.since.value,
                        onShare = ::share,
                        newerBuild = model.updates.newer.collectAsState().value,
                        onGetBuild = ::getBuild,
                    ),
                )
            }
        }
    }

    /**
     * Hands the composed block to whatever the owner posts with.
     *
     * A chooser every time, deliberately: this is the one thing the app
     * sends anywhere, and it should be obvious where it is going rather
     * than silently reusing a default picked once months ago.
     */
    private fun share(text: String) {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        // Swallowing this was its own bug: nothing takes the text, or the
        // text is too big for the transaction, and the button simply does
        // nothing for ever -- the same silent-success shape that lost the
        // nameless row. Say it out loud instead.
        runCatching { startActivity(android.content.Intent.createChooser(send, null)) }
            .onFailure {
                android.widget.Toast.makeText(
                    this, "Nothing on this phone would take the text.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
    }

    /** Opens the newest build's download; with no browser to take it, copies the link instead. */
    private fun getBuild() {
        val view = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(DebugUpdates.DOWNLOAD))
        runCatching { startActivity(view) }.onFailure {
            getSystemService(android.content.ClipboardManager::class.java)
                ?.setPrimaryClip(android.content.ClipData.newPlainText("Carry download", DebugUpdates.DOWNLOAD))
            android.widget.Toast.makeText(this, "No browser here. The download link is copied.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** The normal case is a sync dropping the day's session in while the
     *  app is backgrounded, so the folder is re-read on every resume. */
    override fun onResume() {
        super.onResume()
        model.refresh()
        model.checkForUpdate()
        maybeAskToNotify()
    }

    /**
     * The app held no permissions at all until 2026-10-16, and asks for
     * this one only when it has somewhere to watch: a folder is chosen,
     * notifications are not already possible, and the owner has not been
     * asked before.
     */
    private fun maybeAskToNotify() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!model.folder.chosen || SessionWatchWorker.canNotify(this)) return
        if (SessionWatchWorker.asked(this)) return
        runCatching { askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }
}
