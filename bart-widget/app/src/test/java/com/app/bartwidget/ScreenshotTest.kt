package com.app.bartwidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.compose
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.bartwidget.testutil.TestApp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * End to end with real graphics: stations starred, boards fetched from the fake BART, then the
 * widget and the app drawn. Catches layouts that crash when drawn with real data, and leaves PNGs
 * in build/screenshots for reviewing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container
    private val out = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    @After fun close() = app.bart.close()

    private fun starAndFetch(vararg abbrs: String) = runBlocking {
        abbrs.forEach { c.store.toggle(it) }
        c.refresher.refresh()
    }

    @Test
    fun widget() {
        starAndFetch("MONT", "DUBL")
        val size = DpSize(320.dp, 300.dp)
        val views = runBlocking { BartWidget().compose(app, size = size) }
        val frame = FrameLayout(app)
        val view = views.apply(app, frame)
        frame.addView(view)
        val bitmap = draw(frame, size)
        save("widget", bitmap)

        val texts = texts(frame)
        assertTrue(texts.toString(), "Updated 7:20" in texts)
        assertTrue(texts.toString(), "Refresh" in texts && "Parking" in texts)
        // The stations are a list, whose rows a launcher fetches from Glance's RemoteViewsService;
        // Robolectric has no launcher to bind it, so WidgetTest checks the rows. Here: the list is
        // there, filling the space under the header.
        val list = all(frame).filterIsInstance<android.widget.ListView>().single()
        assertTrue("list is ${list.height}px", list.height > bitmap.height / 2)
    }

    @Test
    fun stations() {
        starAndFetch("MONT", "DUBL")
        ActivityScenario.launch(MainActivity::class.java)
        compose.waitFor("7:22\u00A0(2\u00A0min)")
        save("stations", compose.onRoot().captureToImage().asAndroidBitmap())
    }

    @Test
    fun oneStation() {
        starAndFetch("MONT", "DUBL")
        runBlocking { c.store.toggleLine("MONT:ANTC") }
        ActivityScenario.launch<MainActivity>(android.content.Intent(app, MainActivity::class.java).putExtra(StationParam.name, "MONT"))
        compose.waitFor("SF Airport")
        save("station-mont", compose.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun draw(root: ViewGroup, size: DpSize): Bitmap {
        val d = app.resources.displayMetrics.density
        val w = (size.width.value * d).toInt()
        val h = (size.height.value * d).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
    }

    private fun all(v: View): List<View> =
        listOf(v) + ((v as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { all(g.getChildAt(it)) } } ?: emptyList())

    private fun texts(v: View): List<String> = when (v) {
        is TextView -> listOf(v.text.toString())
        is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
        else -> emptyList()
    }

    private fun save(name: String, bitmap: Bitmap) =
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
