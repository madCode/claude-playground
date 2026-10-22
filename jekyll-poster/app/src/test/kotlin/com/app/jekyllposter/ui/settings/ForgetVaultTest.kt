package com.app.jekyllposter.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.waitUntilLooped
import com.app.jekyllposter.ui.theme.PosterTheme
import com.app.jekyllposter.ui.waitFor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Settings → Forget the vault folder: the app stops reading the folder a shared note's photos come from. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, qualifiers = "w411dp-h891dp")
class ForgetVaultTest {
    @get:Rule val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container

    @After fun close() = app.github.close()

    @Test fun forgettingTheVaultFolderClearsItAndTheRowGoes() {
        runBlocking {
            app.signIn()
            c.settings.setObsidianVault("content://com.android.externalstorage.documents/tree/primary%3ANotes")
        }
        compose.setContent { PosterTheme { SettingsScreen(c, onBack = {}, onBlogs = {}, onAddSignIn = {}) } }
        compose.waitFor("Forget the vault folder")
        compose.onNodeWithText("Forget the vault folder").performScrollTo().performClick()

        compose.waitUntilLooped { runBlocking { c.settings.obsidianVault() } == null }
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Forget the vault folder")).fetchSemanticsNodes().isEmpty() }
    }
}
