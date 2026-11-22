package spotter.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    // A local build: no update check runs, and nothing is offered.
    @Test
    fun opensOnTheHomeScreenWithItsVersion() {
        compose.onNodeWithText("Spotter").assertIsDisplayed()
        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · tap to copy").assertIsDisplayed()
        compose.onNodeWithText("Install").assertDoesNotExist()
    }
}
