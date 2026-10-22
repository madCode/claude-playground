package com.app.jekyllposter.ui.editor

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.testutil.SAMPLE_BLOG
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.idleUntil
import com.app.jekyllposter.testutil.waitUntilLooped
import com.app.jekyllposter.ui.PosterNavHost
import com.app.jekyllposter.ui.home.status
import com.app.jekyllposter.ui.theme.PosterTheme
import com.app.jekyllposter.ui.waitFor
import com.app.jekyllposter.ui.waitForTag
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Schedule…: a post waits on the phone for the time the writer chose, then goes out. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, qualifiers = "w411dp-h891dp")
class SchedulePostTest {
    @get:Rule val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container

    @After fun close() = app.github.close()

    private fun tomorrowAt(hour: Int, minute: Int) =
        LocalDate.now().plusDays(1).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun aScheduledPostWaitsForItsTimeEvenWithTheRandomTimeOn() {
        val id = runBlocking {
            app.signIn()
            c.privacy.update(SAMPLE_BLOG) { it.copy(sendAtRandomTime = true) }
            c.drafts.insert(Draft(blog = SAMPLE_BLOG, title = "Morning", body = "Hello."))
        }
        val editor = EditorViewModel(c, id).also { e -> idleUntil { e.text != null && e.state.value.draft != null } }
        val head = app.github.head
        val at = tomorrowAt(9, 30)
        editor.publish(at = at)
        idleUntil(10_000) { editor.state.value.closed }

        val draft = runBlocking { c.drafts.get(id)!! }
        assertEquals(PostState.Queued, draft.state)
        // Exactly the time chosen: no random moment added on top.
        assertEquals(at, draft.sendAfter)
        assertTrue(draft.scheduled)
        assertEquals(at, app.sendAfters[id])
        // The test app tries to publish at once; before its time the post doesn't go.
        assertEquals(head, app.github.head)
        val label = draft.status().first
        assertTrue(label, label.startsWith("Scheduled for ") && label.endsWith(" tomorrow"))
    }

    @Test fun sendNowAndStopAndEditForgetTheSchedule() {
        app.publishNow = false
        val ids = runBlocking {
            app.signIn()
            List(2) { c.drafts.insert(Draft(blog = SAMPLE_BLOG, title = "Later", body = "x", state = PostState.Queued, sendAfter = tomorrowAt(9, 0), scheduled = true)) }
        }
        runBlocking { c.sendNow(ids[0]) }
        runBlocking { c.takeBack(ids[1]) }
        ids.forEach { id ->
            val draft = runBlocking { c.drafts.get(id)!! }
            assertNull(draft.sendAfter)
            assertFalse(draft.scheduled)
        }
        assertEquals(PostState.Draft, runBlocking { c.drafts.get(ids[1])!! }.state)
    }

    @Test fun schedulingFromTheEditorsMenuQueuesThePostForTheDayAndTimeChosen() {
        runBlocking { app.signIn() }
        compose.setContent { PosterTheme { PosterNavHost(c) } }
        compose.waitFor("New post")
        compose.onNodeWithText("New post", useUnmergedTree = true).performClick()
        compose.waitForTag("title")
        compose.onNodeWithTag("title").performTextInput("Morning pages")
        compose.onNodeWithTag("body").performTextInput("Written the night before.")
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Schedule…").performClick()

        compose.waitFor("Tomorrow")
        compose.onNodeWithText("Tomorrow").performClick()
        setTime(compose, LocalTime.of(9, 30))
        compose.onNodeWithText("Schedule").performClick()

        compose.waitFor("Scheduled for ")
        val draft = runBlocking { c.drafts.list() }.single()
        assertEquals("Morning pages", draft.title)
        assertEquals(tomorrowAt(9, 30), draft.sendAfter)
        assertTrue(draft.scheduled)
        compose.waitUntilLooped { app.github.files().keys.none { it.contains("morning-pages") } }
    }

    @Test fun aTimeAlreadyPastCantBeScheduled() {
        val noon = LocalDate.now().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        var chosen: Long? = null
        compose.setContent { PosterTheme { ScheduleDialog(onDismiss = {}, onSchedule = { chosen = it }, now = { noon }) } }
        compose.onNodeWithText("Today").performClick()
        setTime(compose, LocalTime.of(11, 0))
        compose.waitFor("That time has passed.")
        compose.onNodeWithText("Schedule").assertIsNotEnabled()

        setTime(compose, LocalTime.of(13, 15))
        compose.waitFor("Goes out at ")
        compose.onNodeWithText("Schedule").performClick()
        assertEquals(LocalDate.now().atTime(13, 15).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), chosen)
    }
}

/**
 * Types [time] into the schedule dialog's time fields, as a person would: hour, minute and, on a
 * 12-hour clock, AM or PM.
 */
fun setTime(compose: androidx.compose.ui.test.junit4.ComposeContentTestRule, time: LocalTime) {
    // The dialog's own fields: the editor's title and body behind it take text too.
    val fields = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))
    val twelveHour = compose.onAllNodes(hasText("AM")).fetchSemanticsNodes().isNotEmpty()
    val hour = if (twelveHour) (time.hour % 12).let { if (it == 0) 12 else it } else time.hour
    fields[0].performTextReplacement(hour.toString())
    fields[1].performTextReplacement("%02d".format(time.minute))
    if (twelveHour) compose.onNode(hasText(if (time.hour < 12) "AM" else "PM")).performClick()
}
