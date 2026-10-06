package com.app.jekyllposter.publish

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.app.jekyllposter.PosterApp
import com.app.jekyllposter.core.github.GitHubClient
import com.app.jekyllposter.core.github.GitHubException
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.AccountStore
import com.app.jekyllposter.data.BuildState
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.DraftDao
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Whether the site has a published post yet. Every GitHub Pages site deploys through an Actions run
 * now, so the run for the post's commit says when it's live or why it isn't.
 */
private val failures = setOf("failure", "timed_out", "startup_failure", "action_required")

class BuildWatcher(
    private val drafts: DraftDao,
    private val accounts: AccountStore,
    private val clientFor: (Account) -> GitHubClient,
    private val onFinished: (Draft) -> Unit = {},
) {
    /** True when there's nothing more to wait for. */
    suspend fun check(id: Long, giveUp: Boolean): Boolean {
        val draft = drafts.get(id) ?: return true
        val sha = draft.commitSha ?: return true
        if (draft.buildState != BuildState.Building) return true
        val account = accounts.current() ?: return true
        val state = try {
            // Only the Pages deployment says whether the post is live: a failing test or lint
            // workflow on the same commit doesn't. GitHub's own is "pages build and deployment";
            // a site deployed by its own workflow usually has Pages in the name or file.
            val runs = clientFor(account).workflowRuns(account.owner, account.repo, sha)
                .filter { (it.name.orEmpty() + " " + it.path.orEmpty()).contains("pages", ignoreCase = true) }
            when {
                runs.isEmpty() -> null
                runs.any { it.status != "completed" } -> null
                runs.all { it.conclusion == "success" } -> BuildState.Live
                runs.any { it.conclusion in failures } -> BuildState.Failed
                // Cancelled, usually by a newer push whose deployment carries this post too.
                else -> BuildState.Unknown
            }
        } catch (e: GitHubException) {
            // A token without Actions: read, or a repo that isn't a Pages site: nothing to watch.
            if (e.retryable) null else BuildState.Unknown
        } catch (e: IOException) {
            null
        }
        val final = state ?: if (giveUp) BuildState.Unknown else return false
        drafts.get(id)?.let { drafts.update(it.copy(buildState = final)); onFinished(it.copy(buildState = final)) }
        return true
    }
}

class BuildWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PosterApp).container
        // No giving up while the writer's VPN is off: the answer is only waiting for it.
        val giveUp = runAttemptCount >= MAX_ATTEMPTS && !container.waitingForVpn.first()
        val done = container.buildWatcher.check(inputData.getLong(KEY_ID, -1), giveUp = giveUp)
        return if (done) Result.success() else Result.retry()
    }

    companion object {
        private const val KEY_ID = "draft"

        /** Checks 30 s, 60 s, … apart: about eleven minutes in all; a Pages build usually takes one or two. */
        private const val MAX_ATTEMPTS = 6

        fun enqueue(context: Context, id: Long) {
            val request = OneTimeWorkRequestBuilder<BuildWatchWorker>()
                .setInputData(workDataOf(KEY_ID to id))
                .setInitialDelay(20, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("build-$id", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
