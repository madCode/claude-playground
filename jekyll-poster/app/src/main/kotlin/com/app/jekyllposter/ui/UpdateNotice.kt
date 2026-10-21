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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.jekyllposter.ui.theme.RowDivider
import com.app.jekyllposter.ui.theme.RowFrame
import com.app.jekyllposter.update.AppUpdates
import com.app.jekyllposter.update.DebugUpdates
import com.app.jekyllposter.update.SelfUpdater

/** A line on Home while a newer debug build is out; a tap installs it. Nothing otherwise. */
@Composable
fun UpdateNotice(updates: AppUpdates) {
    val build = updates.newer.collectAsStateWithLifecycle().value ?: return
    val installing = updates.installing.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    RowFrame(
        Modifier.fillMaxWidth()
            .clickable(enabled = !installing, role = Role.Button, onClickLabel = "Install it") { getBuild(context, updates, build) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            if (installing) "Getting build $build…" else "Build $build is out",
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
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
        if (problem.offerLink) openDownload(context)
        updates.problemShown()
    }
}

/** Asks once for "Install unknown apps" if it isn't allowed yet; with no way to install, the link. */
private fun getBuild(context: Context, updates: AppUpdates, build: Int) {
    when (updates.next(build)) {
        SelfUpdater.Step.WaitForPost -> say(context, SelfUpdater.WAIT_FOR_POST)
        SelfUpdater.Step.AllowInstalls -> {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            if (runCatching { context.startActivity(settings) }.isSuccess) {
                say(context, "Allow Jekyll Poster to install updates, then tap the line again.")
            } else {
                openDownload(context)
            }
        }
        SelfUpdater.Step.Install -> updates.install(build)
        SelfUpdater.Step.Browser -> openDownload(context)
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
