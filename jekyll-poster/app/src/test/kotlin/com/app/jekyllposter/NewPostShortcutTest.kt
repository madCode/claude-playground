package com.app.jekyllposter

import android.content.Intent
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.idleUntil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class NewPostShortcutTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()

    @After fun close() = app.github.close()

    @Test fun theLauncherOffersNewPost() {
        val shortcut = ShortcutManagerCompat.getDynamicShortcuts(app).single { it.id == PosterApp.NEW_POST_SHORTCUT }
        assertEquals("New post", shortcut.shortLabel)
        assertEquals(MainActivity.ACTION_NEW_POST, shortcut.intent.action)
    }

    @Test fun theShortcutOpensAnEmptyPostForTheBlog() {
        runBlocking { app.container.accounts.save(Account("sample", "good-token", "sample", "sample-blog", "main")) }
        val intent = Intent(app, MainActivity::class.java).setAction(MainActivity.ACTION_NEW_POST)
        Robolectric.buildActivity(MainActivity::class.java, intent).setup().use {
            idleUntil { runBlocking { app.container.drafts.list() }.isNotEmpty() }
            val draft = runBlocking { app.container.drafts.list() }.single()
            assertEquals(PostState.Draft, draft.state)
            assertEquals("", draft.body)
            assertEquals("sample/sample-blog@main", draft.blog)
        }
    }
}
