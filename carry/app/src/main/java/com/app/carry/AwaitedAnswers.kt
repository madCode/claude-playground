package com.app.carry

import android.content.Context

/**
 * Answers Carry is waiting for from another app: the folder picker, sent for by request code.
 * Android pauses Carry while the picker is up just as when the owner leaves it, so a background
 * install would end Carry there and the folder picked would arrive nowhere. Kept on disk: if
 * Android drops the process meanwhile, it brings Carry back to take the answer, so a background
 * install in a later process waits for that too.
 */
class AwaitedAnswers(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val any: Boolean get() = codes().isNotEmpty()

    fun sent(requestCode: Int) = write(codes() + requestCode.toString())

    fun answered(requestCode: Int) = write(codes() - requestCode.toString())

    /** Nothing is left to answer: Carry started afresh, with no screen to restore. */
    fun forget() {
        prefs.edit().remove(KEY).commit()
    }

    private fun codes(): Set<String> = prefs.getStringSet(KEY, null).orEmpty()

    // Committed, not applied: a process killed right after the picker opens must still leave it set.
    private fun write(codes: Set<String>) {
        prefs.edit().putStringSet(KEY, codes).commit()
    }

    private companion object {
        const val PREFS = "awaited-answers"
        const val KEY = "codes"
    }
}
