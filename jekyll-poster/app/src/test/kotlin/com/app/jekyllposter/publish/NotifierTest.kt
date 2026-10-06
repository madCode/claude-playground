package com.app.jekyllposter.publish

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.BuildState
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class NotifierTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val live = Draft(id = 7, title = "Bus notes", state = PostState.Published, buildState = BuildState.Live, postUrl = "https://me.github.io/blog/bus-notes/")

    @Test fun aLivePostOpensOnTheSite() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifier(app).buildFinished(live)
        val n = shadowOf(manager).allNotifications.single()
        assertEquals("“Bus notes” is live", shadowOf(n).contentTitle)
        // A locked phone shows no title.
        assertEquals(android.app.Notification.VISIBILITY_PRIVATE, n.visibility)
        assertEquals("A post is live", n.publicVersion.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
        val intent = shadowOf(n.contentIntent).savedIntent
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://me.github.io/blog/bus-notes/", intent.dataString)
    }

    @Test fun aFailedBuildSaysWhereToLook() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifier(app).buildFinished(live.copy(buildState = BuildState.Failed))
        assertTrue(shadowOf(shadowOf(manager).allNotifications.single()).contentText.contains("Actions"))
    }

    @Test fun withoutPermissionNothingIsPosted() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifier(app).buildFinished(live)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test fun unknownOutcomesAreQuiet() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifier(app).buildFinished(live.copy(buildState = BuildState.Unknown))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
