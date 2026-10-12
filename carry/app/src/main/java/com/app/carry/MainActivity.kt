package com.app.carry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

/**
 * One screen for now. The folder picker and the Log screen come next; until
 * then this renders the sample session so the look can be judged on a real
 * device rather than in a mock.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TodayScreen(SampleSession.today, Modifier.fillMaxSize().background(Look.Slate))
        }
    }
}
