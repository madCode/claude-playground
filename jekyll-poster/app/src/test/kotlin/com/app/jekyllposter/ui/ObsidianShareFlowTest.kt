package com.app.jekyllposter.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.Shared
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.ui.theme.PosterTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, qualifiers = "w411dp-h891dp")
class ObsidianShareFlowTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<TestApp>()

    @After fun close() = app.github.close()

    private fun signIn() = runBlocking {
        app.container.accounts.save(Account("sample", "good-token", "sample", "sample-blog", "main"))
    }

    @Test fun aNoteWhoseRulesCantBeAppliedStartsNoPostAndSaysWhy() {
        signIn()
        val note = "---\nfind: [Priya, Elm Street]\nreplace: [a friend]\n---\nPriya at Elm Street.\n"
        compose.setContent { PosterTheme { PosterNavHost(app.container, Shared(note, emptyList())) } }
        compose.waitFor("The note has 2 find: and 1 replace: entries; they go in pairs, so the note wasn't added.")
        compose.waitUntil(3_000) { runBlocking { app.container.database.drafts().snapshotCount() } == 0 }
    }
}
