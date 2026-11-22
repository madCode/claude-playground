package spotter.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import spotter.app.update.Installer
import spotter.app.update.UpdateStore
import spotter.app.update.UpdateWorker

class MainActivity : ComponentActivity() {
    private val waiting = mutableIntStateOf(0)
    private var stopListening: () -> Unit = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UpdateWorker.schedule(this)
        val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        setContent {
            HomeScreen(
                version = version,
                waitingBuild = waiting.intValue.takeIf { it > BuildConfig.CI_BUILD && BuildConfig.CI_BUILD > 0 },
                onInstall = ::install,
                onCopyVersion = { copy(version) },
            )
        }
    }

    // Watched while on screen: the check started on opening finds a build a moment later, and
    // the box should appear then, not on the next visit.
    override fun onStart() {
        super.onStart()
        val store = UpdateStore(this)
        waiting.intValue = store.waiting
        stopListening = store.listen { waiting.intValue = store.waiting }
    }

    override fun onStop() {
        stopListening()
        super.onStop()
    }

    private fun install() {
        if (!Installer.canInstall(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android asks once to let Spotter install apps; after that, updates need no tap.
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        UpdateWorker.installNow(this)
        Toast.makeText(this, "Downloading the update", Toast.LENGTH_SHORT).show()
    }

    private fun copy(version: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Spotter version", version))
        // Android 13 and later show their own notice of a copy.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(this, "Copied $version", Toast.LENGTH_SHORT).show()
    }
}
