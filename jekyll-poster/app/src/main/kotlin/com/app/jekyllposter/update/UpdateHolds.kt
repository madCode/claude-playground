package com.app.jekyllposter.update

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

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
    private val keys = MutableStateFlow(emptySet<String>())

    /** The screens open in this process, each by a key it keeps across a rotation. */
    val open: StateFlow<Set<String>> = keys.asStateFlow()

    /** One of the screens is open in this process. */
    val held: Boolean get() = keys.value.isNotEmpty()

    /** [held], or was when the app last went away without closing the screen. */
    val heldOrLeftOpen: Boolean get() = held || prefs.getBoolean(LEFT_OPEN, false)

    /** Screen [key] is open. Holding a key already held, as a rotated screen does, changes nothing. */
    fun hold(key: String) {
        keys.update { it + key }
        prefs.edit().putBoolean(LEFT_OPEN, true).apply()
    }

    fun release(key: String) {
        if (keys.updateAndGet { it - key }.isEmpty()) prefs.edit().remove(LEFT_OPEN).apply()
    }

    /**
     * The app was opened from the launcher with nothing to restore: whatever was left open is
     * gone. Only then: a share starts the app in the sharing app's task, while the writer's own
     * task, editor and all, may still wait in recents to be restored.
     */
    fun freshStart() {
        if (!held) prefs.edit().remove(LEFT_OPEN).apply()
    }

    private companion object {
        const val LEFT_OPEN = "left-open"
    }
}
