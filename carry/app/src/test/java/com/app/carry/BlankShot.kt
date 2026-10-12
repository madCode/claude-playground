package com.app.carry

import android.graphics.Bitmap

/**
 * True when every pixel is the same colour.
 *
 * Robolectric's `captureToImage` can return a flat bitmap instead of the
 * screen, without failing. Seven of twelve screenshots were that image
 * before `forkEvery(1)`, and five still are — so the capture is checked
 * rather than trusted. A green test that produced no picture is worse
 * than a red one, because the picture gets reviewed.
 */
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
