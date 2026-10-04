package com.app.jekyllposter.core.github

import com.app.jekyllposter.core.jekyll.Permalink
import com.app.jekyllposter.core.jekyll.PostContent
import com.app.jekyllposter.core.jekyll.PostPath
import com.app.jekyllposter.core.jekyll.PostWriter
import com.app.jekyllposter.core.jekyll.SiteConfig
import com.app.jekyllposter.core.jekyll.Slug
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Publishes a post to a real blog through the real GitHub API, waits for Pages to serve it, then
 * deletes it. Only `./gradlew :core:liveCheck -PliveRepo=owner/name` runs it (see build.gradle.kts),
 * with a token that can write that repository's contents in `$SAMPLE_BLOG_TOKEN`; the fake GitHub
 * the other tests use can't tell whether GitHub still answers the way the app expects.
 */
class LiveCheckTest {
    private val repo = System.getProperty("liveRepo").orEmpty()
    private val token = System.getenv("SAMPLE_BLOG_TOKEN").orEmpty()
    private val http = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()

    @Test
    fun publishesAndDeletesAPost() = runBlocking {
        assumeTrue("Run with -PliveRepo=owner/name", repo.contains('/'))
        assumeTrue("Set SAMPLE_BLOG_TOKEN", token.isNotBlank())
        val (owner, name) = repo.split('/', limit = 2)
        val github = GitHubClient(http, token)

        val info = github.repo(owner, name)
        assertTrue("The token can't write to $repo", info.canPush)
        val branch = info.defaultBranch
        val config = SiteConfig.parse(github.text(owner, name, branch, "_config.yml"))
        // Not the Pages endpoint: a cloud session's GitHub proxy refuses it.
        val siteUrl = Permalink.siteUrl(config, owner, name, github.text(owner, name, branch, "CNAME"))

        val now = ZonedDateTime.now()
        val title = "Live check ${now.toEpochSecond()}"
        val slug = Slug.of(title)
        val path = PostPath.newPost(now.toLocalDate(), slug).path
        val text = PostWriter.newPost(PostContent(title, "Posted and deleted by Jekyll Poster's live check."), now, config).render()

        val sha = github.commit(owner, name, branch, "Add post: $title", listOf(FileChange.text(path, text)), expect = mapOf(path to null))
        println("Published $path in $sha")
        try {
            val there = github.file(owner, name, branch, path)
            assertEquals(text, there?.text)

            val run = waitFor("the Pages build of $sha", minutes = 10) {
                github.workflowRuns(owner, name, sha).firstOrNull { it.path?.contains("pages") == true && it.status == "completed" }
            }
            assertEquals("Pages build ${run.htmlUrl}", "success", run.conclusion)

            val url = siteUrl + Permalink.path(config, now, slug, emptyList())
            // The build finishing doesn't mean the CDN serves the page yet.
            waitFor("$url to show the post", minutes = 5) { fetch(url)?.takeIf { title in it } }
            println("Live at $url")
        } finally {
            val sent = github.file(owner, name, branch, path)
            if (sent != null) {
                val gone = github.commit(owner, name, branch, "Delete post: $title", listOf(FileChange.delete(path)), expect = mapOf(path to sent.sha))
                println("Deleted $path in $gone")
            }
        }
        assertNull(github.file(owner, name, branch, path))
    }

    /**
     * Commits as the account's no-reply address, as "Commit with your no-reply email" does, and
     * checks GitHub still attributes the commit to the account. Prints whether it's Verified,
     * which the address may cost; then deletes the file. No Pages wait: the build isn't the point.
     */
    @Test
    fun commitsAsTheNoReplyAddressAndStaysTheAccounts() = runBlocking {
        assumeTrue("Run with -PliveRepo=owner/name", repo.contains('/'))
        assumeTrue("Set SAMPLE_BLOG_TOKEN", token.isNotBlank())
        val (owner, name) = repo.split('/', limit = 2)
        val github = GitHubClient(http, token)
        val branch = github.repo(owner, name).defaultBranch
        val user = github.user()
        val author = user.noReplyAuthor
        assertTrue("GitHub gave no account id for ${user.login}", author != null)

        val path = "_drafts/live-check-author-${ZonedDateTime.now().toEpochSecond()}.md"
        val sha = github.commit(
            owner, name, branch, "Add draft: live check of the commit author",
            listOf(FileChange.text(path, "---\ntitle: Live check\n---\n")), expect = mapOf(path to null), author = author,
        )
        try {
            val view = github.commitView(owner, name, sha)
            println("Committed as ${view.commit.author.email}; GitHub attributes it to ${view.login}; verified: ${view.commit.verification?.verified} (${view.commit.verification?.reason})")
            assertEquals(author!!.email, view.commit.author.email)
            assertEquals("GitHub doesn't link ${author.email} to ${user.login}", user.login, view.login)
        } finally {
            github.file(owner, name, branch, path)?.let { sent ->
                github.commit(owner, name, branch, "Delete draft: live check of the commit author", listOf(FileChange.delete(path)), expect = mapOf(path to sent.sha), author = author)
            }
        }
        assertNull(github.file(owner, name, branch, path))
    }

    private fun fetch(url: String): String? =
        http.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute().use { if (it.isSuccessful) it.body.string() else null }

    private suspend fun <T : Any> waitFor(what: String, minutes: Long, poll: suspend () -> T?): T {
        val until = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes)
        while (System.currentTimeMillis() < until) {
            poll()?.let { return it }
            delay(10_000)
        }
        throw AssertionError("Gave up waiting for $what after $minutes minutes")
    }
}
