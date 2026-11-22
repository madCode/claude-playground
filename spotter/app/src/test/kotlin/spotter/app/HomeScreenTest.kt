package spotter.app

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val out = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    @Test
    fun aWaitingBuildIsOfferedAndInstallsOnATap() {
        var installs = 0
        compose.setContent { HomeScreen("0.1.0-debug.41+abc1234 (41)", waitingBuild = 42, onInstall = { installs++ }, onCopyVersion = {}) }
        compose.onNodeWithText("A newer build, 42, is ready.").assertIsDisplayed()
        compose.onNodeWithText("Install").performClick()
        assertEquals(1, installs)
        shoot("home-update-waiting")
    }

    // Where the log is kept is said in plain words, with the one thing to do about it.
    @Test
    fun theBackupSaysWhereTheLogIs() {
        var picks = 0
        var backup by mutableStateOf(BackupState())
        compose.setContent { HomeScreen("v", waitingBuild = null, onInstall = {}, onCopyVersion = {}, backup = backup, onChooseFolder = { picks++ }) }
        compose.onNodeWithText("Your log is only on this phone.").assertIsDisplayed()
        compose.onNodeWithText("Choose a backup folder").performClick()
        assertEquals(1, picks)
        backup = BackupState("Download/Spotter")
        compose.onNodeWithText("Your log will be copied to Download/Spotter.").assertIsDisplayed()
        backup = BackupState("Download/Spotter", "22 Nov, 14:30")
        compose.onNodeWithText("Log copied to Download/Spotter · 22 Nov, 14:30").assertIsDisplayed()
        shoot("home-backup")
        backup = BackupState("Download/Spotter", "22 Nov, 14:30", ok = false)
        compose.onNodeWithText("Couldn't copy your log to Download/Spotter. Choose the folder again.").assertIsDisplayed()
        compose.onNodeWithText("Change folder").performClick()
        assertEquals(2, picks)
    }

    @Test
    fun theVersionCopiesInOneTap() {
        var copies = 0
        compose.setContent { HomeScreen("0.1.0-debug.42+abc1234 (42)", waitingBuild = null, onInstall = {}, onCopyVersion = { copies++ }) }
        compose.onNodeWithText("Install").assertDoesNotExist()
        compose.onNodeWithText("Version 0.1.0-debug.42+abc1234 (42) · tap to copy").performClick()
        assertEquals(1, copies)
        shoot("home")
    }

    private fun shoot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        // A blank capture would pass every assertion above while showing nothing.
        val colours = (0 until bitmap.width step 20).flatMap { x -> (0 until bitmap.height step 20).map { y -> bitmap.getPixel(x, y) } }.toSet()
        assertTrue("$name came back flat", colours.size > 2)
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
