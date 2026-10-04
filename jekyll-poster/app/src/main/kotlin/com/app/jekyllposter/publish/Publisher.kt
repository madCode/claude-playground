package com.app.jekyllposter.publish

import com.app.jekyllposter.core.frontmatter.FrontMatterDocument
import com.app.jekyllposter.core.github.FileChange
import com.app.jekyllposter.core.github.GitHubException
import com.app.jekyllposter.core.jekyll.PostContent
import com.app.jekyllposter.core.jekyll.PostPath
import com.app.jekyllposter.core.jekyll.PostWriter
import com.app.jekyllposter.core.jekyll.Slug
import com.app.jekyllposter.data.BlogRepository
import com.app.jekyllposter.data.BuildState
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.DraftDao
import com.app.jekyllposter.data.PostState
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
    private val blogs: BlogRepository,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    sealed interface Outcome {
        data object Done : Outcome
        data object Retry : Outcome
        data class Failed(val message: String) : Outcome
    }

    suspend fun publish(id: Long): Outcome {
        val draft = drafts.get(id) ?: return Outcome.Done
        if (draft.state != PostState.Queued) return Outcome.Done
        val blog = blogs.blog() ?: return fail(draft, "Sign in to publish.")
        return try {
            val index = blog.index()
            val (path, text, message) = if (draft.editingPath != null) {
                val current = index.posts.firstOrNull { it.path.path == draft.editingPath }
                    ?: return fail(draft, "This post isn't on the blog any more.")
                if (current.sha != draft.baseSha) {
                    return fail(draft, "This post changed on GitHub since you opened it. Open it again to get the new version.")
                }
                val original = blog.read(draft.editingPath) ?: return fail(draft, "This post isn't on the blog any more.")
                val doc = PostWriter.edit(FrontMatterDocument.parse(original), draft.content())
                Triple(draft.editingPath, doc.render(), "Update post: ${draft.title}")
            } else {
                val fixed = fixTarget(draft, index.paths)
                val date = ZonedDateTime.parse(fixed.publishDate)
                val doc = PostWriter.newPost(draft.content(), date, index.config)
                Triple(fixed.targetPath!!, doc.render(), "Add post: ${draft.title}")
            }
            val landed = path in index.paths && blog.read(path) == text
            val sha = if (landed) null else blog.commit(message, listOf(FileChange.text(path, text)))
            val latest = drafts.get(id) ?: draft
            drafts.update(
                latest.copy(
                    state = PostState.Published, targetPath = path, commitSha = sha ?: latest.commitSha,
                    buildState = if (sha != null) BuildState.Building else latest.buildState ?: BuildState.Unknown,
                    error = null, updatedAt = System.currentTimeMillis(),
                ),
            )
            runCatching { blogs.refresh() }
            Outcome.Done
        } catch (e: GitHubException) {
            when {
                e.retryable -> Outcome.Retry
                e.kind == GitHubException.Kind.Unauthorized -> fail(draft, "GitHub didn't accept the sign-in. Sign in again, then publish.")
                e.kind == GitHubException.Kind.NoAccess -> fail(draft, "Your token can't write to this repository. Give it Contents: read and write.")
                else -> fail(draft, "GitHub said: ${e.message}")
            }
        }
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

fun Draft.content() = PostContent(title.trim(), body, categories, tags)
