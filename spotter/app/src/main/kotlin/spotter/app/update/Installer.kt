package spotter.app.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

/**
 * Hands a downloaded build to Android's installer. Installing replaces the running app, so the
 * background worker only does it while Spotter isn't on screen (see [UpdateWorker]).
 */
object Installer {
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /**
     * Writes [apk] into an installer session and commits it. [background] means nobody tapped:
     * if Android then wants a confirmation, [InstallResult] doesn't pop one up out of nowhere.
     */
    fun install(context: Context, apk: File, build: Int, background: Boolean) {
        val installer = context.packageManager.packageInstaller
        // A session left by a process that died before committing keeps its copy of the APK.
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Android 12 and later skip "Update this app?" once Spotter installed itself before.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("spotter.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                // Mutable: the installer fills in the status and any confirmation it needs.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val answer = Intent(context, InstallResult::class.java)
                    .putExtra(EXTRA_BUILD, build)
                    .putExtra(EXTRA_BACKGROUND, background)
                session.commit(PendingIntent.getBroadcast(context, id, answer, flags).intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }

    const val EXTRA_BUILD = "spotter.app.update.BUILD"
    const val EXTRA_BACKGROUND = "spotter.app.update.BACKGROUND"
}

/** Android's answer to an install: show the confirmation it asks for, or remember the outcome. */
class InstallResult : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = UpdateStore(context)
        val build = intent.getIntExtra(Installer.EXTRA_BUILD, 0)
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                if (intent.getBooleanExtra(Installer.EXTRA_BACKGROUND, false)) {
                    // A dialog appearing by itself, maybe as the sheet is glanced at in the
                    // lobby, is worse than waiting: the app offers the update on next open.
                    intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1).takeIf { it >= 0 }
                        ?.let { runCatching { context.packageManager.packageInstaller.abandonSession(it) } }
                    store.waiting = build
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?.let { runCatching { context.startActivity(it) } }
                }
            PackageInstaller.STATUS_SUCCESS -> store.waiting = 0
            // Backing out of the confirmation isn't a failure of the build.
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> store.failed(build)
        }
    }
}
