package com.app.carry

import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The post goes to strangers who will turn up expecting what it says, and
 * it leaves the phone for good. Three properties matter more than the
 * formatting, and the first version of this file tested none of them: it
 * asserted the output did not contain a weight that the code never read,
 * which would have stayed green while the post started publishing notes.
 *
 * So these drive the real channels — `line`, `suggested`, `note`,
 * `classTag`, `date`, and the added rows — with hostile content in each.
 */
class IntelPostTest {

    private fun set(
        ref: Int, canonical: String?, line: String = "10 x whatever",
        prescribed: Int? = 10, components: List<String>? = null, suggested: String? = null,
        // What the board said, which is what gets published. It is a
        // separate argument from `prescribed` on purpose: the two differ
        // for most real rows, and a test that set only one could not tell
        // which of them the post had used.
        prescribedText: String? = "10", durationSec: Int? = null,
    ) = ExerciseSet(
        ref = ref, line = line, canonical = canonical, components = components,
        variant = JsonNull, prescribedReps = prescribed, suggested = suggested,
        prescribedText = prescribedText, prescribedDurationSec = durationSec,
    )

    private fun session(
        borrowed: String? = null, classTag: String? = "2G", date: String = "2026-10-17",
        sets: List<ExerciseSet> = listOf(set(0, "bench press"), set(1, "deadlift")),
    ) = Session(sheetId = "$date:$classTag", date = date, classTag = classTag,
        borrowedFrom = borrowed, sets = sets)

    private fun did(vararg refs: Int) = refs.associateWith { SessionPatch.Entry(weight = 45.0) }

    // ---------------------------------------------------------------- what it claims

    @Test
    fun `a borrowed sheet never publishes the other class's block as this one's`() {
        // The whole point of the project is a history polluted by borrowed
        // blocks. Printing 3G's prescription under a 2G heading would feed
        // that back to the subreddit the coach scrapes, and the pollution
        // becomes self-sustaining and public.
        val post = IntelPost.compose(session(borrowed = "3G"), did(0, 1), emptyList())!!.text
        assertFalse("no rep counts: this sheet's are 3G's", post.contains("10 x"))
        assertTrue(post, post.contains("bench press"))
        assertTrue("must say what it is", post.contains("Reported after the fact"))
        assertFalse("and must not call itself the board", post.contains("floor block"))
    }

    @Test
    fun `a sheet with real intel does publish the prescription`() {
        val post = IntelPost.compose(session(), did(0, 1), emptyList())!!.text
        assertTrue(post, post.contains("10 x bench press"))
        assertTrue(post, post.startsWith("2G floor block"))
        assertFalse(post, post.contains("Reported after the fact"))
    }

    @Test
    fun `only the rows that say the movement was in the block`() {
        val post = IntelPost.compose(
            session(sets = listOf(set(0, "bench press"), set(1, "deadlift"), set(2, "squat"),
                                 set(3, "row"),
                                 set(4, "plank", durationSec = 45, prescribed = null, prescribedText = null))),
            mapOf(
                0 to SessionPatch.Entry(weight = 45.0),        // did it
                1 to SessionPatch.Entry(skipped = true),       // explicitly did not
                2 to SessionPatch.Entry(),                     // blank
                // Tapped while deciding. Not a record that it was in the
                // block -- the first version counted this, while claiming
                // to use the same test the history uses.
                3 to SessionPatch.Entry(hurt = true),
                // "I did it, and there is nothing to say about it", which
                // is the only thing a timed hold can say: it takes no
                // weight and prescribes no reps, so the boxes are empty by
                // definition. The owner's call, 2026-10-16. The row is
                // TIMED because that is the only shape the chip is drawn
                // for -- see the file-sourced test below.
                4 to SessionPatch.Entry(done = true),
            ),
            emptyList(),
        )!!.text
        assertTrue(post, post.contains("bench press"))
        assertTrue("done says it was in the block: $post", post.contains("plank"))
        listOf("deadlift", "squat", "row").forEach {
            assertFalse("$it is not evidence: $post", post.contains(it))
        }
    }

    @Test
    fun `a class that was wholly timed still has a post`() {
        // Before the owner answered this, a timed class offered no post at
        // all: every row was empty by definition, so `did` came back empty
        // and compose returned null. That is a whole class the subreddit
        // never heard about.
        //
        // Deliberately NOT named "bodyweight": an untimed bodyweight row
        // has no `done` chip, so it still cannot be posted. Widening the
        // chip is the owner's call and is on the backlog.
        val post = IntelPost.compose(
            session(sets = listOf(set(0, "plank", durationSec = 45, prescribed = null, prescribedText = null),
                                 set(1, "side plank", durationSec = 30, prescribed = null, prescribedText = null))),
            mapOf(0 to SessionPatch.Entry(done = true), 1 to SessionPatch.Entry(done = true)),
            emptyList(),
        )
        assertTrue("a wholly bodyweight class should still post", post != null)
        assertTrue(post!!.text, post.text.contains("plank"))
        assertTrue(post.text, post.text.contains("side plank"))
    }

    @Test
    fun `nothing recorded means nothing to post`() {
        assertNull(IntelPost.compose(session(), emptyMap(), emptyList()))
        assertNull(IntelPost.compose(session(), mapOf(0 to SessionPatch.Entry()), emptyList()))
        assertNull(IntelPost.compose(session(), emptyMap(), listOf(AddedSet(name = ""))))
    }

    // ---------------------------------------------------------------- what it leaks

    @Test
    fun `a done flag on a row with no done chip cannot reach the post`() {
        // `done` is seeded from the raw file as well as from the chip, and
        // the chip is drawn only for a timed row. So a runner re-run that
        // drops `prescribed_duration_sec` leaves the flag on a row whose
        // chip has vanished: invisible in the form, and one Save tap is
        // the whole condition for offering a post. Worse than a bare
        // bullet -- `prescription` falls back to the rep text, so it would
        // read "10 x plank", a prescription nobody answered this sitting.
        val post = IntelPost.compose(
            session(sets = listOf(set(0, "plank"))),   // no durationSec: not timed
            mapOf(0 to SessionPatch.Entry(done = true)),
            emptyList(),
        )
        assertNull("a row the owner cannot see or clear must not publish", post)
    }

    @Test
    fun `a weight the runner wrote into free text cannot reach the post`() {
        // `suggested` is "try 45 lb (up from 40, held 3x)" -- in the same
        // schema, written by the same script. Building the post from
        // `line` meant one script revision away from publishing that, with
        // no change here and no test failing. So the post is built from
        // the fields this app owns.
        val leaky = set(
            0, canonical = "bench press",
            line = "10 x bench press — try 47.5 lb (up from 45, held 3x)",
            suggested = "try 47.5 lb (up from 45, held 3x)",
        )
        val post = IntelPost.compose(session(sets = listOf(leaky)), did(0), emptyList())!!.text
        assertFalse(post, post.contains("47.5"))
        assertFalse(post, post.contains("up from"))
        assertTrue(post, post.contains("bench press"))
    }

    @Test
    fun `a row this app cannot name itself is left out, not guessed from the sheet line`() {
        val unnamed = set(0, canonical = null, line = "10 x something the matcher missed")
        assertNull(IntelPost.compose(session(sets = listOf(unnamed)), did(0), emptyList()))
    }

    @Test
    fun `an added row publishes its name and never the reps the owner managed`() {
        // An added row has no prescription: `reps` is what they did. "8 x
        // deadlift" would leak a personal number AND invent a block.
        val post = IntelPost.compose(
            session(), emptyMap(), listOf(AddedSet(name = "ybell deadlift", weight = 26.0, reps = 8)),
        )!!.text
        assertTrue(post, post.contains("ybell deadlift"))
        assertFalse("performed reps are not the block", post.contains("8 x"))
        assertFalse(post, post.contains("26"))
    }

    @Test
    fun `a note the owner typed stays on the phone`() {
        val post = IntelPost.compose(
            session(), mapOf(0 to SessionPatch.Entry(weight = 45.0, note = "shoulder felt wrong again")),
            emptyList(),
        )!!.text
        assertFalse(post, post.contains("shoulder"))
    }

    // ---------------------------------------------------------------- hostile input

    @Test
    fun `a newline cannot add a movement the class never did`() {
        // One bullet per movement is the format, so a name holding a
        // newline writes its own bullets. Folding the break to a space
        // stopped that and still published the rest of the string, so
        // the name is cut at the break instead.
        val post = IntelPost.compose(
            session(sets = listOf(set(0, "squat\n- bench press\n- my 1RM is 185"))),
            did(0),
            listOf(AddedSet(name = "plank\n- burpees")),
        )!!.text
        assertEquals(2, post.lines().count { it.startsWith("- ") })
        assertFalse(post, post.contains("1RM"))
    }

    @Test
    fun `a name cannot reverse the line it sits on, anywhere it appears`() {
        val post = IntelPost.compose(
            session(classTag = "2G‮", sets = listOf(set(0, "deadlift‮ drop"))),
            did(0), listOf(AddedSet(name = "row‮ x")),
        )!!.text
        assertFalse(post, post.contains('‮'))
    }

    @Test
    fun `a huge field cannot make a huge post`() {
        val post = IntelPost.compose(
            session(classTag = "x".repeat(50_000), date = "y".repeat(50_000),
                sets = (0..500).map { set(it, "z".repeat(5_000)) }),
            (0..500).associateWith { SessionPatch.Entry(weight = 45.0) },
            emptyList(),
        )!!.text
        assertTrue("${post.length} chars", post.length < 4_000)
    }

    @Test
    fun `the added rows are never the ones the cap drops`() {
        // On a borrowed day they are the session, so appending them last
        // meant a long sheet silently discarded exactly the rows that
        // mattered most.
        val many = (0..100).map { set(it, "move $it") }
        val post = IntelPost.compose(
            session(sets = many), (0..100).associateWith { SessionPatch.Entry(weight = 45.0) },
            listOf(AddedSet(name = "ybell deadlift")),
        )!!.text
        assertTrue(post, post.contains("ybell deadlift"))
    }

    @Test
    fun `the lines a prescribing post makes are the shape the coach's parser reads`() {
        // `^\d+\s*(?:total\s*)?x\s+(.*)$` in gym_lib. A post it cannot
        // parse helps the later classes and nobody else.
        val prescribed = Regex("""^\d+\s*(?:total\s*)?x\s+(.*)$""")
        val post = IntelPost.compose(session(), did(0, 1), emptyList())!!.text
        val lines = post.lines().filter { it.startsWith("- ") }.map { it.removePrefix("- ") }
        assertEquals(2, lines.size)
        lines.forEach { assertTrue("not parseable: $it", prescribed.matches(it)) }
    }

    @Test
    fun `what gets published is what the board said, not the number the math used`() {
        // `prescribed_reps` is the runner's working number: an alternating
        // "10 total" is 5 a side, and a "6-12" range is its floor. They
        // differ for most real rows, so publishing it would tell the
        // subreddit to do half the reps. The file carries the board's own
        // wording alongside, and that is the only one that leaves here.
        val post = IntelPost.compose(
            session(sets = listOf(
                set(0, "bench press", prescribed = 6, prescribedText = "6-12"),
                set(1, "deadlift", prescribed = 5, prescribedText = "10 total"),
            )),
            did(0, 1), emptyList(),
        )!!.text
        assertTrue(post, post.contains("- 6-12 x bench press"))
        assertTrue(post, post.contains("- 10 total x deadlift"))
        assertFalse("the math number must not be the one published", post.contains("- 6 x bench press"))
        assertFalse(post, post.contains("- 5 x deadlift"))
    }

    @Test
    fun `a timed set publishes its duration, not an x`() {
        val post = IntelPost.compose(
            session(sets = listOf(set(0, "low plank", prescribed = null,
                                      prescribedText = null, durationSec = 45))),
            did(0), emptyList(),
        )!!.text
        assertTrue(post, post.contains("- 45 sec of low plank"))
    }

    @Test
    fun `a row the file gives no prescription for publishes the movement alone`() {
        // Better a movement with no number than a number nobody asked for.
        val post = IntelPost.compose(
            session(sets = listOf(set(0, "bench press", prescribed = 8, prescribedText = null))),
            did(0), emptyList(),
        )!!.text
        assertTrue(post, post.contains("- bench press"))
        assertFalse("not even from prescribed_reps", post.contains("8"))
    }

    // ---------------------------------------------- what the file may not dictate

    @Test
    fun `a duration the file cannot mean is refused, not rendered`() {
        // `prescribed_duration_sec` arrives from a file written on another
        // machine. An Int 0 is non-null, so it used to win over a perfectly
        // good `prescribed_text` and publish "0 sec of squat" -- and a
        // negative went straight through "seconds >= 60" into "-45 sec of".
        for (bad in listOf(0, -45, Int.MAX_VALUE)) {
            val set = set(0, "squat", prescribedText = "10 total", durationSec = bad)
            val post = IntelPost.compose(session(sets = listOf(set)), did(0), emptyList())!!.text
            assertTrue("duration $bad", post.contains("- 10 total x squat"))
            assertFalse("duration $bad leaked", post.contains("sec of"))
        }
        // And a real one still works.
        val ok = set(0, "squat", prescribedText = null, durationSec = 45)
        assertTrue(IntelPost.compose(session(sets = listOf(ok)), did(0), emptyList())!!
            .text.contains("- 45 sec of squat"))
    }

    @Test
    fun `a prescription that is not a rep scheme is refused`() {
        // `prescribed_text` comes from the same file, and the same writer,
        // as `suggested` -- which holds "try 45 lb (up from 40, held 3x)".
        // It is both shown and published, so it is exactly where a runner
        // revision could put a weight into a post captioned "never your
        // weights". Anything that is not digits, separators and
        // total/each is dropped rather than trusted.
        for (bad in listOf("10 (45 lb)", "try 45 lb", "10\n- my 1RM is 185", "8 x 45 lb", "")) {
            val set = set(0, "squat", prescribedText = bad)
            val post = IntelPost.compose(session(sets = listOf(set)), did(0), emptyList())!!.text
            assertTrue("$bad -> $post", post.contains("- squat"))
            assertFalse("$bad leaked into $post", post.contains("45"))
            assertFalse("$bad leaked into $post", post.contains("185"))
        }
        // The real schemes all survive.
        for (good in listOf("8", "10 total", "6-12", "6-10 each", "1/2/3/4/5")) {
            val set = set(0, "squat", prescribedText = good)
            assertTrue(good, IntelPost.compose(session(sets = listOf(set)), did(0), emptyList())!!
                .text.contains("- $good x squat"))
        }
    }

    private fun bullets(post: IntelPost.Post) = post.text.lines().filter { it.startsWith("- ") }

    @Test
    fun `a line typed by hand that the sheet already has is posted once, and not marked`() {
        // The owner's answer, 2026-10-16: "show it once". Both render
        // "10 total x squat". The sheet's copy is the one kept, so the
        // digits -- the board's own prescription -- are not flagged as a
        // weight the owner typed.
        val post = IntelPost.compose(
            session(sets = listOf(set(0, "squat", prescribedText = "10 total"))),
            did(0), listOf(AddedSet(id = "a", name = "10 total x squat")),
        )!!
        assertEquals(listOf("- 10 total x squat"), bullets(post))
        assertEquals(emptySet<String>(), post.suspect)
    }

    @Test
    fun `a repeat that differs only in capitals or spacing is still the same line`() {
        // A phone keyboard capitalises the first letter of a typed name,
        // and a thumb doubles a space; neither makes it another movement.
        // On a borrowed day the sheet carries no rep counts, so the typed
        // name meets the bare movement.
        val post = IntelPost.compose(
            session(borrowed = "3G", sets = listOf(set(0, "goblet squat"))),
            did(0), listOf(AddedSet(id = "a", name = "Goblet  squat")),
        )!!
        assertEquals(listOf("- goblet squat"), bullets(post))
    }

    @Test
    fun `an accent typed as two characters is still the same line`() {
        // "plié" from the sheet is one code point for the é; a keyboard
        // can send e + a combining accent. Same text, so one bullet.
        val post = IntelPost.compose(
            session(borrowed = "3G", sets = listOf(set(0, "pli\u00e9 squat"))),
            did(0), listOf(AddedSet(id = "a", name = "plie\u0301 squat")),
        )!!
        assertEquals(listOf("- pli\u00e9 squat"), bullets(post))
    }

    @Test
    fun `two different movements are never merged into one bullet`() {
        // The dedupe must only ever remove a line that says exactly what
        // another one says. A different rep scheme, a different spelling
        // of the movement, or the bare name beside a prescribed one are
        // all things the owner wrote on purpose.
        val post = IntelPost.compose(
            session(sets = listOf(set(0, "squat", prescribedText = "10"), set(1, "push up"))),
            did(0, 1),
            listOf(AddedSet(id = "a", name = "12 x squat"), AddedSet(id = "b", name = "pushup"),
                   AddedSet(id = "c", name = "squat")),
        )!!
        assertEquals(
            listOf("- 12 x squat", "- pushup", "- squat", "- 10 x squat", "- 10 x push up"),
            bullets(post),
        )
    }

    // ------------------------------------------------- what the preview calls out

    @Test
    fun `a typed name that carries a number is flagged for a second look`() {
        // The screen promises "the movements only, never your weights",
        // and an added row's name is typed by hand, so that promise is not
        // one this app can keep alone. Stripping trailing digits would
        // mangle "bicep curl 21s", a real movement -- so the preview says
        // which lines to look at, where the owner is already deciding.
        val post = IntelPost.compose(
            session(), did(0),
            listOf(AddedSet(id = "a", name = "ybell deadlift 26"),
                   AddedSet(id = "b", name = "pop squat")),
        )!!
        assertEquals(setOf("- ybell deadlift 26"), post.suspect)
        assertTrue(post.text, post.text.contains("- pop squat"))
    }

    @Test
    fun `a movement the runner named is never flagged, however many digits it has`() {
        // "bicep curl 21s", "90/90 hip stretch", "3 way shoulder" are real
        // movements. Flagging one lights the warning on a line the owner
        // never typed and cannot fix -- on every intel day, until they
        // learn to ignore it.
        val post = IntelPost.compose(
            session(sets = listOf(set(0, "bicep curl 21s"), set(1, "90/90 hip stretch"))),
            did(0, 1), emptyList(),
        )!!
        assertEquals(emptySet<String>(), post.suspect)
    }

    @Test
    fun `the prescription's own digits are not what gets flagged`() {
        // "10 x bench press" is digits by design. Testing the whole line
        // would mark every row on an intel day and mean nothing.
        val post = IntelPost.compose(session(), did(0, 1), emptyList())!!
        assertTrue(post.text, post.text.contains("- 10 x bench press"))
        assertEquals(emptySet<String>(), post.suspect)
    }
}
