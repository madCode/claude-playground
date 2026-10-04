package com.app.jekyllposter.core.blog

import com.app.jekyllposter.core.github.FileChange
import com.app.jekyllposter.core.github.GitHubClient
import com.app.jekyllposter.core.github.GitHubException
import com.app.jekyllposter.core.github.TreeEntry
import com.app.jekyllposter.core.jekyll.Term
import com.app.jekyllposter.core.testing.FakeGitHub
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BlogTest {
    private val github = FakeGitHub.sampleBlog()
    private val client = GitHubClient(OkHttpClient(), "good-token", github.apiBase)
    private val blog = Blog(client, "sample", "sample-blog", "main")

    @After fun close() = github.close()

    @Test fun readsTheSampleBlogsPostsCategoriesAndTags() = runTest {
        val index = blog.index()
        assertEquals(6, index.posts.size)
        assertEquals("Sourdough, again: a 72% loaf", index.posts.first { it.path.slug == "sourdough-again" }.title)
        assertEquals(
            listOf("writing", "bread", "cooking", "meta", "travel"),
            index.taxonomy.categories.map { it.name },
        )
        assertEquals(Term("writing", 3), index.taxonomy.categories.first())
        assertEquals(Term("weekend", 2), index.taxonomy.tags.first())
        assertEquals("assets/images", index.imageFolder)
        assertEquals("America/Los_Angeles", index.config.timezone?.id)
        assertEquals("post", index.config.defaultPostLayout)
    }

    @Test fun aSecondReadOnlyFetchesWhatChanged() = runTest {
        val first = blog.index()
        github.push("Edit on a laptop", mapOf("_posts/2025-01-12-welcome.md" to "---\ntitle: Hello again\ncategories: [meta]\n---\n"))
        github.log.clear()
        val second = blog.index(first.posts.associateBy { it.path.path })
        assertEquals("Hello again", second.posts.first { it.path.slug == "welcome" }.title)
        assertEquals(1, github.log.count { it.startsWith("POST graphql") })
        github.log.clear()
        blog.index(second.posts.associateBy { it.path.path })
        // Only the config is fetched again; every post is known.
        assertEquals(1, github.log.count { it.startsWith("POST graphql") })
    }

    @Test fun aPostAndItsImageLandInOneCommitKeepingEverythingElse() = runTest {
        val before = github.files().keys
        val sha = blog.commit(
            "Add post",
            listOf(FileChange.text("_posts/2026-10-04-hi.md", "---\ntitle: Hi\n---\n"), FileChange("assets/images/hi.jpg", byteArrayOf(1, 0, 2))),
        )
        assertEquals(github.head, sha)
        assertEquals("Add post", github.commits.getValue(sha).message)
        assertEquals(before + setOf("_posts/2026-10-04-hi.md", "assets/images/hi.jpg"), github.files().keys)
        assertTrue(github.files().getValue("assets/images/hi.jpg").contentEquals(byteArrayOf(1, 0, 2)))
    }

    @Test fun aPushInBetweenIsKeptAndTheCommitGoesOnTop() = runTest {
        github.beforeRefUpdate = { github.push("Laptop push", mapOf("about.md" to "About")) }
        blog.commit("Add post", listOf(FileChange.text("_posts/2026-10-04-hi.md", "x")))
        assertEquals("About", github.text("about.md"))
        assertEquals("x", github.text("_posts/2026-10-04-hi.md"))
    }

    @Test fun aCommitThatExpectsAFileUnchangedRefusesWhenItChangedMeanwhile() = runTest {
        val path = "_posts/2025-01-12-welcome.md"
        val seen = client.file("sample", "sample-blog", "main", path)!!.sha
        github.beforeRefUpdate = { github.push("Laptop edit", mapOf(path to "edited elsewhere")) }
        // The first attempt loses the race to the laptop's push; the rebuilt one sees the change.
        try {
            blog.commit("Edit", listOf(FileChange.text(path, "mine")), expect = mapOf(path to seen))
            fail()
        } catch (e: GitHubException) {
            assertEquals(GitHubException.Kind.Changed, e.kind)
        }
        assertEquals("edited elsewhere", github.text(path))
        try {
            blog.commit("New", listOf(FileChange.text("_posts/2025-01-12-welcome.md", "x")), expect = mapOf(path to null))
            fail()
        } catch (e: GitHubException) {
            assertEquals(GitHubException.Kind.Changed, e.kind)
        }
    }

    @Test fun aRepositoryTooBigToListIsRefusedRatherThanReadInPart() = runTest {
        github.truncated = true
        try { blog.index(); fail() } catch (e: GitHubException) { assertTrue(e.message!!.contains("too big")) }
    }

    @Test fun identicalFilesAtTwoPathsAreTwoPosts() = runTest {
        github.push("Copy", mapOf("_drafts/welcome.md" to github.text("_posts/2025-01-12-welcome.md")))
        val first = blog.index()
        assertEquals(2, first.posts.count { it.path.slug == "welcome" })
        assertEquals(2, blog.index(first.posts.associateBy { it.path.path }).posts.count { it.path.slug == "welcome" })
    }

    @Test fun deletingAFile() = runTest {
        blog.commit("Remove draft", listOf(FileChange.delete("_drafts/garden-plans.md")))
        assertNull(github.text("_drafts/garden-plans.md"))
    }

    @Test fun errorsAreSortedByWhatTheWriterCanDo() = runTest {
        github.token = "other"
        try { blog.index(); fail() } catch (e: GitHubException) { assertEquals(GitHubException.Kind.Unauthorized, e.kind) }
        github.token = "good-token"
        try { Blog(client, "someone", "else", "main").index(); fail() } catch (e: GitHubException) { assertEquals(GitHubException.Kind.NoAccess, e.kind) }
        github.failures["repos/sample/sample-blog/git/blobs"] = 502
        try { blog.commit("x", listOf(FileChange.text("a.md", "a"))); fail() } catch (e: GitHubException) { assertTrue(e.retryable) }
    }

    @Test fun networkFailureIsRetryable() = runTest {
        val dead = GitHubClient(OkHttpClient(), "t", okhttp3.HttpUrl.Builder().scheme("http").host("127.0.0.1").port(1).build())
        try { dead.user(); fail() } catch (e: GitHubException) { assertEquals(GitHubException.Kind.Network, e.kind); assertTrue(e.retryable) }
    }

    @Test fun userReposAndReadingFiles() = runTest {
        assertEquals("sample", client.user().login)
        assertEquals(listOf("sample/sample-blog"), client.writableRepos().map { it.fullName })
        assertTrue(blog.read("_config.yml")!!.contains("A Sample Notebook"))
        assertNull(blog.read("missing.md"))
        assertEquals("https://sample.github.io/sample-blog/", client.pages("sample", "sample-blog")?.htmlUrl)
    }

    @Test fun workflowRunsForACommit() = runTest {
        github.runs["abc"] = "completed" to "success"
        assertEquals("success", client.workflowRuns("sample", "sample-blog", "abc").single().conclusion)
        assertTrue(client.workflowRuns("sample", "sample-blog", "def").isEmpty())
    }

    @Test fun imageFolderFollowsTheBlog() {
        fun files(vararg p: String) = p.map { TreeEntry(it, "blob", it) }
        assertEquals("images", Blog.imageFolder(files("images/a.png", "images/b.png", "assets/img/c.png")))
        assertEquals("assets/images", Blog.imageFolder(files("favicon.ico", "assets/favicons/x.png", "_site/a.png")))
        assertEquals("pics", Blog.imageFolder(files("pics/a.jpg")))
        assertEquals("assets/img", Blog.imageFolder(files("assets/img/2024/a.jpg")))
        assertFalse(Blog.imageFolder(emptyList()).isEmpty())
    }

    @Test fun onlyAWorkflowBuildingWithJekyll4PutsTheBaseurlInPostUrl() {
        val build = "steps:\n  - run: bundle exec jekyll build --baseurl /blog\n"
        assertTrue(Blog.buildsWithJekyll4("source 'https://rubygems.org'\ngem \"jekyll\", \"~> 4.3\"\n", listOf(build)))
        // GitHub's own build ignores the Gemfile; its Pages action is Jekyll 3.
        assertFalse(Blog.buildsWithJekyll4("gem \"jekyll\", \"~> 4.3\"\n", listOf("uses: actions/jekyll-build-pages@v1")))
        assertFalse(Blog.buildsWithJekyll4("gem \"github-pages\", group: :jekyll_plugins\n", listOf(build)))
        assertFalse(Blog.buildsWithJekyll4(null, listOf(build)))
    }
}
