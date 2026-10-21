package com.app.jekyllposter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.app.jekyllposter.AppContainer
import com.app.jekyllposter.ui.theme.RowDivider
import com.app.jekyllposter.ui.theme.RowFrame
import com.app.jekyllposter.update.AppUpdates
import com.app.jekyllposter.update.AppUpdates.Tap
import com.app.jekyllposter.update.DebugUpdates
import com.app.jekyllposter.update.SelfUpdater
import kotlinx.coroutines.launch

/** A line on Home while a newer debug build is out; a tap installs it. Nothing otherwise. */
@Composable
fun UpdateNotice(updates: AppUpdates) {
    val build = updates.newer.collectAsStateWithLifecycle().value ?: return
    val installing = updates.installing.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Read again on each return: the writer may have just allowed installs in Android's settings.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val label = lifecycle.let {
        when (updates.next(build)) {
            SelfUpdater.Step.Install -> "Install it"
            SelfUpdater.Step.AllowInstalls -> "Allow installs"
            SelfUpdater.Step.Browser -> "Open the download"
            SelfUpdater.Step.WaitForPost -> "Say why it waits"
        }
    }
    RowFrame(
        Modifier.fillMaxWidth()
            .clickable(enabled = !installing, role = Role.Button, onClickLabel = label) {
                scope.launch { act(context, updates, updates.tap(build)) }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            if (installing) "Getting build $build…" else "Build $build is out",
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(
            if (installing) "Android may ask to update the app." else "A newer test build of this app. Tap to install it.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    RowDivider()
}

/**
 * Says why the last tap didn't install, once, on whichever screen is open; with the download in a
 * browser when that would still help. From state rather than a callback held across the download,
 * which would outlive a rotated Activity.
 */
@Composable
fun UpdateProblems(updates: AppUpdates) {
    val problem = updates.problem.collectAsStateWithLifecycle().value ?: return
    val context = LocalContext.current
    LaunchedEffect(problem) {
        say(context, problem.why)
        if (problem.offerLink) act(context, updates, updates.openDownload())
        updates.problemShown()
    }
}

/** While on screen, an update waits rather than end the app under it: see [com.app.jekyllposter.update.UpdateHolds]. */
@Composable
fun HoldUpdates(container: AppContainer) {
    DisposableEffect(Unit) {
        container.updates.holds.hold()
        onDispose { container.updates.holds.release() }
    }
}

private suspend fun act(context: Context, updates: AppUpdates, tap: Tap) {
    when (tap) {
        is Tap.Say -> say(context, tap.text)
        Tap.Installing -> Unit
        Tap.OpenDownload -> openDownload(context)
        Tap.AllowInstalls -> {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            if (runCatching { context.startActivity(settings) }.isSuccess) {
                say(context, "Allow Jekyll Poster to install updates, then tap the line again.")
            } else {
                // No such settings screen: the browser's download, if the VPN rule allows it.
                act(context, updates, updates.openDownload())
            }
        }
    }
}

/** The fallback: the newest build's download in a browser, or the link copied if there's none. */
private fun openDownload(context: Context) {
    val link = DebugUpdates.DOWNLOAD.toString()
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }.onFailure {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Jekyll Poster download", link))
        say(context, "No browser here. The download link is copied.")
    }
}

private fun say(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
