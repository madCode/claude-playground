package com.app.jekyllposter.publish

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.BuildState
import com.app.jekyllposter.data.Destination
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.DraftImage
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.testutil.TestApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class PublisherTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container
    private val github = app.github
    private val evening = ZonedDateTime.of(2026, 10, 4, 22, 15, 0, 0, ZoneId.of("America/Los_Angeles"))
    private val publisher = Publisher(c.drafts, c.accounts, c.blogs, c.settings) { evening }

    @Before fun signIn() = runBlocking {
        c.accounts.save(Account("sample", "good-token", "sample", "sample-blog", "main"))
    }

    @After fun close() = github.close()

    private fun queue(draft: Draft): Long = runBlocking { c.drafts.insert(draft.copy(state = PostState.Queued)) }

    @Test fun aNewPostLandsWithItsDateOffsetAndCategories() = runBlocking {
        val id = queue(Draft(title = "Late night: notes", body = "Hello from the phone.", categories = listOf("writing", "late"), tags = listOf("phone")))
        assertEquals(Publisher.Outcome.Done, publisher.publish(id))
        assertEquals(
            "---\ntitle: \"Late night: notes\"\ndate: 2026-10-04 22:15:00 -0700\ncategories: [writing, late]\ntags: [phone]\n---\n\nHello from the phone.\n",
            github.text("_posts/2026-10-04-late-night-notes.md"),
        )
        val draft = c.drafts.get(id)!!
        assertEquals(PostState.Published, draft.state)
        assertEquals(github.head, draft.commitSha)
        assertEquals(BuildState.Building, draft.buildState)
        assertEquals("Add post: Late night: notes", github.commits.getValue(github.head).message)
        // The blog is read again, so the post shows under "On your blog".
        assertTrue(c.database.posts().snapshot().any { it.path == "_posts/2026-10-04-late-night-notes.md" })
    }

    @Test fun aPostWrittenAbroadIsDatedInTheSitesTimeZoneNotThePhones() = runBlocking {
        // Tokyo, Monday morning: Sunday evening at the site, in Los Angeles.
        val tokyo = evening.withZoneSameInstant(ZoneId.of("Asia/Tokyo"))
        val abroad = Publisher(c.drafts, c.accounts, c.blogs, c.settings) { tokyo }
        val id = queue(Draft(title = "From abroad", body = "Hello."))
        assertEquals(Publisher.Outcome.Done, abroad.publish(id))
        val text = github.text("_posts/2026-10-04-from-abroad.md")!!
        assertTrue(text, text.contains("date: 2026-10-04 22:15:00 -0700"))
    }

    @Test fun commitsNameNoAuthorUnlessTheWriterAsksForTheNoReplyAddress() = runBlocking {
        // GitHub's default: the account, with whatever email its settings give.
        publisher.publish(queue(Draft(title = "Default", body = "x")))
        assertNull(github.commits.getValue(github.head).authorEmail)
        c.settings.setCommitAsNoReply("sample", com.app.jekyllposter.core.github.CommitAuthor("Sample Writer", "1001+sample@users.noreply.github.com"))
        publisher.publish(queue(Draft(title = "Private", body = "x")))
        assertEquals("1001+sample@users.noreply.github.com", github.commits.getValue(github.head).authorEmail)
    }

    @Test fun signedInAsSomeoneElseTheirOwnNoReplyAddressIsLookedUp() = runBlocking {
        // Kept from another account; this one's is found once and kept instead.
        c.settings.setCommitAsNoReply("someone-else", com.app.jekyllposter.core.github.CommitAuthor("Them", "7+someone-else@users.noreply.github.com"))
        publisher.publish(queue(Draft(title = "Mine", body = "x")))
        assertEquals("1001+sample@users.noreply.github.com", github.commits.getValue(github.head).authorEmail)
    }

    @Test fun photosTheTextStillUsesGoInThePostsCommit() = runBlocking {
        val kept = java.io.File.createTempFile("kept", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val dropped = java.io.File.createTempFile("dropped", ".jpg").apply { writeBytes(byteArrayOf(9)) }
        val id = queue(
            Draft(
                title = "Lighthouse", body = "Look:\n\n![]({{ '/assets/images/2026/a.jpg' | relative_url }})\n",
                images = listOf(DraftImage("/assets/images/2026/a.jpg", kept.path), DraftImage("/assets/images/2026/b.jpg", dropped.path)),
            ),
        )
        assertEquals(Publisher.Outcome.Done, publisher.publish(id))
        val files = github.files()
        // Named for the post, not for when it was added; the text links the new name.
        assertTrue(files.getValue("assets/images/2026/lighthouse.jpg").contentEquals(byteArrayOf(1, 2, 3)))
        assertTrue(github.text("_posts/2026-10-04-lighthouse.md")!!.contains("'/assets/images/2026/lighthouse.jpg'"))
        assertTrue(files.keys.none { it.startsWith("assets/images/2026/") && it != "assets/images/2026/lighthouse.jpg" })
        // One commit for the post and its photo.
        assertEquals(github.commits.getValue(github.head).parent, github.commits.values.first { it.message == "Initial commit" }.sha)
    }

    @Test fun aPhotoMissingFromThePhoneStopsThePostRatherThanBreakingIt() = runBlocking {
        val id = queue(Draft(title = "T", body = "![]({{ '/assets/images/x.jpg' | relative_url }})", images = listOf(DraftImage("/assets/images/x.jpg", "/nope/x.jpg"))))
        assertTrue(publisher.publish(id) is Publisher.Outcome.Failed)
        assertTrue(github.files().keys.none { it.endsWith("-t.md") })
    }

    @Test fun aRetriedEditThatHadLandedIsDoneNotAConflict() = runBlocking {
        c.blogs.refresh()
        val path = "_posts/2025-01-12-welcome.md"
        val sha = github.files().let { c.database.posts().snapshot().first { it.path == path }.sha }
        val id = queue(Draft(title = "Welcome", body = "New words.", editingPath = path, baseSha = sha))
        publisher.publish(id)
        val head = github.head
        c.drafts.update(c.drafts.get(id)!!.copy(state = PostState.Queued))
        assertEquals(Publisher.Outcome.Done, publisher.publish(id))
        assertEquals(PostState.Published, c.drafts.get(id)!!.state)
        assertEquals(head, github.head)
    }

    @Test fun twoPostsWithTheSameTitleBothLand() = runBlocking {
        val a = queue(Draft(title = "Weekly notes", body = "One"))
        val b = queue(Draft(title = "Weekly notes", body = "Two"))
        kotlinx.coroutines.coroutineScope {
            launch(kotlinx.coroutines.Dispatchers.IO) { publisher.publish(a) }
            launch(kotlinx.coroutines.Dispatchers.IO) { publisher.publish(b) }
        }
        assertTrue(github.text("_posts/2026-10-04-weekly-notes.md")!!.contains("One") xor github.text("_posts/2026-10-04-weekly-notes.md")!!.contains("Two"))
        assertTrue(github.text("_posts/2026-10-04-weekly-notes-2.md") != null)
    }

    @Test fun aPostWrittenForAnotherBlogWaitsForIt() = runBlocking {
        val id = queue(Draft(blog = "sample/other-blog@main", title = "Elsewhere", body = "x"))
        assertTrue(publisher.publish(id) is Publisher.Outcome.Failed)
        assertTrue(c.drafts.get(id)!!.error!!.contains("sample/other-blog"))
        assertTrue(github.files().keys.none { it.contains("elsewhere") })
    }

    @Test fun aPublishedPostKnowsItsAddress() = runBlocking {
        val id = queue(Draft(title = "Bus notes", body = "x", categories = listOf("Writing")))
        publisher.publish(id)
        // The sample blog's permalink is /:categories/:year/:month/:day/:title/ in Los Angeles time.
        assertEquals("https://sample.github.io/sample-blog/writing/2026/10/04/bus-notes/", c.drafts.get(id)!!.postUrl)
    }

    @Test fun savingToTheBlogsDraftsWritesAnUndatedJekyllDraft() = runBlocking {
        val id = queue(Draft(title = "Half an idea", body = "To finish on the laptop.", destination = Destination.Drafts))
        assertEquals(Publisher.Outcome.Done, publisher.publish(id))
        assertEquals("---\ntitle: Half an idea\n---\n\nTo finish on the laptop.\n", github.text("_drafts/half-an-idea.md"))
        assertEquals("Add draft: Half an idea", github.commits.getValue(github.head).message)
        val draft = c.drafts.get(id)!!
        assertEquals(null, draft.buildState)
        assertEquals(null, draft.postUrl)
    }

    @Test fun publishingAJekyllDraftMovesItToPostsInOneCommit() = runBlocking {
        c.blogs.refresh()
        val path = "_drafts/garden-plans.md"
        val sha = c.database.posts().snapshot().first { it.path == path }.sha
        val id = queue(
            Draft(
                title = "Garden plans", body = "Tomatoes along the fence, beans by the shed.", categories = listOf("garden"),
                editingPath = path, baseSha = sha, destination = Destination.Posts,
            ),
        )
        val before = github.head
        assertEquals(Publisher.Outcome.Done, publisher.publish(id))
        assertEquals(null, github.text(path))
        assertEquals(
            "---\ntitle: Garden plans\ncategories: [garden]\ndate: 2026-10-04 22:15:00 -0700\n---\n\nTomatoes along the fence, beans by the shed.\n",
            github.text("_posts/2026-10-04-garden-plans.md"),
        )
        assertEquals(before, github.commits.getValue(github.head).parent)
        assertEquals("Publish draft: Garden plans", github.commits.getValue(github.head).message)
        // As if the app died before recording it: the retry finds the move done.
        c.drafts.update(c.drafts.get(id)!!.copy(state = PostState.Queued))
        assertEquals(Publisher.Outcome.Done, publisher.publish(id))
        assertEquals(PostState.Published, c.drafts.get(id)!!.state)
    }

    private fun queueDelete(path: String): Long = runBlocking {
        c.blogs.refresh()
        val sha = c.database.posts().snapshot().first { it.path == path }.sha
        queue(Draft(title = "To go", body = "Edited, then deleted.", editingPath = path, baseSha = sha, destination = Destination.Delete))
    }

    @Test fun deletingAPostRemovesItInOneCommitAndForgetsIt() = runBlocking {
        val path = "_posts/2025-01-12-welcome.md"
        val photo = java.io.File.createTempFile("waiting", ".jpg").apply { writeBytes(byteArrayOf(1)) }
        val id = queueDelete(path)
        c.drafts.update(c.drafts.get(id)!!.copy(images = listOf(DraftImage("/assets/images/2026/x.jpg", photo.path))))
        val before = github.head
        assertEquals(Publisher.Outcome.Done, publisher.publish(id))
        assertNull(github.text(path))
        assertEquals(before, github.commits.getValue(github.head).parent)
        assertEquals("Delete post: To go", github.commits.getValue(github.head).message)
        // Nothing is left to show on the phone: the row, the photo it never sent, the cached post.
        assertNull(c.drafts.get(id))
        assertTrue(!photo.exists())
        assertTrue(c.database.posts().snapshot().none { it.path == path })
    }

    @Test fun aDeleteThatLandedUnheardIsDone() = runBlocking {
        val path = "_drafts/garden-plans.md"
        val id = queueDelete(path)
        // The commit lands, but its answer is lost on the way back.
        github.loseNextRefAnswer = true
        assertEquals(Publisher.Outcome.Retry, publisher.publish(id))
        assertNull(github.text(path))
        val before = github.head
        assertEquals(Publisher.Outcome.Done, publisher.publish(id))
        assertEquals(before, github.head)
        assertNull(c.drafts.get(id))
    }

    @Test fun aPostMovedElsewhereIsNotTakenAsDeleted() = runBlocking {
        val path = "_drafts/garden-plans.md"
        val id = queueDelete(path)
        // Published from the laptop meanwhile: the draft is gone from _drafts but live on the site.
        github.push("Laptop publish", mapOf(path to null, "_posts/2026-10-04-garden-plans.md" to "---\ntitle: Garden plans\n---\n"))
        assertTrue(publisher.publish(id) is Publisher.Outcome.Failed)
        assertEquals(PostState.Failed, c.drafts.get(id)!!.state)
    }

    @Test fun deletingAPostForgetsItsEarlierPublishedUpdates() = runBlocking {
        val path = "_posts/2025-01-12-welcome.md"
        val blog = c.accounts.current()!!.blogKey
        // An update of it, and the phone's record of first publishing it, both still watching the build.
        val updated = c.drafts.insert(Draft(title = "Welcome", editingPath = path, targetPath = path, blog = blog, state = PostState.Published, buildState = BuildState.Building))
        val written = c.drafts.insert(Draft(title = "Welcome", targetPath = path, blog = blog, state = PostState.Published, buildState = BuildState.Building))
        // A Jekyll draft of the same name, published from the phone, now lives elsewhere.
        val other = c.drafts.insert(Draft(title = "Elsewhere", editingPath = path, targetPath = "_posts/2026-10-04-welcome.md", blog = blog, state = PostState.Published))
        val id = queueDelete(path)
        c.drafts.update(c.drafts.get(id)!!.copy(blog = blog))
        assertEquals(Publisher.Outcome.Done, publisher.publish(id))
        assertNull(c.drafts.get(updated))
        assertNull(c.drafts.get(written))
        assertTrue(c.drafts.get(other) != null)
    }

    @Test fun aPostChangedOnGitHubIsNotDeleted() = runBlocking {
        val path = "_drafts/garden-plans.md"
        val id = queueDelete(path)
        github.push("Laptop", mapOf(path to "---\ntitle: Garden plans\n---\nRewritten on the laptop.\n"))
        assertTrue(publisher.publish(id) is Publisher.Outcome.Failed)
        assertTrue(github.text(path)!!.contains("Rewritten on the laptop."))
        assertEquals(PostState.Failed, c.drafts.get(id)!!.state)
    }

    @Test fun updatingAJekyllDraftKeepsItADraft() = runBlocking {
        c.blogs.refresh()
        val path = "_drafts/garden-plans.md"
        val sha = c.database.posts().snapshot().first { it.path == path }.sha
        val id = queue(Draft(title = "Garden plans", body = "More.", categories = listOf("garden"), editingPath = path, baseSha = sha, destination = Destination.Drafts))
        publisher.publish(id)
        assertEquals("---\ntitle: Garden plans\ncategories: [garden]\n---\n\nMore.\n", github.text(path))
        assertEquals("Update draft: Garden plans", github.commits.getValue(github.head).message)
    }

    @Test fun aPostThatLandedUnheardThenFailedIsNotPublishedTwice() = runBlocking {
        val id = queue(Draft(title = "Once only", body = "First version."))
        publisher.publish(id)
        // As if the response to the commit was lost and a later attempt failed on sign-in: the
        // writer then edits and publishes again.
        c.drafts.update(c.drafts.get(id)!!.copy(state = PostState.Queued, body = "Second version."))
        assertEquals(Publisher.Outcome.Done, publisher.publish(id))
        val files = github.files().keys.filter { it.contains("once-only") }
        assertEquals(listOf("_posts/2026-10-04-once-only.md"), files)
        assertTrue(github.text(files.single())!!.contains("Second version."))
        assertEquals("Update post: Once only", github.commits.getValue(github.head).message)
    }

    @Test fun aRetryThatSwitchedToDraftsDoesntUseThePostsName() = runBlocking {
        github.failures["repos/sample/sample-blog/git/refs/heads/main"] = 403
        val id = queue(Draft(title = "Switch", body = "x"))
        assertTrue(publisher.publish(id) is Publisher.Outcome.Failed)
        github.failures.clear()
        c.drafts.update(c.drafts.get(id)!!.copy(state = PostState.Queued, destination = Destination.Drafts))
        publisher.publish(id)
        assertTrue(github.text("_drafts/switch.md") != null)
        assertTrue(github.files().keys.none { it.startsWith("_posts/") && it.contains("switch") })
    }

    @Test fun anyTextSentBeforeIsRecognisedNotJustTheLast() = runBlocking {
        val id = queue(Draft(title = "Twice sent", body = "A"))
        publisher.publish(id)
        // B is attempted and lost; then C is sent: the file still holds A, which is this post's.
        val landed = c.drafts.get(id)!!
        c.drafts.update(landed.copy(state = PostState.Queued, body = "B", sentShas = landed.sentShas + "b-sha-never-landed"))
        c.drafts.update(c.drafts.get(id)!!.copy(body = "C"))
        publisher.publish(id)
        assertEquals(listOf("_posts/2026-10-04-twice-sent.md"), github.files().keys.filter { it.contains("twice-sent") })
        assertTrue(github.text("_posts/2026-10-04-twice-sent.md")!!.contains("C"))
    }

    @Test fun twoRenamedPhotosGetTwoNames() = runBlocking {
        val a = java.io.File.createTempFile("photo", ".jpg").apply { writeBytes(byteArrayOf(1)) }
        val b = java.io.File.createTempFile("photo", ".jpg").apply { writeBytes(byteArrayOf(2)) }
        val pa = "/assets/images/2025/loaf.jpg"
        val pb = "/assets/images/2025/lighthouse.jpg"
        val id = queue(Draft(title = "Two", body = "![]({{ '$pa' | relative_url }}) ![]({{ '$pb' | relative_url }})", images = listOf(DraftImage(pa, a.path), DraftImage(pb, b.path))))
        publisher.publish(id)
        val links = Regex("""'(/assets/images/2026/[^']+)'""").findAll(github.text("_posts/2026-10-04-two.md")!!).map { it.groupValues[1] }.toList()
        assertEquals(2, links.distinct().size)
        assertTrue(github.files().getValue(links[0].removePrefix("/")).contentEquals(byteArrayOf(1)))
        assertTrue(github.files().getValue(links[1].removePrefix("/")).contentEquals(byteArrayOf(2)))
    }

    @Test fun aDraftPublishedElsewhereIsNotMistakenForThisPhonesMove() = runBlocking {
        c.blogs.refresh()
        val path = "_drafts/garden-plans.md"
        val sha = c.database.posts().snapshot().first { it.path == path }.sha
        val id = queue(Draft(title = "Garden plans", body = "Phone words", editingPath = path, baseSha = sha, destination = Destination.Posts, targetPath = "_posts/2026-10-04-garden-plans.md", publishDate = "2026-10-04T22:15-07:00"))
        // Published from a laptop that day, under the same name.
        github.push("Laptop publish", mapOf(path to null, "_posts/2026-10-04-garden-plans.md" to "---\ntitle: Garden plans\n---\nLaptop words\n"))
        assertTrue(publisher.publish(id) is Publisher.Outcome.Failed)
        assertTrue(github.text("_posts/2026-10-04-garden-plans.md")!!.contains("Laptop words"))
    }

    @Test fun aNewPostDoesntTakeAnOlderPostsAddress() = runBlocking {
        // Under a date-free permalink, a free file name can still be another post's URL.
        github.push("Permalinks", mapOf("_config.yml" to "permalink: /:title/\n", "_posts/2020-01-01-weekly-notes.md" to "---\ntitle: Weekly notes\n---\n"))
        val id = queue(Draft(title = "Weekly notes", body = "x"))
        publisher.publish(id)
        assertEquals("_posts/2026-10-04-weekly-notes-2.md", c.drafts.get(id)!!.targetPath)
    }

    @Test fun aPhotoWhoseNameWasTakenIsRenamedNotOverwritten() = runBlocking {
        val photo = java.io.File.createTempFile("photo", ".jpg").apply { writeBytes(byteArrayOf(4, 5)) }
        val taken = "/assets/images/2025/loaf.jpg"
        val id = queue(Draft(title = "Bread", body = "![]({{ '$taken' | relative_url }})", images = listOf(DraftImage(taken, photo.path))))
        val before = github.files().getValue("assets/images/2025/loaf.jpg")
        publisher.publish(id)
        assertTrue(github.files().getValue("assets/images/2025/loaf.jpg").contentEquals(before))
        val post = github.text("_posts/2026-10-04-bread.md")!!
        val renamed = Regex("""'(/assets/images/2026/[^']+)'""").find(post)!!.groupValues[1]
        assertTrue(github.files().getValue(renamed.removePrefix("/")).contentEquals(byteArrayOf(4, 5)))
        // Uploaded, so the phone's copy goes.
        assertTrue(!photo.exists())
    }

    @Test fun anEditsAddressUsesTheSitesTimeZoneForADateWithoutOne() = runBlocking {
        c.blogs.refresh()
        val path = "_posts/2025-04-20-reading-list.md"
        github.push("Date", mapOf(path to "---\ntitle: April\ndate: 2025-04-20 00:30:00\ncategory: Writing\n---\n"))
        val sha = c.blogs.blog()!!.file(path)!!.sha
        val id = queue(Draft(title = "April", body = "x", categories = listOf("Writing"), editingPath = path, baseSha = sha))
        publisher.publish(id)
        // 00:30 in Los Angeles, the sample blog's zone, is still the 20th there.
        assertEquals("https://sample.github.io/sample-blog/writing/2025/04/20/reading-list/", c.drafts.get(id)!!.postUrl)
    }

    @Test fun aNewPostsExtraFrontMatterGoesAfterItsOwnKeys() = runBlocking {
        val id = queue(Draft(title = "With a cover", body = "x", extraFrontMatter = "image: /assets/cover.jpg\nexcerpt: Short"))
        publisher.publish(id)
        assertEquals(
            "---\ntitle: With a cover\ndate: 2026-10-04 22:15:00 -0700\nimage: /assets/cover.jpg\nexcerpt: Short\n---\n\nx\n",
            github.text("_posts/2026-10-04-with-a-cover.md"),
        )
    }

    @Test fun aRetryAfterTheCommitLandedDoesNotPostTwice() = runBlocking {
        val id = queue(Draft(title = "Once", body = "Only once."))
        publisher.publish(id)
        val head = github.head
        // As if the app died after the commit but before marking the post published.
        c.drafts.update(c.drafts.get(id)!!.copy(state = PostState.Queued))
        assertEquals(Publisher.Outcome.Done, publisher.publish(id))
        assertEquals(head, github.head)
        assertEquals(1, github.files().keys.count { it.contains("once") })
    }

    @Test fun aTakenNameGetsANumber() = runBlocking {
        github.push("Laptop", mapOf("_posts/2026-10-04-hello.md" to "---\ntitle: Hello\n---\n"))
        val id = queue(Draft(title = "Hello", body = "Again"))
        publisher.publish(id)
        assertEquals("_posts/2026-10-04-hello-2.md", c.drafts.get(id)!!.targetPath)
        assertTrue(github.text("_posts/2026-10-04-hello-2.md")!!.contains("Again"))
    }

    @Test fun anEditKeepsTheKeysTheAppDoesNotTouch() = runBlocking {
        c.blogs.refresh()
        val path = "_posts/2025-04-20-reading-list.md"
        val sha = c.database.posts().snapshot().first { it.path == path }.sha
        val id = queue(Draft(title = "What I read in April", body = "Updated list.", categories = listOf("Writing"), tags = listOf("books", "essays"), editingPath = path, baseSha = sha))
        assertEquals(Publisher.Outcome.Done, publisher.publish(id))
        assertEquals(
            "---\nlayout: post\ntitle: What I read in April\ncategory: Writing\ntags: [books, essays]\n# Kept by hand: the theme reads this for the post card.\nimage: /assets/img/books.png\n---\n\nUpdated list.\n",
            github.text(path),
        )
        assertEquals("Update post: What I read in April", github.commits.getValue(github.head).message)
    }

    @Test fun anEditOfAPostChangedElsewhereIsRefused() = runBlocking {
        c.blogs.refresh()
        val path = "_posts/2025-01-12-welcome.md"
        val sha = c.database.posts().snapshot().first { it.path == path }.sha
        github.push("Laptop edit", mapOf(path to "---\ntitle: Changed on the laptop\n---\n"))
        val id = queue(Draft(title = "Mine", body = "Mine", editingPath = path, baseSha = sha))
        assertTrue(publisher.publish(id) is Publisher.Outcome.Failed)
        assertEquals("---\ntitle: Changed on the laptop\n---\n", github.text(path))
        val draft = c.drafts.get(id)!!
        assertEquals(PostState.Failed, draft.state)
        assertTrue(draft.error!!.contains("changed on GitHub"))
    }

    @Test fun aRevokedTokenFailsWithWhatToDo() = runBlocking {
        github.token = "rotated"
        val id = queue(Draft(title = "T", body = "B"))
        assertTrue(publisher.publish(id) is Publisher.Outcome.Failed)
        assertTrue(c.drafts.get(id)!!.error!!.contains("Sign in again"))
    }

    @Test fun anOutageIsRetriedLaterAndKeepsThePost() = runBlocking {
        github.failures["repos/sample/sample-blog/git/trees/"] = 503
        val id = queue(Draft(title = "T", body = "B"))
        assertEquals(Publisher.Outcome.Retry, publisher.publish(id))
        assertEquals(PostState.Queued, c.drafts.get(id)!!.state)
    }

    @Test fun onlyQueuedPostsAreSent() = runBlocking {
        val id = runBlocking { c.drafts.insert(Draft(title = "Still writing", body = "x")) }
        assertEquals(Publisher.Outcome.Done, publisher.publish(id))
        assertNull(github.files().keys.firstOrNull { it.contains("still-writing") })
    }

    @Test fun theBuildWatcherFollowsThePagesRun() = runBlocking {
        val id = queue(Draft(title = "Watch me", body = "x"))
        publisher.publish(id)
        val sha = c.drafts.get(id)!!.commitSha!!
        assertEquals(false, c.buildWatcher.check(id, giveUp = false))
        github.runs[sha] = "in_progress" to null
        assertEquals(false, c.buildWatcher.check(id, giveUp = false))
        github.runs[sha] = "completed" to "success"
        assertEquals(true, c.buildWatcher.check(id, giveUp = false))
        assertEquals(BuildState.Live, c.drafts.get(id)!!.buildState)
    }

    @Test fun otherWorkflowsOnTheCommitDontDecideWhetherThePostIsLive() = runBlocking {
        val id = queue(Draft(title = "With CI", body = "x"))
        publisher.publish(id)
        val sha = c.drafts.get(id)!!.commitSha!!
        github.otherRuns[sha] = listOf(Triple("CI", "completed", "failure"))
        // Only CI has run so far: not live yet, and not failed.
        assertEquals(false, c.buildWatcher.check(id, giveUp = false))
        github.runs[sha] = "completed" to "success"
        c.buildWatcher.check(id, giveUp = false)
        assertEquals(BuildState.Live, c.drafts.get(id)!!.buildState)
    }

    @Test fun aFailedBuildIsReportedAndSilenceEndsAsUnknown() = runBlocking {
        val a = queue(Draft(title = "Broken", body = "x"))
        publisher.publish(a)
        github.runs[c.drafts.get(a)!!.commitSha!!] = "completed" to "failure"
        c.buildWatcher.check(a, giveUp = false)
        assertEquals(BuildState.Failed, c.drafts.get(a)!!.buildState)

        val b = queue(Draft(title = "Quiet", body = "x"))
        publisher.publish(b)
        assertEquals(true, c.buildWatcher.check(b, giveUp = true))
        assertEquals(BuildState.Unknown, c.drafts.get(b)!!.buildState)
    }
}
