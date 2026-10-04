package com.app.jekyllposter.ui.settings

import android.content.ClipboardManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.BuildConfig
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.ui.theme.PosterTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, qualifiers = "w411dp-h891dp")
class VersionRowTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<TestApp>()

    @After fun close() = app.github.close()

    @Test fun aLongPressOnTheVersionCopiesIt() {
        compose.setContent { PosterTheme { SettingsScreen(app.container, {}, {}, {}) } }
        compose.onNodeWithText("Jekyll Poster ${BuildConfig.VERSION_NAME}").performTouchInput { longClick() }
        compose.waitForIdle()
        val clip = app.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals("Jekyll Poster ${BuildConfig.VERSION_NAME}", clip.getItemAt(0).text.toString())
    }
}
