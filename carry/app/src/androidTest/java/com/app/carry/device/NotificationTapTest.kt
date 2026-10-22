package com.app.carry.device

import android.Manifest
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.app.carry.PrefsSeen
import com.app.carry.SessionFolder
import com.app.carry.SessionWatchWorker
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Carry as installed, on a device: the folder grant, WorkManager, a real notification, and a tap
 * on it in the system's notification shade, which Robolectric only imitates.
 */
@RunWith(AndroidJUnit4::class)
class NotificationTapTest {
    @get:Rule val onFailure = ScreenOnFailure()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val carry = instrumentation.targetContext
    private val pkg = carry.packageName

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes().decodeToString() }

    /** A made-up sheet for today, with one invented lift, in a folder Carry holds as if picked. */
    @Before fun aSheetForTodayInTheFolder() {
        shell("cmd statusbar collapse")
        shell("pm grant $pkg ${Manifest.permission.POST_NOTIFICATIONS}")
        NotificationManagerCompat.from(carry).cancelAll()
        TestFolder.grantTo(carry, pkg)
        assertTrue("Carry couldn't take the folder's grant", SessionFolder(carry).remember(TestFolder.tree()))
        // Seen before, but not this sheet: a phone that has run the check, so today's is news.
        PrefsSeen(carry).remember(emptySet())
        val today = LocalDate.now()
        // The class starts now, so the sheet is due whatever the hour the test runs.
        val startsAt = LocalDateTime.now().withNano(0)
        TestFolder.put(
            carry, "session-$today-3G.json",
            """
            {"schema":"gym-session/1","sheet_id":"$today:3G","date":"$today","class":"3G","starts_at":"$startsAt",
             "sets":[{"ref":0,"line":"10 x goblet squat","canonical":"goblet squat","weight":null,"reps":null}],
             "added":[]}
            """.trimIndent(),
        )
    }

    private fun open() {
        val launch = carry.packageManager.getLaunchIntentForPackage(pkg)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        carry.startActivity(launch)
        assertTrue("Carry didn't open", device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 20_000))
    }

    /** The After tab's weight box for the sheet's one row, going to the tab if it isn't showing. */
    private fun weightBox() = device.wait(Until.findObject(By.desc(BOX)), 2_000)
        ?: run {
            device.wait(Until.findObject(By.text("After")), 10_000)!!.click()
            device.wait(Until.findObject(By.desc(BOX)), 10_000)!!
        }

    @Test fun tappingTheNotificationKeepsUnsavedTyping() {
        open()
        assertTrue("The sheet didn't show", device.wait(Until.hasObject(By.textContains("goblet squat")), 20_000))
        weightBox().text = "42"
        assertEquals("42", weightBox().text)

        // Not saved. The owner locks the phone or goes Home, and the sheet's notice arrives.
        device.pressHome()
        WorkManager.getInstance(carry).enqueue(OneTimeWorkRequestBuilder<SessionWatchWorker>().build())
        device.openNotification()
        val notice = device.wait(Until.findObject(By.text("3G sheet is ready")), 60_000)
        checkNotNull(notice) { "No notification for today's sheet" }
        notice.click()

        assertTrue("The tap didn't bring Carry back", device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 20_000))
        assertEquals("The typed weight was lost", "42", weightBox().text)
    }

    private companion object {
        const val BOX = "goblet squat, lb"
    }
}
