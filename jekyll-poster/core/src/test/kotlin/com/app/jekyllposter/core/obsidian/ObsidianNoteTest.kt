package com.app.jekyllposter.core.obsidian

import com.app.jekyllposter.core.obsidian.ObsidianNote.Embed
import com.app.jekyllposter.core.obsidian.ObsidianNote.LinkTarget
import com.app.jekyllposter.core.obsidian.ObsidianNote.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObsidianNoteTest {
    private val posts = listOf(
        LinkTarget("_posts/2025-04-20-reading-list.md", "Reading list"),
        LinkTarget("_posts/2025-01-12-welcome.md", "Welcome"),
        LinkTarget("travel/_posts/2025-06-01-lighthouse.md", "A walk to the lighthouse"),
        LinkTarget("_posts/2025/2025-03-03-spring.md", "Spring"),
    )

    private fun convert(text: String, fileName: String? = null) = ObsidianNote.convert(text, fileName, posts) as Result.Converted

    private fun problem(text: String) = (ObsidianNote.convert(text, null, posts) as Result.Problem).message

    @Test fun plainMarkdownStaysAsItIsAndTheFileNameIsTheTitle() {
        val note = convert("Some *words*, a [link](https://example.com).\n\n- a list\n", "Bus notes.md")
        assertEquals("Bus notes", note.title)
        assertEquals("Some *words*, a [link](https://example.com).\n\n- a list\n", note.body)
        assertNull(note.extra)
    }

    @Test fun frontMatterFillsTheEditorsFieldsAndObsidiansOwnKeysGo() {
        val note = convert(
            """
            ---
            title: Bus notes
            date: 2025-04-20
            tags: [commute, "#notes"]
            categories: writing
            aliases: [the bus one]
            cssclasses: wide
            image: /assets/bus.jpg
            ---
            # Bus notes

            On the 22.
            """.trimIndent(),
            "Something else.md",
        )
        assertEquals("Bus notes", note.title)
        assertEquals(listOf("commute", "notes"), note.tags)
        assertEquals(listOf("writing"), note.categories)
        assertEquals("image: /assets/bus.jpg", note.extra)
        // The heading repeated the title.
        assertEquals("On the 22.", note.body)
    }

    @Test fun aFirstHeadingIsTheTitleWhenThereIsNoOther() {
        val note = convert("# Bus notes\n\nOn the 22.\n")
        assertEquals("Bus notes", note.title)
        assertEquals("On the 22.\n", note.body)
    }

    @Test fun linksToPostsBecomePostUrlLinks() {
        val note = convert("See [[Reading list]], [[welcome|my first]], [[2025-01-12-welcome]] and [[Spring#Flowers]].")
        assertEquals(
            "See [Reading list]({% post_url 2025-04-20-reading-list %}), [my first]({% post_url 2025-01-12-welcome %}), " +
                "[2025-01-12-welcome]({% post_url 2025-01-12-welcome %}) and [Spring]({% post_url 2025/2025-03-03-spring %}).",
            note.body,
        )
    }

    @Test fun aLinkFindsAPostByItsSlugToo() {
        assertEquals("[lighthouse]({% post_url 2025-06-01-lighthouse %})", convert("[[lighthouse]]").body)
    }

    @Test fun linksToNotesNotYetPostedStayAsWritten() {
        assertEquals("Next: [[Things I want to write]] and [[Later|soon]].", convert("Next: [[Things I want to write]] and [[Later|soon]].").body)
    }

    @Test fun codeIsLeftAlone() {
        val text = "Write `[[Reading list]]` for a link:\n\n```\n[[Reading list]]\n```\n\nLike [[Reading list]]."
        assertEquals(
            "Write `[[Reading list]]` for a link:\n\n```\n[[Reading list]]\n```\n\nLike [Reading list]({% post_url 2025-04-20-reading-list %}).",
            convert(text).body,
        )
    }

    @Test fun findAndReplaceRulesAreAppliedEverywhereThenRemoved() {
        val note = convert(
            """
            ---
            title: Lunch with Priya
            tags: [Priya]
            location: Priya's flat on Elm Street
            find:
              - Priya
              - '(\d+) Elm Street'
            replace:
              - a friend
              - 'Elm Street'
            ---
            Priya made soup at 12 Elm Street. It cost $5.
            """.trimIndent(),
        )
        assertEquals("Lunch with a friend", note.title)
        assertEquals(listOf("a friend"), note.tags)
        assertEquals("location: a friend's flat on Elm Street", note.extra)
        assertEquals("a friend made soup at Elm Street. It cost \$5.\n", note.body)
        val everything = listOf(note.title, note.body, note.extra.orEmpty()) + note.tags
        assertFalse(everything.toString(), everything.any { "Priya" in it || "find" in it || "12" in it })
    }

    @Test fun rulesCanUseGroupsAsPythonWritesThem() {
        val note = convert("---\nfind: ['(?P<first>\\w+) Smith']\nreplace: ['\\g<first> S.']\n---\nAda Smith and Bo Smith\n")
        assertEquals("Ada S. and Bo S.\n", note.body)
        val numbered = convert("---\nfind: ['(\\w+)@example\\.com']\nreplace: ['\\1 (email)']\n---\nWrite to ada@example.com.\n")
        assertEquals("Write to ada (email).\n", numbered.body)
    }

    @Test fun theFileNameTitleIsScrubbedToo() {
        assertEquals("Lunch with a friend", convert("---\nfind: [Priya]\nreplace: [a friend]\n---\nSoup.\n", "Lunch with Priya.md").title)
    }

    @Test fun rulesThatCantBeAppliedStopTheNoteRatherThanLeakIt() {
        assertTrue(problem("---\nfind: [Priya, Elm]\nreplace: [a friend]\n---\nx\n").contains("pairs"))
        assertTrue(problem("---\nfind: ['(unclosed']\nreplace: [x]\n---\nx\n").contains("(unclosed"))
        assertTrue(problem("---\nfind: {a: b}\nreplace: [x]\n---\nx\n").contains("lists"))
        assertTrue(problem("---\nfind: [Priya\nreplace: x\n---\nPriya\n").contains("isn't YAML"))
    }

    @Test fun imageEmbedsAreListedAndOtherEmbedsStay() {
        val note = convert("![[cat.jpg]]\n\n![[attachments/dog.PNG|300]]\n\n![[cat.jpg|A cat asleep]]\n\n![[Another note]]\n")
        assertEquals(
            listOf(Embed("![[cat.jpg]]", "cat.jpg", ""), Embed("![[attachments/dog.PNG|300]]", "attachments/dog.PNG", ""), Embed("![[cat.jpg|A cat asleep]]", "cat.jpg", "A cat asleep")),
            note.embeds,
        )
        assertTrue(note.body.contains("![[Another note]]"))
    }

    @Test fun anEmbedResolvesAsObsidianDoes() {
        val files = listOf("Notes/cat.jpg", "cat.jpg", "attachments/dog.png", "Trips/attachments/dog.png", "Trips/Fish.JPG")
        assertEquals("cat.jpg", ObsidianNote.resolve("cat.jpg", files))
        assertEquals("Trips/attachments/dog.png", ObsidianNote.resolve("Trips/attachments/dog.png", files))
        assertEquals("attachments/dog.png", ObsidianNote.resolve("dog.png", files))
        assertEquals("Trips/Fish.JPG", ObsidianNote.resolve("fish.jpg", files))
        assertNull(ObsidianNote.resolve("bird.jpg", files))
    }

    @Test fun anEmbedIsReplacedWhereItWas() {
        assertEquals("a\n\n![x](y)\n\nb ![[cat.jpg]]", ObsidianNote.replaceEmbed("a\n\n![[cat.jpg]]\n\nb ![[cat.jpg]]", "![[cat.jpg]]", "![x](y)"))
    }
}
