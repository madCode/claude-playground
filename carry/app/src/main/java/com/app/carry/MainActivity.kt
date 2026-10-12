package com.app.carry

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                    ),
                )
            }
        }
    }

    /** The normal case is a sync dropping the day's session in while the
     *  app is backgrounded, so the folder is re-read on every resume. */
    override fun onResume() {
        super.onResume()
        model.refresh()
    }
}
