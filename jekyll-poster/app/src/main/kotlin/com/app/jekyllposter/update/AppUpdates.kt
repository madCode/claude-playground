package com.app.jekyllposter.update

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The debug build's updates, for the screens: whether a newer build is out ([check]), and getting
 * it ([updater]). Kept in the container, so an install started by a tap outlives a rotation.
 *
 * @param publishing whether a post is being sent now.
 * @param holds the screens an update mustn't end the app under.
 * @param waitingForVpn a blog asks for the VPN and there's none: nothing may be fetched, the
 *   browser's download included.
 */
class AppUpdates(
    val check: DebugUpdates,
    val updater: SelfUpdater,
    private val publishing: () -> Boolean,
    val holds: UpdateHolds,
    private val waitingForVpn: suspend () -> Boolean,
    private val scope: CoroutineScope,
) {
    val newer: StateFlow<Int?> get() = check.newer
    val installing: StateFlow<Boolean> get() = updater.installing

    private val _problem = MutableStateFlow<UpdateProblem?>(null)

    /** Why the last tap didn't install, for the screen to say once. */
    val problem: StateFlow<UpdateProblem?> = _problem.asStateFlow()
    fun problemShown() { _problem.value = null }

    private var checking: Job? = null

    /** On every return to the app; [DebugUpdates] decides whether it is due. One at a time, so two
     *  quick returns can't both ask before the first has recorded that it did. */
    fun checkIfDue() {
        if (checking?.isActive == true) return
        checking = scope.launch {
            check.checkIfDue()
            check.newer.value?.let(updater::newerBuild)
        }
    }

    /** What a tap on the notice for [build] will do, for its label. */
    fun next(build: Int): SelfUpdater.Step = updater.next(build, publishing(), updater.canInstall())

    /** What the screen does for a tap on the notice for [build]. */
    sealed interface Tap {
        data class Say(val text: String) : Tap
        data object AllowInstalls : Tap
        data object OpenDownload : Tap

        /** Started; how it went arrives in [problem]. */
        data object Installing : Tap
    }

    suspend fun tap(build: Int): Tap = when (next(build)) {
        SelfUpdater.Step.WaitForPost -> Tap.Say(SelfUpdater.WAIT_FOR_POST)
        SelfUpdater.Step.AllowInstalls -> Tap.AllowInstalls
        SelfUpdater.Step.Browser -> openDownload()
        SelfUpdater.Step.Install -> {
            install(build)
            Tap.Installing
        }
    }

    /** The browser's download, unless a blog asks for the VPN and there's none: it would go around it. */
    suspend fun openDownload(): Tap = if (waitingForVpn()) Tap.Say(SelfUpdater.WAIT_FOR_VPN) else Tap.OpenDownload

    private var resuming: Job? = null

    private fun install(build: Int) {
        scope.launch {
            val problem = updater.install(build, ::tapHold) ?: return@launch
            _problem.value = problem
            // Once the post is left, the kept file installs; the writer doesn't tap again.
            if (problem.resumes && resuming?.isActive != true) {
                resuming = scope.launch {
                    holds.open.first { it == 0 }
                    install(build)
                }
            }
        }
    }

    private fun tapHold(): Hold? = when {
        publishing() -> Hold.Post
        holds.held -> Hold.Screen
        else -> null
    }

    fun start() = updater.start()
    fun resumed(activity: Activity) = updater.resumed(activity)
    fun paused() = updater.paused()
}
