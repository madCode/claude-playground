package com.app.carry

import android.content.Context

/**
 * Whether there is unsaved typing on the After tab, kept on disk so a background install can read
 * it while Carry isn't running. The draft itself lives in saved instance state, which an install
 * throws away along with the process, so installing over it would lose the typing.
 */
class UnsavedMark(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Written on change only: this runs on every keystroke, on the main thread, and the read is
     *  from memory. Committed, not applied, so a process killed right after the first keystroke
     *  still leaves it set. */
    fun set(unsaved: Boolean) {
        if (isSet == unsaved) return
        prefs.edit().putBoolean(KEY, unsaved).commit()
    }

    val isSet: Boolean get() = prefs.getBoolean(KEY, false)

    private companion object {
        const val PREFS = "unsaved-mark"
        const val KEY = "unsaved"
    }
}
