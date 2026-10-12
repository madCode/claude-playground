package com.app.carry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier

/**
 * One screen for now. The folder picker and the Log screen come next; until
 * then this renders the sample session so the look can be judged on a real
 * device rather than in a mock.
 *
 * Edge to edge, with insets applied to the CONTENT and not to the
 * background: slate runs behind the status and navigation bars so the app
 * reads as one surface, while no row can slide under the clock.
 *
 * This was wrong in the first build and no test caught it. A Robolectric
 * screenshot draws the composable with no system bars at all, so it came
 * back clean and convincing; the owner's device showed the title colliding
 * with the status bar immediately. Insets are a device-only check, and the
 * same will be true of every screen added here.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Box(Modifier.fillMaxSize().background(Look.Slate)) {
                TodayScreen(SampleSession.today, Modifier.safeDrawingPadding())
            }
        }
    }
}
