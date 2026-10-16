package com.app.carry

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Installs a newer debug build from inside the app (claude-playground #107): download the APK,
 * check it is this app and newer, and hand it to Android's own installer, which asks the owner
 * to confirm. Installing replaces the running app, so it never starts over typing that isn't
 * saved yet: losing typed numbers is the one failure this app exists to avoid.
 */
object UpdateInstaller {
    private val _installing = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** From the tap until Android's installer gives a final answer: the notice can't start a
     *  second download and session meanwhile. */
    val installing: kotlinx.coroutines.flow.StateFlow<Boolean> = _installing

    /** False when an attempt is already under way. */
    fun start(): Boolean = _installing.compareAndSet(expect = false, update = true)
    fun finish() { _installing.value = false }

    /** Whether Carry's window is in front, kept by MainActivity: Android won't let a receiver
     *  open the confirmation over another app. */
    @Volatile var inFront = false

    /** The installer's confirmation, held when it arrived while Carry wasn't in front; shown on
     *  the next return, once. */
    @Volatile var awaitingConfirm: Intent? = null

    /** What a tap on "Build N is out" does next. */
    enum class Step { SaveFirst, AllowInstalls, Install }

    fun next(unsaved: Boolean, canInstall: Boolean): Step = when {
        unsaved -> Step.SaveFirst
        !canInstall -> Step.AllowInstalls
        else -> Step.Install
    }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    // A debug build is a few tens of megabytes; this is room to grow, not a size anyone expects.
    private const val MAX_APK = 200L * 1024 * 1024

    /**
     * Downloads [url] to [dest], following GitHub's redirect to its file host. Throws
     * [IOException] on any failure, including a non-200 answer, an oversized file, or taking
     * longer than [budgetMillis] in all; [dest] is only ever a whole file.
     */
    fun download(
        url: String,
        dest: File,
        budgetMillis: Long = 5 * 60_000,
        maxBytes: Long = MAX_APK,
        clock: () -> Long = System::currentTimeMillis,
    ) {
        val deadline = clock() + budgetMillis
        val partial = File(dest.parentFile, dest.name + ".part")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "Carry")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("the download answered $code")
            partial.outputStream().use { out ->
                connection.inputStream.use { input ->
                    val chunk = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(chunk)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) throw IOException("the download is larger than any build")
                        if (clock() > deadline) throw IOException("the download took too long")
                        out.write(chunk, 0, n)
                    }
                }
            }
            if (!partial.renameTo(dest)) throw IOException("could not keep the download")
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }

    /** True when [apk] is this app at a higher version than [installed]. */
    fun isNewerSelf(context: Context, apk: File, installed: Long): Boolean {
        val info = context.packageManager.getPackageArchiveInfo(apk.path, 0) ?: return false
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
        return info.packageName == context.packageName && code > installed
    }

    /** Hands [apk] to Android's installer. The answer arrives at [Result]. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Updating itself, Android 12 and later may skip the confirmation. Where it doesn't,
            // the owner gets the system's "Update this app?" instead, which is fine.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        try {
            commit(context, installer, id, apk)
        } catch (e: Exception) {
            // Otherwise its staged bytes stay on the device until Android expires the session,
            // and every retry leaves another.
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }

    private fun commit(context: Context, installer: PackageInstaller, id: Int, apk: File) {
        installer.openSession(id).use { session ->
            session.openWrite("carry.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            // Mutable: the installer fills in the status and, when it needs the owner, the
            // confirmation intent.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val reply = PendingIntent.getBroadcast(context, id, Intent(context, Result::class.java), flags)
            session.commit(reply.intentSender)
        }
    }

    /** The installer's answer: show its confirmation when it asks, say so when it fails. */
    class Result : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return finish()
                    if (!inFront || runCatching { context.startActivity(confirm) }.isFailure) {
                        awaitingConfirm = confirm
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> finish()
                // The owner backing out of the confirmation is not an error worth a toast.
                PackageInstaller.STATUS_FAILURE_ABORTED -> finish()
                else -> {
                    finish()
                    val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
                    Toast.makeText(context, "The update didn't install: $why", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
