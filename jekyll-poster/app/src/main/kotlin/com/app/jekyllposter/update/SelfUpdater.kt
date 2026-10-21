package com.app.jekyllposter.update

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Why a tap on the notice didn't install; [offerLink] when the download in a browser would still help. */
data class UpdateProblem(val why: String, val offerLink: Boolean)

/**
 * A CI debug build installing newer builds of itself. The debug source set fills it in
 * (`LiveUpdates`); a release build gets [NONE], and carries neither the installer nor the
 * permission to install apps, which app stores refuse.
 */
interface SelfUpdater {
    /** From the tap until Android's installer has answered: the notice can't start a second one. */
    val installing: StateFlow<Boolean>

    /** What a tap on "Build [build] is out" does next. [publishing]: a post is going out now. */
    fun next(build: Int, publishing: Boolean, canInstall: Boolean): Step

    /** At app start: the check every six hours, and clearing up after the update that just
     *  installed this build. */
    fun start()

    /** [DebugUpdates] knows of [build], newer than this one: fetch it on Wi-Fi. */
    fun newerBuild(build: Int)

    /** Android's "Install unknown apps" switch for this app. */
    fun canInstall(): Boolean

    /**
     * Downloads the newest build, checks it is this app, signed with this app's key and newer, and
     * hands it to Android. [publishing] is asked again immediately before the hand-off, since
     * installing ends the running app, and a commit cut short is left for the next attempt to
     * sort out. Null once handed over.
     */
    suspend fun install(build: Int, publishing: () -> Boolean): UpdateProblem?

    /** The app's window came to the front, or left it: Android only lets the installer's
     *  confirmation open over the app itself. */
    fun resumed(activity: Activity)
    fun paused()

    enum class Step {
        /** A post is being sent: installing now would end the app part way through. */
        WaitForPost,
        AllowInstalls,
        Install,

        /** The app gave up installing this build itself: the download in a browser is the way left. */
        Browser,
    }

    companion object {
        const val WAIT_FOR_POST = "A post is going out. Tap again once it's sent: updating restarts the app."

        val NONE: SelfUpdater = object : SelfUpdater {
            override val installing: StateFlow<Boolean> = MutableStateFlow(false)
            override fun start() {}
            override fun newerBuild(build: Int) {}
            override fun next(build: Int, publishing: Boolean, canInstall: Boolean) = Step.Browser
            override fun canInstall() = false
            override suspend fun install(build: Int, publishing: () -> Boolean): UpdateProblem? = null
            override fun resumed(activity: Activity) {}
            override fun paused() {}
        }
    }
}
