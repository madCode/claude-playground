package spotter.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import spotter.app.update.InstallResult
import spotter.app.update.Installer
import spotter.app.update.UpdateStore
import java.io.File

@RunWith(AndroidJUnit4::class)
class InstallResultTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = UpdateStore(context)

    private fun answer(status: Int, background: Boolean, confirm: Intent? = null) {
        val intent = Intent()
            .putExtra(PackageInstaller.EXTRA_STATUS, status)
            .putExtra(Installer.EXTRA_BUILD, 42)
            .putExtra(Installer.EXTRA_BACKGROUND, background)
        confirm?.let { intent.putExtra(Intent.EXTRA_INTENT, it) }
        InstallResult().onReceive(context, intent)
    }

    // Nobody asked: a dialog appearing on its own is worse than waiting for a tap.
    @Test
    fun aBackgroundInstallThatNeedsAYesWaitsForATap() {
        answer(PackageInstaller.STATUS_PENDING_USER_ACTION, background = true, confirm = Intent("confirm"))
        assertEquals(42, store.waiting)
        assertNull(shadowOf(context as android.app.Application).nextStartedActivity)
    }

    @Test
    fun aTappedInstallShowsAndroidsConfirmation() {
        answer(PackageInstaller.STATUS_PENDING_USER_ACTION, background = false, confirm = Intent("confirm"))
        assertEquals("confirm", shadowOf(context as android.app.Application).nextStartedActivity?.action)
    }

    @Test
    fun successClearsTheWaitingBuild() {
        store.waiting = 42
        answer(PackageInstaller.STATUS_SUCCESS, background = true)
        assertEquals(0, store.waiting)
    }

    @Test
    fun aFailureCountsAgainstTheBuildButBackingOutDoesnt() {
        answer(PackageInstaller.STATUS_FAILURE_ABORTED, background = false)
        assertEquals(0, store.failures(42))
        answer(PackageInstaller.STATUS_FAILURE_CONFLICT, background = true)
        assertEquals(1, store.failures(42))
    }

    @Test
    fun anInstallIsStagedAndCommitted() {
        val apk = File(context.cacheDir, "spotter-42.apk").apply { writeBytes(ByteArray(1024) { 1 }) }
        Installer.install(context, apk, 42, background = true)
        val sessions = context.packageManager.packageInstaller.allSessions
        assertNotNull(sessions.singleOrNull { it.appPackageName == context.packageName })
    }
}
