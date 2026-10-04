package com.app.jekyllposter.core.github

import com.app.jekyllposter.core.jekyll.Permalink
import com.app.jekyllposter.core.jekyll.PostContent
import com.app.jekyllposter.core.jekyll.PostPath
import com.app.jekyllposter.core.jekyll.PostWriter
import com.app.jekyllposter.core.jekyll.SiteConfig
import com.app.jekyllposter.core.jekyll.Slug
import com.app.jekyllposter.core.obsidian.ObsidianNote
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
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
 * deletes it. The post starts as an Obsidian note, with a `[[link]]` and a find/replace rule. Only `./gradlew :core:liveCheck -PliveRepo=owner/name` runs it (see build.gradle.kts),
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
        // Written as an Obsidian note, so the build checks the converter's output too: a
        // `post_url` to a post that isn't there would fail the whole site's build.
        // A post the site builds, so its post_url resolves: dated today or before, not unpublished.
        val today = now.withZoneSameInstant(config.timezone ?: java.time.ZoneOffset.UTC).toLocalDate()
        val linked = github.files(owner, name, branch).map { PostPath(it.path) }
            .filter { it.isPost && !it.isDraft && (it.date?.let { d -> d <= today } ?: false) && ObsidianNote.postUrlName(it.path) != null }
            .sortedByDescending { it.date }
            .firstOrNull { github.text(owner, name, branch, it.path)?.contains(Regex("(?m)^published:\\s*false")) == false }
        val note = "---\nfind: [Hidden Name]\nreplace: [a friend]\n---\n" +
            "Posted and deleted by Jekyll Poster's live check, for Hidden Name." +
            (linked?.let { " See [[${it.fileName.substringBeforeLast('.')}|the linked post]]." } ?: "") + "\n"
        val converted = ObsidianNote.convert(note, "$title.md", listOfNotNull(linked).map { ObsidianNote.LinkTarget(it.path, "") }) as ObsidianNote.Result.Converted
        val slug = Slug.of(title)
        val path = PostPath.newPost(now.toLocalDate(), slug).path
        val text = PostWriter.newPost(PostContent(converted.title, converted.body), now, config).render()

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
            val page = waitFor("$url to show the post", minutes = 5) { fetch(url)?.takeIf { title in it } }
            println("Live at $url")
            assertTrue("The find/replace rule wasn't applied", "Hidden Name" !in page && "a friend" in page)
            if (linked != null) {
                // The link must open the post: a missing baseurl or a wrong post_url name would 404.
                val href = Regex("""<a href="([^"]+)">the linked post</a>""").find(page)?.groupValues?.get(1)
                    ?: throw AssertionError("The [[link]] didn't become a link")
                val target = url.toHttpUrl().resolve(href)!!.toString()
                assertTrue("The link to ${linked.path} doesn't open: $target", fetch(target) != null)
                println("Its link opens $target")
            }
        } finally {
            val sent = github.file(owner, name, branch, path)
            if (sent != null) {
                val gone = github.commit(owner, name, branch, "Delete post: $title", listOf(FileChange.delete(path)), expect = mapOf(path to sent.sha))
                println("Deleted $path in $gone")
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
