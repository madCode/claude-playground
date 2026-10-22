package com.app.jekyllposter.publish

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.testutil.SAMPLE_BLOG
import com.app.jekyllposter.testutil.TestApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A post waiting for its time wakes the app then, not only when WorkManager gets round to it. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class SendTimeAlarmTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val alarms = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager)

    @Before fun workManager() = WorkManagerTestInitHelper.initializeTestWorkManager(app)

    @After fun close() = app.github.close()

    @Test fun queuingAPostForLaterSetsAnAlarmForThatTimeAndTakingItBackClearsIt() {
        val at = System.currentTimeMillis() + 3_600_000
        val queue = WorkManagerQueue(app)
        queue.enqueue(7, at)
        val alarm = alarms.peekNextScheduledAlarm()
        assertNotNull(alarm)
        assertEquals(at, alarm!!.triggerAtTime)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        queue.cancel(7)
        assertNull(alarms.peekNextScheduledAlarm())

        queue.enqueue(8, null)
        assertNull(alarms.peekNextScheduledAlarm())
    }

    @Test fun theAlarmSendsAPostWhoseTimeHasCome() {
        val id = runBlocking {
            app.signIn()
            app.container.drafts.insert(Draft(blog = SAMPLE_BLOG, title = "On time", body = "x", state = PostState.Queued, sendAfter = System.currentTimeMillis() - 1_000, scheduled = true))
        }
        val head = app.github.head
        WorkManagerQueue(app).enqueue(id, System.currentTimeMillis() - 1_000)
        alarms.peekNextScheduledAlarm()!!.operation!!.send()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val deadline = System.currentTimeMillis() + 10_000
        while (runBlocking { app.container.drafts.get(id)!! }.state == PostState.Queued && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertEquals(PostState.Published, runBlocking { app.container.drafts.get(id)!! }.state)
        assertTrue(app.github.head != head)
        assertEquals(listOf(id), app.retried.toList())
    }
}
