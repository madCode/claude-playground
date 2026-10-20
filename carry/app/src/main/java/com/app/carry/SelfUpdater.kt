package com.app.carry

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A CI debug build installing newer builds of itself (claude-playground #107). The debug source
 * set fills it in (`LiveUpdates`); a release build gets [NONE], and carries neither the installer
 * nor the permission to install apps, which app stores refuse.
 */
interface SelfUpdater {
    /** From the tap until Android's installer has answered: the notice can't start a second one. */
    val installing: StateFlow<Boolean>

    /** What a tap on "Build [build] is out" does next. */
    fun next(build: Int, unsaved: Boolean, canInstall: Boolean): Step

    /** Android's "Install unknown apps" switch for this app. */
    fun canInstall(): Boolean

    /**
     * Downloads the newest build, checks it is this app, signed with this app's key and newer, and
     * hands it to Android. [unsaved] is asked again on the caller's thread immediately before the
     * hand-off, since installing replaces the running app. Null once handed over.
     */
    suspend fun install(build: Int, unsaved: () -> Boolean): UpdateProblem?

    /** Carry's window came to the front, or left it: Android only lets the installer's
     *  confirmation open over Carry itself. [unsaved] typing drops a confirmation still waiting. */
    fun resumed(activity: Activity, unsaved: Boolean)
    fun paused()

    enum class Step {
        SaveFirst,
        AllowInstalls,
        Install,

        /** Carry gave up installing this build itself: the download in a browser is the way left. */
        Browser,
    }

    companion object {
        val NONE: SelfUpdater = object : SelfUpdater {
            override val installing: StateFlow<Boolean> = MutableStateFlow(false)
            override fun next(build: Int, unsaved: Boolean, canInstall: Boolean) = Step.Browser
            override fun canInstall() = false
            override suspend fun install(build: Int, unsaved: () -> Boolean): UpdateProblem? = null
            override fun resumed(activity: Activity, unsaved: Boolean) {}
            override fun paused() {}
        }
    }
}
