package com.app.jekyllposter.publish

import com.app.jekyllposter.core.frontmatter.FrontMatterDocument
import com.app.jekyllposter.core.github.FileChange
import com.app.jekyllposter.core.github.GitHubException
import com.app.jekyllposter.core.jekyll.Images
import com.app.jekyllposter.core.jekyll.Permalink
import com.app.jekyllposter.core.jekyll.PostContent
import com.app.jekyllposter.core.jekyll.PostPath
import com.app.jekyllposter.core.jekyll.PostWriter
import com.app.jekyllposter.core.jekyll.Slug
import com.app.jekyllposter.core.blog.Blog
import com.app.jekyllposter.core.blog.SiteIndex
import com.app.jekyllposter.data.AccountStore
import com.app.jekyllposter.data.BlogRepository
import com.app.jekyllposter.data.BuildState
import com.app.jekyllposter.data.Destination
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.DraftDao
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.data.Settings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Sends a queued post to GitHub. Kept apart from the worker so its rules are testable directly:
 *
 * - A new post's path and date are fixed the first time, so a retry writes the same file. If the
 *   file is already there with the same text, an earlier attempt landed and this one is done.
 * - An edit goes only if the post on GitHub is still the one the writer opened; otherwise it fails
 *   and says so, rather than overwriting what was changed elsewhere.
 */
class Publisher(
    private val drafts: DraftDao,
    private val accounts: AccountStore,
    private val blogs: BlogRepository,
    private val settings: Settings,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    sealed interface Outcome {
        data object Done : Outcome
        data object Retry : Outcome
        data class Failed(val message: String) : Outcome
    }

    /**
     * One publish at a time: two queued posts with the same title would otherwise both see the
     * name free and the second would replace the first.
     */
    private val lock = Mutex()

    suspend fun publish(id: Long): Outcome = lock.withLock {
        repeat(3) { attempt(id)?.let { return it } }
        Outcome.Retry
    }

    /** Null when a file changed under the commit: decide again from the blog as it is now. */
    private suspend fun attempt(id: Long): Outcome? {
        val draft = drafts.get(id) ?: return Outcome.Done
        if (draft.state != PostState.Queued) return Outcome.Done
        val verb = if (draft.destination == Destination.Delete) "delete" else "publish"
        val account = accounts.current() ?: return fail(draft, "Sign in to $verb.")
        if (draft.blog != null && draft.blog != account.blogKey) {
            return fail(draft, "This post was written for ${draft.blog.substringBefore('@')}. Sign in to that blog to $verb it.")
        }
        val blog = blogs.blog(account)
        return try {
            val index = blogs.refresh() ?: return Outcome.Retry
            val editing = draft.editingPath
            val moving = editing != null && PostPath(editing).isDraft && draft.destination == Destination.Posts
            val plan = when {
                draft.destination == Destination.Delete -> planDelete(draft, editing ?: return fail(draft, "Only a post on the blog can be deleted."), blog)
                editing != null && !moving -> planEdit(draft, editing, index, blog) ?: return null
                moving -> planMove(draft, editing!!, index, blog) ?: return null
                else -> planNew(draft, index, blog) ?: return null
            }
            if (plan is Plan.Finished) return plan.outcome
            plan as Plan.Commit
            val author = if (settings.commitAsNoReply()) blog.user().noReplyAuthor else null
            val sha = blog.commit(plan.message, plan.changes, plan.expect, author)
            if (draft.destination == Destination.Delete) return deleted(draft, plan.path)
            published(drafts.get(id) ?: draft, plan.path, sha, plan.date?.let { postUrl(index, plan.path, it, plan.draft) })
        } catch (e: GitHubException) {
            when {
                e.kind == GitHubException.Kind.Changed -> null
                e.retryable -> Outcome.Retry
                e.kind == GitHubException.Kind.Unauthorized -> fail(draft, "GitHub didn't accept the sign-in. Sign in again, then publish.")
                e.kind == GitHubException.Kind.NoAccess -> fail(draft, "Your token can't write to this repository. Give it Contents: read and write.")
                else -> fail(draft, "GitHub said: ${e.message}")
            }
        } catch (e: IOException) {
            // Reading a photo from the phone's storage, say; worth another try.
            Outcome.Retry
        }
    }

    private sealed interface Plan {
        class Finished(val outcome: Outcome) : Plan
        class Commit(
            val draft: Draft,
            val path: String,
            val message: String,
            val changes: List<FileChange>,
            val expect: Map<String, String?>,
            val date: ZonedDateTime?,
        ) : Plan
    }

    /** An edit of a post or Jekyll draft, in place. */
    private suspend fun planEdit(draft: Draft, path: String, index: SiteIndex, blog: Blog): Plan? {
        val current = blog.file(path) ?: return Plan.Finished(fail(draft, "This post isn't on the blog any more."))
        // Checked before the conflict rule: after a crash, the post on GitHub may be this very
        // edit, which no longer has the sha the writer opened.
        if (current.text == PostWriter.edit(FrontMatterDocument.parse(current.text), draft.content()).render()) {
            return Plan.Finished(published(draft, path, null, null))
        }
        if (current.sha != draft.baseSha) return Plan.Finished(fail(draft, CHANGED))
        // The same clock as a new post's date, so a photo lands in the year folder its post would.
        val year = (index.config.timezone?.let { now().withZoneSameInstant(it) } ?: now()).year
        val (ready, photos) = photos(draft, index, ours = emptySet(), PostPath(path).slug, year) ?: return Plan.Finished(fail(draft, PHOTO_GONE))
        val doc = PostWriter.edit(FrontMatterDocument.parse(current.text), ready.content())
        val date = doc.string("date")?.let { parseJekyllDate(it, siteZone(index)) }
            ?: PostPath(path).date?.atStartOfDay(siteZone(index))
        val kind = if (PostPath(path).isDraft) "draft" else "post"
        return Plan.Commit(
            ready, path, "Update $kind: ${ready.title}",
            listOf(FileChange.text(path, doc.render())) + photos.changes,
            mapOf(path to current.sha) + photos.expect, date,
        )
    }

    /**
     * A post or Jekyll draft deleted from the blog, only if it's still the version the writer
     * opened: they decided on what they saw. [Draft.targetPath] is set to the path before the
     * commit, so a file found gone afterwards is told apart: this phone's commit landed unheard,
     * or the post was moved or deleted elsewhere, which the writer is told about.
     */
    private suspend fun planDelete(draft: Draft, path: String, blog: Blog): Plan {
        val current = blog.file(path)
        if (current == null) {
            if (draft.targetPath == path) return Plan.Finished(deleted(draft, path))
            return Plan.Finished(fail(draft, "This post isn't at $path any more: it was moved or deleted elsewhere. Discard this, then look for it on the blog."))
        }
        if (current.sha != draft.baseSha) {
            return Plan.Finished(fail(draft, "This post changed on GitHub since you opened it, so it wasn't deleted. Discard your changes to start again from the new version."))
        }
        val marked = draft.copy(targetPath = path).also { drafts.update(it) }
        val kind = if (PostPath(path).isDraft) "draft" else "post"
        val title = draft.title.trim().ifEmpty { PostPath(path).slug }
        // The photos stay: another post may show them too, and the history keeps the text anyway.
        return Plan.Commit(marked, path, "Delete $kind: $title", listOf(FileChange.delete(path)), mapOf(path to current.sha), null)
    }

    /**
     * The post is off the blog; so is the phone's record of it, any photo it was waiting to send,
     * and earlier published updates of it, whose build watch would otherwise call it live.
     */
    private suspend fun deleted(draft: Draft, path: String): Outcome {
        (drafts.get(draft.id) ?: draft).images.forEach { File(it.file).delete() }
        drafts.delete(draft.id)
        draft.blog?.let { drafts.deletePublishedEditsOf(path, it) }
        runCatching { blogs.refresh() }
        return Outcome.Done
    }

    /** A Jekyll draft published from the phone: dated, written to _posts, out of _drafts, in one commit. */
    private suspend fun planMove(draft: Draft, path: String, index: SiteIndex, blog: Blog): Plan? {
        val current = blog.file(path)
        if (current == null) {
            // Gone from _drafts. If what's at the new name is what this phone sent, an earlier
            // attempt landed; otherwise it was published or deleted elsewhere, and the phone's
            // changes (and photos) are kept for the writer rather than marked done.
            val target = draft.targetPath?.takeIf { it in index.paths }?.let { blog.file(it) }
            if (target != null && target.sha in draft.sentShas) return Plan.Finished(published(draft, draft.targetPath!!, null, null))
            return Plan.Finished(fail(draft, "This draft isn't in _drafts any more: it was published or removed elsewhere. Copy your text to keep it."))
        }
        if (current.sha != draft.baseSha) return Plan.Finished(fail(draft, CHANGED))
        // A name picked on an earlier attempt and taken since is picked again.
        val stale = draft.targetPath?.let { it in index.paths } == true
        val fixed = fixTarget(if (stale) draft.copy(targetPath = null, publishDate = null) else draft, index)
        val date = ZonedDateTime.parse(fixed.publishDate)
        val target = fixed.targetPath!!
        // Named once the post's own name is known.
        val (ready, photos) = photos(fixed, index, ours = emptySet(), PostPath(target).slug, date.year) ?: return Plan.Finished(fail(draft, PHOTO_GONE))
        val doc = PostWriter.edit(FrontMatterDocument.parse(current.text), ready.content())
        doc.setRaw("date", PostWriter.timestamp(date))
        drafts.update(ready.copy(sentShas = (ready.sentShas + gitBlobSha(doc.render())).distinct()))
        return Plan.Commit(
            ready, target, "Publish draft: ${ready.title}",
            listOf(FileChange.text(target, doc.render()), FileChange.delete(path)) + photos.changes,
            mapOf(path to current.sha, target to null) + photos.expect, date,
        )
    }

    /** A new post, or a new Jekyll draft. */
    private suspend fun planNew(draft: Draft, index: SiteIndex, blog: Blog): Plan? {
        val toDrafts = draft.destination == Destination.Drafts
        fun render(d: Draft, at: ZonedDateTime) =
            if (toDrafts) PostWriter.newDraft(d.content(), index.config).render() else PostWriter.newPost(d.content(), at, index.config).render()
        var fixed = fixTarget(draft, index)
        var date = ZonedDateTime.parse(fixed.publishDate)
        val there = fixed.targetPath!!.takeIf { it in index.paths }?.let { blog.file(it) }
        if (there?.text == render(fixed, date)) return Plan.Finished(published(fixed, fixed.targetPath!!, null, postUrl(index, fixed.targetPath!!, date, fixed)))
        // The file is there but different. If it's what this post last sent, an earlier attempt
        // landed unheard and the writer has edited since: update it. Otherwise the name was taken
        // by something else meanwhile: pick another.
        val ours = there != null && there.sha in fixed.sentShas
        if (there != null && !ours) {
            fixed = fixTarget(fixed.copy(targetPath = null, publishDate = null), index)
            date = ZonedDateTime.parse(fixed.publishDate)
        }
        // Photos already on the branch from that landed attempt are this post's own.
        val sent = if (ours) fixed.images.map { it.sitePath.removePrefix("/") }.filter { it in index.paths }.toSet() else emptySet()
        val (ready, photos) = photos(fixed, index, sent, PostPath(fixed.targetPath!!).slug, date.year) ?: return Plan.Finished(fail(draft, PHOTO_GONE))
        val target = ready.targetPath!!
        val text = render(ready, date)
        // Added before the commit, never replaced: any of them may be the one that landed.
        drafts.update(ready.copy(sentShas = (ready.sentShas + gitBlobSha(text)).distinct()))
        val verb = when {
            ours -> "Update ${if (toDrafts) "draft" else "post"}"
            toDrafts -> "Add draft"
            else -> "Add post"
        }
        return Plan.Commit(
            ready, target, "$verb: ${ready.title}",
            listOf(FileChange.text(target, text)) + photos.changes,
            mapOf(target to there?.sha?.takeIf { ours }) + photos.expect, date,
        )
    }

    private class Photos(val changes: List<FileChange>, val expect: Map<String, String?>)

    /**
     * The photos the text still links to, as file changes, each expected not to exist yet. Each is
     * named for the post, [name] in [year]'s folder, in the text too: added under a placeholder,
     * or taken on the blog meanwhile (where it would replace a picture in an older post). [ours]
     * are already there from this post's own earlier attempt. Null when a photo's file is gone
     * from the phone.
     */
    private suspend fun photos(draft: Draft, index: SiteIndex, ours: Set<String>, name: String, year: Int): Pair<Draft, Photos>? {
        var body = draft.body
        // Names given out in this pass count as taken, so two photos never share one.
        val taken = (index.paths + draft.images.map { it.sitePath }).toMutableSet()
        val images = draft.images.map { image ->
            val path = image.sitePath.removePrefix("/")
            val settled = Images.isNamedFor(image.sitePath, index.imageFolder, name) && path !in index.paths
            if (!Images.isUsed(body, image.sitePath) || path in ours || settled) return@map image
            val ext = image.sitePath.substringAfterLast('.')
            val renamed = Images.sitePath(index.imageFolder, year, name, ext, taken)
            taken += renamed
            body = body.replace(image.sitePath, renamed)
            image.copy(sitePath = renamed)
        }
        val ready = if (body == draft.body) draft else draft.copy(body = body, images = images).also { drafts.update(it) }
        val used = images.filter { Images.isUsed(body, it.sitePath) && it.sitePath.removePrefix("/") !in ours }
        if (used.any { !File(it.file).exists() }) return null
        val changes = used.map { FileChange(it.sitePath.removePrefix("/"), File(it.file).readBytes()) }
        return ready to Photos(changes, changes.associate { it.path to null })
    }

    private fun siteZone(index: SiteIndex) = index.config.timezone ?: java.time.ZoneOffset.UTC

    private fun postUrl(index: SiteIndex, path: String, date: ZonedDateTime, draft: Draft): String? {
        val postPath = PostPath(path)
        if (postPath.isDraft) return null
        // Jekyll puts front matter categories first, then the folders above _posts.
        return index.siteUrl + Permalink.path(index.config, date, postPath.slug, draft.categories + postPath.folderCategories)
    }

    /** Marks the post published; [sha] is null when an earlier attempt's commit had already landed. */
    private suspend fun published(draft: Draft, path: String, sha: String?, url: String?): Outcome {
        val latest = drafts.get(draft.id) ?: draft
        drafts.update(
            latest.copy(
                state = PostState.Published, targetPath = path, commitSha = sha ?: latest.commitSha,
                postUrl = url ?: latest.postUrl,
                // A Jekyll draft isn't built into the site, so there's nothing to watch for it.
                buildState = when {
                    PostPath(path).isDraft -> null
                    sha != null -> BuildState.Building
                    else -> latest.buildState ?: BuildState.Unknown
                },
                error = null, updatedAt = System.currentTimeMillis(),
            ),
        )
        // The photos are on the blog now; the preview loads them from there.
        latest.images.forEach { File(it.file).delete() }
        runCatching { blogs.refresh() }
        return Outcome.Done
    }

    /**
     * Picks the new post's path and date once, avoiding any file already on the branch, and any
     * existing post's address: under a date-free permalink (`/:title/`), a free file name can
     * still be another post's URL, and Jekyll would build one over the other.
     */
    private suspend fun fixTarget(draft: Draft, index: SiteIndex): Draft {
        val toDrafts = draft.destination == Destination.Drafts && draft.editingPath == null
        // Kept across attempts, unless the writer has since chosen the other destination.
        val kept = draft.targetPath?.let { PostPath(it).isDraft == toDrafts } == true
        if (kept && draft.publishDate != null) return draft
        // In the site's time zone when it names one: the phone's offset says where the writer is
        // (a trip abroad shows as +0900), and the site's gives the same day and URL Jekyll will.
        val date = index.config.timezone?.let { now().withZoneSameInstant(it) } ?: now()
        val slug = Slug.of(draft.title).ifEmpty { "post" }
        fun at(s: String) = if (toDrafts) PostPath.newDraft(s).path else PostPath.newPost(date.toLocalDate(), s).path
        val urls = if (toDrafts) emptySet() else index.posts.filterNot { it.path.isDraft }.mapNotNull { post ->
            post.path.date?.let { Permalink.path(index.config, it.atStartOfDay(siteZone(index)), post.path.slug, post.categories) }
        }.toSet()
        fun taken(s: String) = at(s) in index.paths || (!toDrafts && Permalink.path(index.config, date, s, draft.categories) in urls)
        var chosen = slug
        var n = 2
        while (taken(chosen)) chosen = "$slug-${n++}"
        val fixed = draft.copy(targetPath = at(chosen), publishDate = date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        drafts.update(fixed)
        return fixed
    }

    private suspend fun fail(draft: Draft, message: String): Outcome {
        drafts.update((drafts.get(draft.id) ?: draft).copy(state = PostState.Failed, error = message, updatedAt = System.currentTimeMillis()))
        return Outcome.Failed(message)
    }
}

private const val PHOTO_GONE = "A photo in this post is no longer on the phone. Remove it from the text and try again."

private const val CHANGED = "This post changed on GitHub since you opened it. Discard your changes to start again from the new version (copy your text first)."

/**
 * A front matter date as Jekyll reads it: `2025-03-02 18:05:00 -0800`; one without an offset, or a
 * bare day, is in the site's time zone [zone].
 */
internal fun parseJekyllDate(value: String, zone: java.time.ZoneId): ZonedDateTime? {
    val v = value.trim()
    val zoned = listOf("yyyy-MM-dd HH:mm:ss Z", "yyyy-MM-dd HH:mm:ss XXX", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd HH:mm Z")
    zoned.forEach { p -> runCatching { return ZonedDateTime.parse(v, DateTimeFormatter.ofPattern(p)) } }
    listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm").forEach { p ->
        runCatching { return java.time.LocalDateTime.parse(v, DateTimeFormatter.ofPattern(p)).atZone(zone) }
    }
    return runCatching { java.time.LocalDate.parse(v.take(10)).atStartOfDay(zone) }.getOrNull()
}

/** Git's id for a file's content, as GitHub reports it: SHA-1 of `blob <size>\0<bytes>`. */
internal fun gitBlobSha(text: String): String {
    val bytes = text.toByteArray(Charsets.UTF_8)
    val digest = java.security.MessageDigest.getInstance("SHA-1")
    digest.update("blob ${bytes.size}\u0000".toByteArray())
    digest.update(bytes)
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun Draft.content() = PostContent(title.trim(), body, categories, tags, extraFrontMatter)
