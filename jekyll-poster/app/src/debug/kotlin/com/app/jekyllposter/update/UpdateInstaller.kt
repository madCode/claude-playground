package com.app.jekyllposter.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.Request

/**
 * Android's side of installing a newer debug build ([LiveUpdates] decides when): the download,
 * the installer session and its answer. Installing ends the running app, so it never starts while
 * the writer is in it or a post is being sent.
 */
object UpdateInstaller {
    private val _installing = MutableStateFlow(false)

    /** From the tap until Android's installer gives a final answer: the notice can't start a
     *  second download and session meanwhile. */
    val installing: StateFlow<Boolean> = _installing

    /** False when an attempt is already under way. */
    fun start(): Boolean = _installing.compareAndSet(expect = false, update = true)
    fun finish() { committed = null; awaitingConfirm = null; _installing.value = false }

    /** Whether the app's window is in front, kept by MainActivity: Android won't let a receiver
     *  open the confirmation over another app. */
    @Volatile var inFront = false

    /** When the window last left the front: the editor saves the last keystrokes a moment after. */
    @Volatile var leftFrontAt = 0L

    /** The installer's confirmation, held when it arrived while the app wasn't in front; shown on
     *  the next return, once. */
    @Volatile var awaitingConfirm: Intent? = null

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    // A debug build is a few tens of megabytes; this is room to grow, not a size anyone expects.
    private const val MAX_APK = 200L * 1024 * 1024

    /**
     * Downloads [url] to [dest] through [calls], following GitHub's redirect to its file host.
     * Throws [IOException] on any failure, including a non-200 answer, an oversized file, no VPN
     * when one is asked for, or taking longer than [budgetMillis] in all; [dest] is only ever a
     * whole file.
     */
    fun download(calls: Call.Factory, url: HttpUrl, dest: File, budgetMillis: Long = 5 * 60_000, maxBytes: Long = MAX_APK) {
        val partial = File(dest.parentFile, dest.name + ".part")
        val call = calls.newCall(Request.Builder().url(url).build())
        call.timeout().timeout(budgetMillis, TimeUnit.MILLISECONDS)
        try {
            call.execute().use { response ->
                if (response.code != 200) throw IOException("the download answered ${response.code}")
                partial.outputStream().use { out ->
                    response.body.byteStream().use { input ->
                        val chunk = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(chunk)
                            if (n < 0) break
                            total += n
                            if (total > maxBytes) throw IOException("the download is larger than any build")
                            out.write(chunk, 0, n)
                        }
                    }
                }
            }
            if (!partial.renameTo(dest)) throw IOException("could not keep the download")
        } finally {
            partial.delete()
        }
    }

    /** The session id of an attempt handed to Android and not yet answered. */
    @Volatile var committed: Int? = null
    @Volatile var committedAt = 0L

    /**
     * Writes [apk] into a new installer session and returns its id, without committing: the
     * copy takes a while, and the caller checks again between this and [commit].
     */
    fun stage(context: Context, apk: File): Int {
        val installer = context.packageManager.packageInstaller
        // A session left by a process that died before committing would keep its copy of the
        // APK for days. One committed and waiting on Android stays.
        installer.mySessions.filter { it.sessionId != committed }.forEach { runCatching { installer.abandonSession(it.sessionId) } }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Android 12 and later may skip the confirmation once the app has installed itself
            // before; until then, and below 12, the writer sees "Update this app?".
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("jekyll-poster.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
            }
        } catch (e: Exception) {
            abandon(context, id)
            throw e
        }
        return id
    }

    /**
     * Hands the staged session of [build] to Android. The answer arrives at [Result].
     *
     * @param background started while the app was closed, not by a tap: if Android wants the
     *   writer to confirm, they aren't asked out of nowhere; the notice's tap installs it instead.
     */
    fun commit(context: Context, id: Int, build: Int, background: Boolean = false) {
        try {
            context.packageManager.packageInstaller.openSession(id).use { session ->
                // Mutable: the installer fills in the status and, when it needs the writer, the
                // confirmation intent. Explicit, so nothing else can receive it.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val answer = Intent(context, Result::class.java).putExtra(EXTRA_BUILD, build).putExtra(EXTRA_BACKGROUND, background)
                val reply = PendingIntent.getBroadcast(context, id, answer, flags)
                committed = id
                committedAt = System.currentTimeMillis()
                session.commit(reply.intentSender)
            }
        } catch (e: Exception) {
            committed = null
            abandon(context, id)
            throw e
        }
    }

    /** Otherwise its staged bytes stay on the device until Android expires the session, and every
     *  retry leaves another. */
    fun abandon(context: Context, id: Int) {
        runCatching { context.packageManager.packageInstaller.abandonSession(id) }
    }

    /**
     * Unsticks [installing] when Android has dropped the session without a final answer: a
     * confirmation left with Home rather than Cancel sends none on some versions, and nor does a
     * session Android expired.
     */
    fun forgetIfGone(context: Context) {
        val id = committed ?: return
        if (context.packageManager.packageInstaller.getSessionInfo(id) == null) finish()
    }

    /** The installer's answer: show its confirmation when it asks, say so when it fails. */
    class Result : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // An answer about a session from before a restart says nothing about the attempt
            // under way now, so it mustn't end it; a failure still counts against its build.
            val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
            fun finish() { if (id == -1 || id == committed) UpdateInstaller.finish() }
            val build = intent.getIntExtra(EXTRA_BUILD, 0)
            val background = intent.getBooleanExtra(EXTRA_BACKGROUND, false)
            when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> if (background) {
                    // Android wants a yes. A dialog appearing on its own is worse than waiting
                    // for the writer's tap on the notice.
                    if (id >= 0) abandon(context, id)
                    LiveUpdates.needsConfirming(context, build)
                    finish()
                } else {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return finish()
                    if (id != -1 && id != committed) return
                    if (!inFront || runCatching { context.startActivity(confirm) }.isFailure) {
                        awaitingConfirm = confirm
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> finish()
                // The writer backing out of the confirmation is not an error worth a toast, nor
                // a failure counted toward giving up on the build.
                PackageInstaller.STATUS_FAILURE_ABORTED -> finish()
                else -> {
                    finish()
                    val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
                    val gaveUp = LiveUpdates.installFailed(context, build, status)
                    val next = if (gaveUp) ". The app won't try this build again; tap the notice for the download." else ""
                    // Nobody asked for a background attempt, so nobody is told it failed.
                    if (!background) Toast.makeText(context, "The update didn't install: $why$next", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    const val EXTRA_BUILD = "com.app.jekyllposter.BUILD"
    const val EXTRA_BACKGROUND = "com.app.jekyllposter.BACKGROUND"
}
