package com.app.jekyllposter.testutil

import android.os.Looper
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import org.robolectric.Shadows.shadowOf

/** Runs the main looper until [condition] holds: Room and DataStore finish on other threads. */
fun idleUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (true) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        check(System.currentTimeMillis() < deadline) { "Condition not met within $timeoutMs ms" }
        Thread.sleep(10)
    }
}

/**
 * Compose's `waitUntil`, turning the main looper as it waits. On Robolectric the test runs on the
 * main thread, and Compose's wait turns the looper only when the condition reads the screen: a
 * condition on the database, GitHub or a view model's state waits on work posted to the main
 * thread (`viewModelScope`, Room handing back a result) that never runs, and times out.
 */
fun ComposeContentTestRule.waitUntilLooped(timeoutMs: Long = 5_000, condition: () -> Boolean) =
    waitUntil(timeoutMs) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }
