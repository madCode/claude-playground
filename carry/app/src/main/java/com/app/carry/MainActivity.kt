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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier

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
    private lateinit var folder: SessionFolder

    // Held by the activity, not created in composition: it has to survive
    // recomposition, and it is refreshed in onResume because the normal case
    // is a sync dropping the day's session in while the app is backgrounded.
    private val state = mutableStateOf<CarryState>(CarryState.NeedsFolder())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        folder = SessionFolder(this)

        setContent {
            val pick = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { tree: Uri? ->
                // remember() returns false when the grant could not be
                // persisted; carryState then reports NeedsFolder on its own,
                // because `chosen` asks the system rather than the prefs.
                if (tree != null) folder.remember(tree)
                state.value = carryState(folder)
            }
            Box(Modifier.fillMaxSize().background(Look.Slate)) {
                CarryApp(
                    state = state.value,
                    onChooseFolder = { pick.launch(null) },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // keepLastGood: a sync replaces the file rather than editing it, so
        // resuming mid-write must not throw away the sheet on screen.
        state.value = keepLastGood(state.value, carryState(folder))
    }
}
