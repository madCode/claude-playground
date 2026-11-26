package spotter.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import spotter.app.update.UpdateStore

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    // On CI the build has a run number, so opening the app schedules the update check: WorkManager
    // must be up before the activity starts, as Android's own start-up does on a phone.
    @get:Rule(order = 0) val workManager = object : ExternalResource() {
        override fun before() = WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext())
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensOnTheHomeScreenWithItsVersion() {
        // It opens on the sheet's tab; the version is under Settings.
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · tap to copy").assertIsDisplayed()
    }

    // The check started on opening finds a build a moment later, while Spotter is on screen.
    @Test
    fun aBuildFoundWhileOpenIsOfferedAtOnce() {
        if (BuildConfig.CI_BUILD == 0) return // a local build never offers one
        compose.onNodeWithText("Install").assertDoesNotExist()
        compose.runOnUiThread { UpdateStore(compose.activity).waiting = BuildConfig.CI_BUILD + 1 }
        compose.onNodeWithText("Install").assertIsDisplayed()
    }
}
