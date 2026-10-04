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
import com.app.jekyllposter.core.blog.SiteIndex
import com.app.jekyllposter.data.AccountStore
import com.app.jekyllposter.data.BlogRepository
import com.app.jekyllposter.data.BuildState
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.DraftDao
import com.app.jekyllposter.data.PostState
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
        val account = accounts.current() ?: return fail(draft, "Sign in to publish.")
        if (draft.blog != null && draft.blog != account.blogKey) {
            return fail(draft, "This post was written for ${draft.blog.substringBefore('@')}. Sign in to that blog to publish it.")
        }
        val blog = blogs.blog(account)
        return try {
            val index = blogs.refresh() ?: return Outcome.Retry
            var date: ZonedDateTime?
            val expect: Map<String, String?>
            val (path, text, message) = if (draft.editingPath != null) {
                val current = blog.file(draft.editingPath) ?: return fail(draft, "This post isn't on the blog any more.")
                val doc = PostWriter.edit(FrontMatterDocument.parse(current.text), draft.content())
                val rendered = doc.render()
                // Checked before the conflict rule: after a crash, the post on GitHub may be this
                // very edit, which no longer has the sha the writer opened.
                if (current.text == rendered) return published(draft, draft.editingPath, null, null)
                if (current.sha != draft.baseSha) {
                    return fail(draft, "This post changed on GitHub since you opened it. Discard your changes to start again from the new version (copy your text first).")
                }
                date = doc.string("date")?.let { parseJekyllDate(it) }
                    ?: PostPath(draft.editingPath).date?.atStartOfDay(index.config.timezone ?: java.time.ZoneOffset.UTC)
                expect = mapOf(draft.editingPath to current.sha)
                Triple(draft.editingPath, rendered, "Update post: ${draft.title}")
            } else {
                var fixed = fixTarget(draft, index.paths)
                date = ZonedDateTime.parse(fixed.publishDate)
                var rendered = PostWriter.newPost(draft.content(), date, index.config).render()
                val there = if (fixed.targetPath!! in index.paths) blog.read(fixed.targetPath!!) else null
                if (there == rendered) {
                    val url = postUrl(index, fixed.targetPath!!, date, draft)
                    return published(fixed, fixed.targetPath!!, null, url)
                }
                if (there != null) {
                    // The name was taken by something else since it was picked: pick again.
                    fixed = fixTarget(fixed.copy(targetPath = null, publishDate = null), index.paths)
                    date = ZonedDateTime.parse(fixed.publishDate)
                    rendered = PostWriter.newPost(draft.content(), date, index.config).render()
                }
                expect = mapOf(fixed.targetPath!! to null)
                Triple(fixed.targetPath!!, rendered, "Add post: ${draft.title}")
            }
            // Photos go in the same commit, so the post never goes live with missing pictures.
            val images = draft.images.filter { Images.isUsed(draft.body, it.sitePath) }.map {
                val file = File(it.file)
                if (!file.exists()) return fail(draft, "A photo in this post is no longer on the phone. Remove it from the text and try again.")
                FileChange(it.sitePath.removePrefix("/"), file.readBytes())
            }
            val sha = blog.commit(message, listOf(FileChange.text(path, text)) + images, expect)
            published(drafts.get(id) ?: draft, path, sha, date?.let { postUrl(index, path, it, draft) })
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

    private fun postUrl(index: SiteIndex, path: String, date: ZonedDateTime, draft: Draft): String? {
        val postPath = PostPath(path)
        if (postPath.isDraft) return null
        return index.siteUrl + Permalink.path(index.config, date, postPath.slug, postPath.folderCategories + draft.categories)
    }

    /** Marks the post published; [sha] is null when an earlier attempt's commit had already landed. */
    private suspend fun published(draft: Draft, path: String, sha: String?, url: String?): Outcome {
        val latest = drafts.get(draft.id) ?: draft
        drafts.update(
            latest.copy(
                state = PostState.Published, targetPath = path, commitSha = sha ?: latest.commitSha,
                postUrl = url ?: latest.postUrl,
                buildState = if (sha != null) BuildState.Building else latest.buildState ?: BuildState.Unknown,
                error = null, updatedAt = System.currentTimeMillis(),
            ),
        )
        runCatching { blogs.refresh() }
        return Outcome.Done
    }

    /** Picks the new post's path and date once, avoiding any file already on the branch. */
    private suspend fun fixTarget(draft: Draft, existing: Set<String>): Draft {
        if (draft.targetPath != null && draft.publishDate != null) return draft
        val date = now()
        val slug = Slug.of(draft.title).ifEmpty { "post" }
        var path = PostPath.newPost(date.toLocalDate(), slug).path
        var n = 2
        while (path in existing) path = PostPath.newPost(date.toLocalDate(), "$slug-${n++}").path
        val fixed = draft.copy(targetPath = path, publishDate = date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        drafts.update(fixed)
        return fixed
    }

    private suspend fun fail(draft: Draft, message: String): Outcome {
        drafts.update((drafts.get(draft.id) ?: draft).copy(state = PostState.Failed, error = message, updatedAt = System.currentTimeMillis()))
        return Outcome.Failed(message)
    }
}

/** A front matter date as Jekyll writes them, `2025-03-02 18:05:00 -0800`, or a bare day. */
internal fun parseJekyllDate(value: String): ZonedDateTime? {
    val v = value.trim()
    val patterns = listOf("yyyy-MM-dd HH:mm:ss Z", "yyyy-MM-dd HH:mm:ss XXX", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd HH:mm Z")
    patterns.forEach { p -> runCatching { return ZonedDateTime.parse(v, DateTimeFormatter.ofPattern(p)) } }
    return runCatching { java.time.LocalDate.parse(v.take(10)).atStartOfDay(java.time.ZoneOffset.UTC) }.getOrNull()
}

fun Draft.content() = PostContent(title.trim(), body, categories, tags)
