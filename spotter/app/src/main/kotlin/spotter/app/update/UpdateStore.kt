package spotter.app.update

import android.content.Context
import android.content.SharedPreferences

/** What the updater remembers between runs: a build waiting for a tap, and failed attempts. */
class UpdateStore(private val prefs: SharedPreferences) : UpdateRun.Store {
    constructor(context: Context) : this(context.getSharedPreferences("updates", Context.MODE_PRIVATE))

    /** A newer build found while Spotter was open, or one Android wants a tap for; 0 for none. */
    override var waiting: Int
        get() = prefs.getInt(WAITING, 0)
        set(value) = prefs.edit().putInt(WAITING, value).apply()

    override var needsTap: Int
        get() = prefs.getInt(NEEDS_TAP, 0)
        set(value) = prefs.edit().putInt(NEEDS_TAP, value).apply()

    override fun failures(build: Int): Int = prefs.getInt("$FAILED$build", 0)

    override fun failed(build: Int) = prefs.edit().putInt("$FAILED$build", failures(build) + 1).apply()

    /** Calls [onChange] whenever a run changes what's waiting; returns how to stop. */
    fun listen(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val WAITING = "waiting"
        const val NEEDS_TAP = "needs-tap"
        const val FAILED = "failed-"
    }
}
