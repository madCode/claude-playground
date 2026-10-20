package com.app.jekyllposter.debug

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.publish.Publisher
import com.app.jekyllposter.testutil.SAMPLE_BLOG
import com.app.jekyllposter.testutil.TestApp
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The debug build's request record, from the app's own clients against the fake GitHub. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class RequestRecordTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container
    private val record = RequestRecord.of(app)

    // Shaped like real ones, so hiding them doesn't lean on the header alone.
    private val token = "ghu_" + "A1b2C3d4".repeat(5)
    // Not shaped like one, so only the form field rule can hide it.
    private val refreshToken = "refresh-old-1"

    @Before fun setUp() {
        record.dir.deleteRecursively()
        app.github.token = token
    }

    @After fun close() {
        record.dir.deleteRecursively()
        app.github.close()
    }

    private fun turnOn() {
        record.dir.mkdirs()
        File(record.dir, "on").createNewFile()
    }

    private fun entries(): List<JsonObject> =
        record.file.readLines().map { Json.parseToJsonElement(it).jsonObject }

    private operator fun JsonObject.get(key: String, inner: String) = this[key]!!.jsonObject[inner]?.jsonPrimitive?.content

    private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.content

    private suspend fun signIn(other: Account? = null) =
        c.accounts.save(other ?: Account("sample", token, "sample", "sample-blog", "main"))

    private suspend fun publish(blog: String, title: String) {
        val id = c.drafts.insert(Draft(blog = blog, title = title, body = "Body of $title.", state = PostState.Queued))
        assertEquals(Publisher.Outcome.Done, c.publisher.publish(id))
    }

    @Test fun aPublishIsRecordedForItsBlogWithTheTokenHidden() = runBlocking {
        turnOn()
        signIn()
        c.blogs.refresh()
        publish(SAMPLE_BLOG, "Recorded post")

        val commit = entries().single { it.text("method") == "POST" && it.text("url")!!.endsWith("/git/commits") }
        assertEquals(SAMPLE_BLOG, commit.text("via"))
        assertEquals(RequestRecord.HIDDEN, commit["headers", "Authorization"])
        assertEquals(RequestRecord.fingerprint("Bearer $token"), commit.text("token"))
        // What goes out with the commit is what an audit reads: its message, here.
        assertTrue(commit.text("body")!!.contains("Recorded post"))
        assertEquals("201", commit.text("outcome"))
        assertFalse(record.file.readText().contains(token))
    }

    @Test fun twoSignInsAreToldApartByTheirTokens() = runBlocking {
        turnOn()
        val otherToken = "github_pat_" + "Q1w2E3r4".repeat(6)
        app.github.addAccount("other", otherToken, "other-blog", mapOf("_config.yml" to "title: Other\n".toByteArray()))
        signIn()
        c.blogs.refresh()
        signIn(Account("other", otherToken, "other", "other-blog", "main"))
        c.blogs.refresh()

        val byBlog = entries().groupBy({ it.text("via") }, { it.text("token") })
        assertEquals(setOf(RequestRecord.fingerprint("Bearer $token")), byBlog[SAMPLE_BLOG]!!.toSet())
        assertEquals(setOf(RequestRecord.fingerprint("Bearer $otherToken")), byBlog["other/other-blog@main"]!!.toSet())
        val text = record.file.readText()
        assertFalse(text.contains(token))
        assertFalse(text.contains(otherToken))
    }

    @Test fun aRenewalHidesTheRefreshToken() = runBlocking {
        turnOn()
        // Due for renewal: it expires within the five minutes the app renews ahead of.
        signIn(Account("sample", "ghu_" + "Old0".repeat(10), "sample", "sample-blog", "main", refreshToken = refreshToken, expiresAt = System.currentTimeMillis() + 60_000))
        c.accounts.current()

        val renewal = entries().single { it.text("url")!!.endsWith("login/oauth/access_token") }
        assertEquals("renewal for $SAMPLE_BLOG", renewal.text("via"))
        assertTrue(renewal.text("body")!!.contains("refresh_token=${RequestRecord.HIDDEN}"))
        assertFalse(record.file.readText().contains(refreshToken))
    }

    @Test fun signingInWithGitHubHidesTheDeviceCode() = runBlocking {
        turnOn()
        app.github.deviceApproved = true
        val flow = c.deviceFlow!!
        flow.await(flow.start()) {}

        val poll = entries().single { it.text("url")!!.endsWith("login/oauth/access_token") }
        assertEquals("sign-in", poll.text("via"))
        assertTrue(poll.text("body")!!.contains("device_code=${RequestRecord.HIDDEN}"))
        assertFalse(record.file.readText().contains("device-1"))
    }

    // A backstop behind the header and field rules above, for a secret that turns up anywhere else.
    @Test fun anythingShapedLikeAGitHubTokenIsHidden() {
        val shapes = listOf("ghp_", "gho_", "ghu_", "ghs_", "ghr_", "github_pat_").map { it + "k3Y9".repeat(6) }
        val line = RequestRecord.scrub(shapes.joinToString(" and ", "\"url\": \"", "\""))
        shapes.forEach { assertFalse(it, line.contains(it)) }
        assertEquals(6, Regex(Regex.escape(RequestRecord.HIDDEN)).findAll(line).count())
    }

    @Test fun aPreviewPhotoFromAPrivateFileHidesItsToken() {
        turnOn()
        val link = app.github.apiBase.resolve("sample/private/main/photo.jpg?token=GHSAT0AAAAAB4XYZ")!!.toString()
        c.previewFetcher.fetch(link)

        val fetch = entries().single { it.text("via") == "preview" }
        assertTrue(fetch.text("url")!!.endsWith("photo.jpg?token=${RequestRecord.HIDDEN}"))
        assertFalse(record.file.readText().contains("GHSAT0AAAAAB4XYZ"))
    }

    @Test fun aRecordThatCantBeWrittenDoesntStopAPublish() = runBlocking {
        turnOn()
        // Stands in for a full disk: the file can't be appended to.
        record.file.mkdirs()
        signIn()
        c.blogs.refresh()
        publish(SAMPLE_BLOG, "Still goes out")
        assertTrue(app.github.files().keys.any { it.endsWith("-still-goes-out.md") })
    }

    @Test fun nothingIsRecordedUntilTurnedOn() = runBlocking {
        signIn()
        c.blogs.refresh()
        publish(SAMPLE_BLOG, "Not recorded")
        assertFalse(record.file.exists())
    }
}
