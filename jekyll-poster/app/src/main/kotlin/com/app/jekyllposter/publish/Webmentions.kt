package com.app.jekyllposter.publish

import com.app.jekyllposter.core.webmention.Webmention
import com.app.jekyllposter.data.BuildState
import com.app.jekyllposter.data.Destination
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.DraftDao
import com.app.jekyllposter.data.Mention
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.data.Privacy
import com.app.jekyllposter.core.github.NoVpnException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tells the pages a live post links to, for blogs that send webmentions. Each target's outcome
 * is kept with the post, so a retry sends only to the ones still waiting.
 */
class Webmentions(
    private val drafts: DraftDao,
    private val privacy: Privacy,
    /** Sends for blog key; through that blog's connections, behind its VPN switch. */
    private val webmentionFor: (String) -> Webmention,
    /** Hands post id over to be sent: WorkManager in the app. */
    private val queue: (Long) -> Unit,
) {
    /**
     * [draft]'s site has built: if its blog sends webmentions, the pages it links to are noted
     * and queued. The receiver reads the post to check the link, so not before it's live; a build
     * that ended unknown (cancelled by a newer push that carries the post too, or one the app
     * couldn't watch) is likely live, and a receiver that can't find the link just says no.
     */
    suspend fun built(draft: Draft) {
        if (!wanted(draft)) return
        val targets = Webmention.targets(draft.body, draft.postUrl!!)
        // Once: a second report of the same build mustn't send them all again.
        if (targets.isNotEmpty() && drafts.startWebmentions(draft.id, targets.map { Mention(it) }) > 0) queue(draft.id)
    }

    /** Post [id]'s waiting webmentions, sent: true when none is left waiting. */
    suspend fun send(id: Long): Boolean {
        val draft = drafts.get(id) ?: return true
        if (!wanted(draft)) {
            // The switch went off, or the post was hidden, since it was queued: nothing more goes.
            drafts.setWebmentions(id, draft.webmentions.filter { it.state != Mention.State.Waiting })
            return true
        }
        val webmention = webmentionFor(draft.blog!!)
        for (mention in draft.webmentions.filter { it.state == Mention.State.Waiting }) {
            val done = try {
                when (webmention.send(draft.postUrl!!, mention.target)) {
                    Webmention.Outcome.Sent -> mention.copy(state = Mention.State.Sent)
                    Webmention.Outcome.NoEndpoint -> mention.copy(state = Mention.State.NoEndpoint)
                    Webmention.Outcome.Refused -> mention.copy(state = Mention.State.Refused)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoVpnException) {
                // Nothing was tried: waiting for the VPN doesn't count against the site.
                mention
            } catch (e: IOException) {
                val tries = mention.tries + 1
                mention.copy(tries = tries, state = if (tries >= MAX_TRIES) Mention.State.Failed else Mention.State.Waiting)
            } catch (e: Exception) {
                // A page the parser or OkHttp can't make sense of won't read better later.
                mention.copy(state = Mention.State.Refused)
            }
            record(id, done)
        }
        return drafts.get(id)?.webmentions.orEmpty().none { it.state == Mention.State.Waiting }
    }

    private val lock = Mutex()

    /** [mention]'s outcome, written over the row's own list as it is now: another run may have written since. */
    private suspend fun record(id: Long, mention: Mention) = lock.withLock {
        val now = drafts.get(id)?.webmentions ?: return@withLock
        drafts.setWebmentions(id, now.map { if (it.target == mention.target && it.state == Mention.State.Waiting) mention else it })
    }

    private suspend fun wanted(draft: Draft): Boolean =
        draft.state == PostState.Published && (draft.buildState == BuildState.Live || draft.buildState == BuildState.Unknown) &&
            draft.destination == Destination.Posts && !draft.hidden && draft.postUrl != null && draft.blog != null && privacy.get(draft.blog).sendWebmentions

    companion object {
        /** Times a site that can't be reached is tried before it counts as failed. */
        const val MAX_TRIES = 5
    }
}
