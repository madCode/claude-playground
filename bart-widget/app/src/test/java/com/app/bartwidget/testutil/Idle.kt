package com.app.bartwidget.testutil

import android.os.Looper
import org.robolectric.Shadows.shadowOf

/** Runs the main looper until [condition] holds: DataStore and the network finish on other threads. */
fun idleUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (true) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        check(System.currentTimeMillis() < deadline) { "Condition not met within $timeoutMs ms" }
        Thread.sleep(10)
    }
}
