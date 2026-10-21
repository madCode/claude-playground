package com.app.jekyllposter.update

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The debug build's updates, for the screens: whether a newer build is out ([check]), and getting
 * it ([updater]). Kept in the container, so an install started by a tap outlives a rotation.
 *
 * @param publishing whether a post is being sent now.
 */
class AppUpdates(
    val check: DebugUpdates,
    val updater: SelfUpdater,
    private val publishing: () -> Boolean,
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

    fun next(build: Int): SelfUpdater.Step = updater.next(build, publishing(), updater.canInstall())

    fun install(build: Int) {
        scope.launch { updater.install(build, publishing)?.let { _problem.value = it } }
    }

    fun start() = updater.start()
    fun resumed(activity: Activity) = updater.resumed(activity)
    fun paused() = updater.paused()
}
