package com.app.carry

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.File

/**
 * Saves the screen as `<name>.png` in [out], failing if the picture is blank.
 *
 * Robolectric's `captureToImage` copies the window's surface, and that copy
 * sometimes holds only the window background, with the screen fully drawn
 * behind it: on CI, about one build in two lost a simple centred screen this
 * way, and locally it never happened. Waiting for more frames didn't help.
 * Drawing the view onto a bitmap ourselves doesn't go through the surface,
 * so a flat capture falls back to that. The blank check stays after it: a
 * green test that produced no picture is worse than a red one, because the
 * picture gets reviewed.
 */
internal fun ComposeTestRule.shoot(out: File, name: String) {
    waitForIdle()
    var shot = onRoot().captureToImage().asAndroidBitmap()
    if (shot.isFlat()) shot = runOnIdle { drawnInSoftware() } ?: shot
    check(!shot.isFlat()) { "$name captured a blank image, so it proves nothing" }
    File(out, "$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

private fun drawnInSoftware(): Bitmap? {
    val activity = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).firstOrNull() ?: return null
    // The whole window, not just the content view: captureToImage includes
    // the window background, and drawing the decor view gives the same
    // picture pixel for pixel.
    val window = activity.window.decorView
    if (window.width <= 0 || window.height <= 0) return null
    return Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        .also { window.draw(Canvas(it)) }
}

/** True when every pixel is the same colour. */
internal fun Bitmap.isFlat(): Boolean {
    val first = getPixel(0, 0)
    var x = 0
    while (x < width) {
        var y = 0
        while (y < height) {
            if (getPixel(x, y) != first) return false
            y += 7      // sampled: a real screen differs long before this ends
        }
        x += 7
    }
    return true
}
