package com.app.jekyllposter.update

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Why a tap on the notice didn't install; [offerLink] when the download in a browser would still
 * help. [resumes]: it installs by itself once the writer leaves the screen that held it.
 */
data class UpdateProblem(val why: String, val offerLink: Boolean, val resumes: Boolean = false)

/** What an install must wait for: installing ends the running app. */
enum class Hold {
    /** A post being sent: the writer taps again once it's out. */
    Post,

    /** A post's editor or Connect open, perhaps behind the camera, a picker or the browser. */
    Screen,
}

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
     * hands it to Android. [hold] is asked again immediately before the hand-off, since installing
     * ends the running app; held by a screen, the checked file is kept for when it's left. Null
     * once handed over.
     */
    suspend fun install(build: Int, hold: () -> Hold?): UpdateProblem?

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
        const val WAIT_FOR_SCREEN = "The update waits until you leave the post, then installs."
        const val WAIT_FOR_VPN = "Your blogs ask for a VPN and there's none, so the update waits. Turn your VPN on, then tap again."

        val NONE: SelfUpdater = object : SelfUpdater {
            override val installing: StateFlow<Boolean> = MutableStateFlow(false)
            override fun start() {}
            override fun newerBuild(build: Int) {}
            override fun next(build: Int, publishing: Boolean, canInstall: Boolean) = Step.Browser
            override fun canInstall() = false
            override suspend fun install(build: Int, hold: () -> Hold?): UpdateProblem? = null
            override fun resumed(activity: Activity) {}
            override fun paused() {}
        }
    }
}
