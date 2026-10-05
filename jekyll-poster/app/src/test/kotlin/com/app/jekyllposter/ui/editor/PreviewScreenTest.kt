package com.app.jekyllposter.ui.editor

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.idleUntil
import com.app.jekyllposter.ui.theme.PosterTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, qualifiers = "w411dp-h891dp")
class PreviewScreenTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<TestApp>()

    @After fun close() = app.github.close()

    @Test fun theEyeButtonShowsThePreview() {
        runBlocking { app.container.accounts.save(Account("sample", "good-token", "sample", "sample-blog", "main")) }
        val id = runBlocking { app.container.drafts.insert(Draft(title = "A walk", body = "Out past the *harbour* wall.\n\n![x]({{ '/assets/images/2025/a.jpg' | relative_url }})\n")) }
        val vm = EditorViewModel(app.container, id)
        idleUntil { vm.text != null }
        compose.setContent { PosterTheme { EditorScreen(vm) {} } }
        compose.onNodeWithContentDescription("Preview").performClick()
        compose.waitUntil(5_000) { vm.state.value.previewing }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Preview of the post").assertExists()
    }
}
