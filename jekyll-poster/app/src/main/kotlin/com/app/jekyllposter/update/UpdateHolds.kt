package com.app.jekyllposter.update

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Screens an update must not end the app under: a post's editor, and Connect (a device-flow
 * sign-in, or installing the GitHub App in the browser). Both open other apps (the camera, the
 * photo and folder pickers, the browser), which pause the app without the writer having left it.
 *
 * Also kept on disk ([prefs]), so a background install in a process started after this one died
 * with the editor open still waits: the editor comes back when the writer returns. A fresh start
 * of the app, with nothing to restore, clears it ([freshStart]).
 */
class UpdateHolds(private val prefs: SharedPreferences) {
    private val count = MutableStateFlow(0)

    /** How many such screens are open in this process. */
    val open: StateFlow<Int> = count.asStateFlow()

    /** One of the screens is open in this process. */
    val held: Boolean get() = count.value > 0

    /** [held], or was when the app last went away without closing the screen. */
    val heldOrLeftOpen: Boolean get() = held || prefs.getBoolean(LEFT_OPEN, false)

    fun hold() {
        synchronized(this) { count.value += 1 }
        prefs.edit().putBoolean(LEFT_OPEN, true).apply()
    }

    fun release() {
        val left = synchronized(this) { (count.value - 1).coerceAtLeast(0).also { count.value = it } }
        if (left == 0) prefs.edit().remove(LEFT_OPEN).apply()
    }

    /** The app started with no screen to restore: whatever was left open is gone. */
    fun freshStart() {
        if (!held) prefs.edit().remove(LEFT_OPEN).apply()
    }

    private companion object {
        const val LEFT_OPEN = "left-open"
    }
}
