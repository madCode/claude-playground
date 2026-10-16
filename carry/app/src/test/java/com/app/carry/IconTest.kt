package com.app.carry

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.hypot

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val out = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    private fun render(id: Int, px: Int, tint: Int? = null): Bitmap {
        val d = context.getDrawable(id)!!.mutate()
        tint?.let { d.setTint(it) }
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, px, px)
        d.draw(Canvas(bmp))
        check(!bmp.isFlat()) { "drawable $id rendered nothing, so it proves nothing" }
        return bmp
    }

    private fun Bitmap.save(name: String) =
        File(out, "$name.png").outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }

    @Test
    fun `the drawing stays inside the circle every launcher mask keeps`() {
        // Launchers may crop the 108dp layer to anything down to a 66dp
        // circle; ink outside it is cut off on some phones and not others.
        for (layer in listOf(R.drawable.ic_launcher_foreground, R.drawable.ic_launcher_monochrome)) {
            val px = 432
            val bmp = render(layer, px)
            val safe = px * 33f / 108f
            var outside = 0
            for (x in 0 until px) for (y in 0 until px) {
                if (Color.alpha(bmp.getPixel(x, y)) > 0 &&
                    hypot(x + 0.5f - px / 2f, y + 0.5f - px / 2f) > safe) outside++
            }
            assertTrue("$outside pixels of layer $layer fall outside the safe circle", outside == 0)
        }
    }

    @Test
    fun `the status-bar icon is a shape on a clear ground`() {
        // The status bar draws only a small icon's alpha, so any opaque
        // background turns it into a solid square there.
        val px = 96
        val bmp = render(R.drawable.ic_notification, px)
        for ((x, y) in listOf(0 to 0, px - 1 to 0, 0 to px - 1, px - 1 to px - 1)) {
            assertTrue("corner $x,$y is not transparent", Color.alpha(bmp.getPixel(x, y)) == 0)
        }
        var ink = 0
        for (x in 0 until px) for (y in 0 until px) if (Color.alpha(bmp.getPixel(x, y)) > 0) ink++
        assertTrue("ink covers $ink of ${px * px} pixels", ink < px * px / 3)
        bmp.save("icon-status-bar")
    }

    @Test
    fun `the launcher icon on a dark and a light wallpaper, and themed`() {
        // Pictures for review, at 48dp xxhdpi with a circle mask.
        val px = 144
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        for ((name, wallpaper) in listOf("dark" to 0xFF0E0F11.toInt(), "light" to 0xFFECECEC.toInt())) {
            val bmp = Bitmap.createBitmap(px * 2, px * 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(wallpaper)
            canvas.translate(px / 2f, px / 2f)
            canvas.clipPath(Path().apply { addCircle(px / 2f, px / 2f, px / 2f, Path.Direction.CW) })
            icon.setBounds(0, 0, px, px)
            icon.draw(canvas)
            // The wallpaper alone is not flat-checked away, so check the
            // icon drew: some of the circle has to be the zones, not slate.
            var zone = 0
            for (x in px / 2 until px * 3 / 2) for (y in px / 2 until px * 3 / 2) {
                val p = bmp.getPixel(x, y)
                if (Color.red(p) - Color.blue(p) > 120 || Color.blue(p) - Color.red(p) > 120) zone++
            }
            check(zone > px) { "the $name launcher picture shows no icon" }
            bmp.save("icon-launcher-$name")
        }
        val themed = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        Canvas(themed).apply {
            drawCircle(px / 2f, px / 2f, px / 2f, Paint().apply { color = 0xFFC9D6EE.toInt(); isAntiAlias = true })
            val mono = context.getDrawable(R.drawable.ic_launcher_monochrome)!!.mutate()
            mono.setTint(0xFF1B2B4A.toInt())
            // The adaptive layer is 108dp with the visible icon its middle 72dp.
            val pad = (px * 18 / 72)
            mono.setBounds(-pad, -pad, px + pad, px + pad)
            mono.draw(this)
        }
        themed.save("icon-launcher-themed")
    }
}
