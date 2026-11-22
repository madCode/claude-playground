package spotter.app.update

import android.content.Context
import android.content.SharedPreferences

/** What the updater remembers between runs: a build waiting for a tap, and failed attempts. */
class UpdateStore(private val prefs: SharedPreferences) : UpdateRun.Store {
    constructor(context: Context) : this(context.getSharedPreferences("updates", Context.MODE_PRIVATE))

    /** A newer build Android wants a tap for, or found while Spotter was open; 0 for none. */
    override var waiting: Int
        get() = prefs.getInt(WAITING, 0)
        set(value) = prefs.edit().putInt(WAITING, value).apply()

    override fun failures(build: Int): Int = prefs.getInt("$FAILED$build", 0)

    override fun failed(build: Int) = prefs.edit().putInt("$FAILED$build", failures(build) + 1).apply()

    private companion object {
        const val WAITING = "waiting"
        const val FAILED = "failed-"
    }
}
