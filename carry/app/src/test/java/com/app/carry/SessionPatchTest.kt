package com.app.carry

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The write path's reason for existing: what the runner wrote comes back
 * out as it went in, except the handful of fields a person can edit — and
 * when the file is not shaped the way this app understands, nothing is
 * written at all.
 */
class SessionPatchTest {

    private val original = """
        {
          "schema": "gym-session/1",
          "sheet_id": "2026-10-17:2G",
          "date": "2026-10-17",
          "class": "2G",
          "coach_note": "a field this app has never heard of",
          "sets": [
            {"ref": 0, "line": "6-12 x bench press", "canonical": "bench press",
             "components": null, "variant": {"stance": "both", "implement": "barbell"},
             "prescribed_reps": 8, "suggested_weight": 45, "family": "push",
             "weight": null, "reps": null, "runner_only": 7},
            {"ref": 1, "line": "10 x deadlift", "canonical": "deadlift",
             "variant": {"implement": "2 DB"}, "prescribed_reps": 10,
             "suggested_weight": 25, "family": "legs", "weight": null, "reps": null}
          ],
          "added": []
        }
    """.trimIndent()

    private fun patched(text: String, edits: Map<Int, SessionPatch.Entry>, added: List<AddedSet> = emptyList()): String {
        val out = SessionPatch.apply(text, edits, added)
        assertTrue("expected a patch, got $out", out is SessionPatch.Outcome.Patched)
        return (out as SessionPatch.Outcome.Patched).text ?: text
    }
    private fun refused(
        text: String,
        edits: Map<Int, SessionPatch.Entry>,
        added: List<AddedSet> = emptyList(),
    ): String {
        val out = SessionPatch.apply(text, edits, added)
        assertTrue("expected a refusal, got $out", out is SessionPatch.Outcome.Refused)
        return (out as SessionPatch.Outcome.Refused).why
    }
    private fun doc(text: String) = Json.parseToJsonElement(text).jsonObject
    private fun set(text: String, i: Int) = doc(text)["sets"]!!.jsonArray[i].jsonObject

    private val filled = mapOf(0 to SessionPatch.Entry(weight = 45.0, reps = 8))

    // ---- what must survive ------------------------------------------------

    @Test
    fun `a field this app has never heard of survives the write`() {
        val out = patched(original, filled)
        assertEquals("7", set(out, 0)["runner_only"].toString())
        assertEquals("\"a field this app has never heard of\"", doc(out)["coach_note"].toString())
    }

    @Test
    fun `identity is copied, never rebuilt`() {
        val out = patched(original, filled)
        for (key in listOf("line", "canonical", "components", "variant", "prescribed_reps")) {
            assertEquals("$key changed", set(original, 0)[key], set(out, 0)[key])
        }
    }

    @Test
    fun `a row the owner did not touch is untouched`() {
        val out = patched(original, filled)
        assertEquals(set(original, 1), set(out, 1))
        assertNull("a blank row must not gain a skip", set(out, 1)["skipped"])
    }

    @Test
    fun `saving a session nobody edited writes nothing at all`() {
        // Not a nicety: a sync watches this folder, and a file that rewrites
        // itself on every save looks like a change that isn't one.
        assertEquals(null, (SessionPatch.apply(original, emptyMap()) as SessionPatch.Outcome.Patched).text)
        assertEquals(null, (SessionPatch.apply(original, mapOf(0 to SessionPatch.Entry())) as SessionPatch.Outcome.Patched).text)
    }

    @Test
    fun `a whole weight stays whole and a half stays half`() {
        assertEquals("45", set(patched(original, filled), 0)["weight"].toString())
        assertEquals("12.5", set(patched(original, mapOf(0 to SessionPatch.Entry(weight = 12.5))), 0)["weight"].toString())
    }

    @Test
    fun `skipping is a deliberate answer and is recorded as one`() {
        val out = patched(original, mapOf(1 to SessionPatch.Entry(skipped = true)))
        assertEquals("true", set(out, 1)["skipped"].toString())
    }

    @Test
    fun `a mistyped number can be unmade`() {
        // The first version filtered blank entries out entirely, so a weight
        // typed by accident could never be cleared -- it just stayed in the
        // file as a confirmed answer.
        val wrong = patched(original, mapOf(0 to SessionPatch.Entry(weight = 95.0, reps = 8)))
        assertEquals("95", set(wrong, 0)["weight"].toString())
        val cleared = patched(wrong, mapOf(0 to SessionPatch.Entry()))
        assertEquals("null", set(cleared, 0)["weight"].toString())
        assertEquals(emptyMap<Int, SessionPatch.Entry>(), SessionPatch.entriesIn(cleared))
    }

    // ---- what must be refused --------------------------------------------

    @Test
    fun `a file whose exercise list is not a list is left alone`() {
        // This used to replace the whole workout with an empty array.
        val odd = original.replace("\"sets\": [", "\"sets\": {\"0\": [")
            .replace("\"added\": []", "\"added\": []}").let { original.replace("\"sets\": [", "\"sets\": \"nope\", \"ignored\": [") }
        assertTrue(refused(odd, filled).isNotEmpty())
    }

    @Test
    fun `two exercises sharing an id are refused, not written to both`() {
        // One weight typed once, appearing against two different exercises
        // in a training history, is exactly the wrong-number failure.
        val dupes = original.replace("\"ref\": 1,", "\"ref\": 0,")
        assertTrue(refused(dupes, filled).contains("same id"))
    }

    @Test
    fun `an edit that cannot be placed stops the whole save`() {
        // The likeliest cause is the file having been replaced by a sync
        // while the owner was filling it in -- so the rows they were
        // looking at are not the rows now on disk.
        assertTrue(refused(original, mapOf(99 to SessionPatch.Entry(weight = 1.0))).contains("couldn't find"))
    }

    @Test
    fun `a set that is not an object is refused rather than crashing`() {
        val odd = original.replace("{\"ref\": 1,", "null, {\"ref\": 1,")
        assertTrue(refused(odd, filled).isNotEmpty())
    }

    @Test
    fun `text that is not JSON is refused rather than crashing`() {
        assertTrue(refused("{ truncated", filled).isNotEmpty())
        assertTrue(refused("", filled).isNotEmpty())
    }

    @Test
    fun `a weight that is not a real number cannot reach the encoder`() {
        val out = patched(original, mapOf(0 to SessionPatch.Entry(weight = Double.NaN, reps = 8)))
        assertEquals("null", set(out, 0)["weight"].toString())
        assertEquals("8", set(out, 0)["reps"].toString())
    }

    // ---- two sittings ------------------------------------------------------

    @Test
    fun `everything the first sitting entered comes back in the second`() {
        val first = patched(original, mapOf(
            0 to SessionPatch.Entry(weight = 45.0, reps = 8, easy = true),
            1 to SessionPatch.Entry(weight = 25.0, reps = 6, hurt = true, note = "knee"),
        ))
        val back = SessionPatch.entriesIn(first)
        assertEquals(setOf(0, 1), back.keys)
        assertEquals(SessionPatch.Entry(weight = 45.0, reps = 8, easy = true), back[0])
        assertEquals(SessionPatch.Entry(weight = 25.0, reps = 6, hurt = true, note = "knee"), back[1])
    }

    @Test
    fun `one unreadable row does not erase every other row's answers`() {
        // It used to: a single guard around the whole loop meant one junk
        // element returned an empty map, and the owner would re-enter from
        // memory -- a wrong number, which is worse than a missing one.
        val first = patched(original, mapOf(0 to SessionPatch.Entry(weight = 45.0, reps = 8)))
        val damaged = first.replace("{\"ref\": 1,", "\"junk\", {\"ref\": 1,")
        assertEquals(setOf(0), SessionPatch.entriesIn(damaged).keys)
    }

    @Test
    fun `added rows are replaced, not appended again on every save`() {
        val row = AddedSet(name = "ybell deadlift to calf raise", weight = 26.0, reps = 12)
        val first = patched(original, emptyMap(), listOf(row))
        assertEquals(1, doc(first)["added"]!!.jsonArray.size)
        // `asRead` is the row as the file holds it and `id` is assigned on
        // the way back out, so neither is equal to itself here; what this
        // asserts is that the owner's own fields round trip.
        assertEquals(listOf(row), SessionPatch.addedIn(first).map { it.copy(asRead = null, id = "") })

        // Re-saving the same list must not duplicate it.
        val second = SessionPatch.apply(first, emptyMap(), SessionPatch.addedIn(first))
        assertEquals(null, (second as SessionPatch.Outcome.Patched).text)

        // And removing one actually removes it.
        val none = patched(first, emptyMap(), emptyList())
        assertEquals(0, doc(none)["added"]!!.jsonArray.size)
    }

    @Test
    fun `an added row with nothing in it is not resurrected as a blank`() {
        val empty = withAdded(
            """{"name": "", "weight": null, "reps": null, "easy": false, "hurt": false, "note": null}"""
        )
        assertEquals(emptyList<AddedSet>(), SessionPatch.addedIn(empty))
        // And it is not written back either, so it cannot accumulate.
        val out = patched(empty, mapOf(0 to SessionPatch.Entry(weight = 45.0)))
        assertEquals(0, doc(out)["added"]!!.jsonArray.size)
    }

    @Test
    fun `a one-space name still refuses, and the row says so`() {
        // `blank` is isBlank, so " " refuses -- and the placeholder used
        // isEmpty, so neither hint showed and every row looked named.
        val why = refused(original, emptyMap(), listOf(AddedSet(name = " ", weight = 10.0)))
        assertTrue("should say what to do, got: $why", why.contains("Name the exercise"))
    }

    @Test
    fun `an added row holding a number comes back even with no name`() {
        // It used to be dropped on the read and so deleted by the next
        // save. Unnamed rows have to reach the screen: the save refuses
        // them, and a refusal about a row nobody can see is a dead end.
        val numbered = withAdded("""{"weight": 10, "reps": 12}""")
        assertEquals(listOf(10.0), SessionPatch.addedIn(numbered).map { it.weight })
    }

    @Test
    fun `an added row with a number but no name is refused, not written`() {
        val why = refused(original, emptyMap(), listOf(AddedSet(name = "", weight = 10.0)))
        assertTrue("should say what to do, got: $why", why.contains("Name the exercise"))
    }

    @Test
    fun `a blank added row is left out rather than written as unreadable`() {
        val out = patched(original, mapOf(0 to SessionPatch.Entry(reps = 8)), listOf(AddedSet(name = "")))
        assertEquals(0, doc(out)["added"]!!.jsonArray.size)
    }

    @Test
    fun `a key a newer Carry wrote on an added row survives a save`() {
        val file = withAdded(
            """{"name": "plank", "weight": null, "reps": null, "easy": false,
                "hurt": false, "note": null, "logged_by": "carry-2"}"""
        )
        // Re-saving what was read must not count as a change: before
        // unknown keys were carried, the re-encoded row never compared
        // equal to the file's, so every save rewrote the document.
        val again = SessionPatch.apply(file, emptyMap(), SessionPatch.addedIn(file))
        assertEquals(null, (again as SessionPatch.Outcome.Patched).text)

        val row = SessionPatch.addedIn(file).single()
        val out = patched(file, emptyMap(), listOf(row.copy(weight = 20.0)))
        val saved = doc(out)["added"]!!.jsonArray.single().jsonObject
        assertEquals("carry-2", saved["logged_by"]!!.jsonPrimitive.content)
        assertEquals(20.0, saved["weight"]!!.jsonPrimitive.content.toDouble(), 0.0)
    }

    @Test
    fun `an added row the screen never saw is carried back out, not deleted`() {
        // The array is replaced wholesale, so anything not handed back is
        // destroyed. Refusing the save instead was worse than it sounds:
        // the offending row is by definition one the owner cannot see, so
        // the day's real numbers could never be written either.
        for (row in listOf(
            "\"nonsense\"",                                  // not an object at all
            """{"name": "", "logged_by": "carry-2"}""",     // blank, but holds a key
            """{"name": {"a": 1}}""",                       // decodes blank, holds an object
        )) {
            val file = withAdded(row)
            assertEquals("should not reach the screen", emptyList<AddedSet>(), SessionPatch.addedIn(file))
            val out = patched(file, mapOf(0 to SessionPatch.Entry(weight = 45.0)))
            assertEquals("the day's numbers must still save", 45.0, set(out, 0)["weight"]!!.jsonPrimitive.double, 0.0)
            assertEquals("and the row must survive", doc(file)["added"], doc(out)["added"])
        }
    }

    @Test
    fun `a carried row sits alongside one the owner adds`() {
        val file = withAdded("\"nonsense\"")
        val out = patched(file, emptyMap(), listOf(AddedSet(name = "plank", reps = 60)))
        val saved = doc(out)["added"]!!.jsonArray
        assertEquals(2, saved.size)
        assertEquals("plank", saved[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("nonsense", saved[1].jsonPrimitive.content)
    }

    @Test
    fun `an added row too big to hold is carried, not held and not deleted`() {
        // Shown rows are held verbatim in the draft, which is encoded into
        // saved instance state on every keystroke and crosses a Binder
        // transaction. A file from another machine must not be able to put
        // a megabyte in there -- nor to have Carry delete it instead.
        val huge = withAdded("""{"name": "plank", "junk": "${"x".repeat(80_000)}"}""")
        assertEquals(emptyList<AddedSet>(), SessionPatch.addedIn(huge))
        val out = patched(huge, mapOf(0 to SessionPatch.Entry(weight = 45.0)))
        assertEquals(doc(huge)["added"], doc(out)["added"])
    }

    @Test
    fun `a deeply nested added row does not blow the stack on the read`() {
        // 2000 levels is 12 KB of valid JSON that parses fine, and
        // `toString()` on it overflows the stack. The READ is what
        // matters: it runs on every resume, so a throw there is a crash
        // loop on a file the phone cannot edit. Measuring a row's size
        // is depth-capped for this reason.
        val deep = withAdded("""{"name": "plank", "junk": ${"{\"a\":".repeat(2000)}1${"}".repeat(2000)}}""")
        assertEquals(emptyList<AddedSet>(), SessionPatch.addedIn(deep))

        // The WRITE still can't serialise it -- nor could it before this
        // change, since the whole document is re-encoded and the nesting
        // is in the document. It refuses, says the file is unchanged, and
        // that is true; it does not crash and does not half-write.
        val why = refused(deep, mapOf(0 to SessionPatch.Entry(weight = 45.0)))
        assertTrue("got: $why", why.contains("file is unchanged"))
    }

    @Test
    fun `a value this build cannot parse survives a row it did not edit`() {
        // Re-encoding flattens what it cannot read: `12.5` reps becomes
        // null, `45.0` becomes `45`. An untouched row goes back verbatim.
        val file = withAdded("""{"name": "plank", "weight": 45.0, "reps": 12.5}""")
        val row = SessionPatch.addedIn(file).single()
        assertEquals(null, SessionPatch.apply(file, emptyMap(), listOf(row)).let {
            (it as SessionPatch.Outcome.Patched).text
        })

        val out = patched(file, mapOf(0 to SessionPatch.Entry(reps = 8)), listOf(row))
        val saved = doc(out)["added"]!!.jsonArray.single().jsonObject
        assertEquals("12.5", saved["reps"]!!.jsonPrimitive.content)
        assertEquals("45.0", saved["weight"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a value this build cannot parse is carried, not treated as empty`() {
        // `"45 lb"` is not a number, so the row decodes blank -- and a
        // check that only looked at key NAMES called that "holds
        // nothing" and deleted it.
        for (row in listOf(
            """{"name": "", "weight": "45 lb"}""",
            """{"name": "", "easy": "yes"}""",
        )) {
            val file = withAdded(row)
            val out = patched(file, mapOf(0 to SessionPatch.Entry(weight = 45.0)))
            assertEquals("must survive the save", doc(file)["added"], doc(out)["added"])
        }
    }

    @Test
    fun `a null name does not become an exercise called null`() {
        // `JsonNull.content` is the string "null". Writing that back is
        // Carry inventing an identity, which is the one thing it must
        // never do.
        val file = withAdded("""{"name": null, "weight": 45}""")
        assertEquals("", SessionPatch.addedIn(file).single().name)
        val why = refused(file, emptyMap(), SessionPatch.addedIn(file))
        assertTrue("got: $why", why.contains("Name the exercise"))
    }

    @Test
    fun `a file with a carried row does not churn on every save`() {
        // The carried elements go back at the END, so a file that
        // interleaved them could be reordered once. It must settle: a
        // sync watches this folder, and a file that rewrites itself on
        // every save looks like a change that isn't one.
        val file = withAdded("""{"name": "plank"}, "nonsense"""")
        val once = patched(file, mapOf(0 to SessionPatch.Entry(weight = 45.0)))
        val again = SessionPatch.apply(once, emptyMap(), SessionPatch.addedIn(once))
        assertEquals(null, (again as SessionPatch.Outcome.Patched).text)
    }

    @Test
    fun `an added row keeps its id across a save, so nothing identifies it by position`() {
        val first = patched(original, emptyMap(), listOf(AddedSet(id = "abc123", name = "plank", reps = 60)))
        assertEquals("abc123", SessionPatch.addedIn(first).single().id)
        // And re-saving what was read is still a no-op: the id must not
        // be the thing that makes every save rewrite the file.
        val again = SessionPatch.apply(first, emptyMap(), SessionPatch.addedIn(first))
        assertEquals(null, (again as SessionPatch.Outcome.Patched).text)
    }

    @Test
    fun `a carried row survives even when the owner is writing one with its id`() {
        // Deduping these by id was tried and reverted. The owner's copy
        // comes from a draft pinned to OLDER bytes, so dropping the
        // file's copy writes that draft's stale `asRead` over it --
        // destroying whatever was added to the row since, which is the
        // one thing the carry path exists to prevent. A visible duplicate
        // is the lesser bug; it stays in the backlog.
        val file = withAdded("""{"id": "abc123", "name": "plank", "junk": "${"x".repeat(80_000)}"}""")
        assertEquals("too big to show, so it is carried", emptyList<AddedSet>(), SessionPatch.addedIn(file))
        val out = patched(file, emptyMap(), listOf(AddedSet(id = "abc123", name = "plank", reps = 60)))
        assertEquals("the file's copy must not be deleted", doc(file)["added"]!!.jsonArray.single(),
            doc(out)["added"]!!.jsonArray.last())
    }

    @Test
    fun `a file written before rows had ids reads with a stand-in, marked as one`() {
        val old = withAdded("""{"name": "plank", "reps": 60}""")
        val row = SessionPatch.addedIn(old).single()
        assertEquals("@0", row.id)
        // Marked, because the stand-in is the old index identity under a
        // new name and something has to replace it. The ViewModel does
        // that on seeding -- see CarryViewModelTest; this only pins that
        // the mark is readable.
        assertTrue(SessionPatch.isStandIn(row.id))
        assertTrue(SessionPatch.isStandIn(""))
        assertFalse(SessionPatch.isStandIn("real1234"))
    }

    @Test
    fun `a positional stand-in is never written into the file`() {
        // Persisted, `"@1"` collides with the next read's stand-in for
        // whatever row then sits at index 1 -- and two rows sharing an id
        // is worse than neither having one, because the screen keys its
        // boxes by it and the save dedupes by it.
        val old = withAdded("""{"name": "plank", "reps": 60}""")
        val row = SessionPatch.addedIn(old).single()
        val out = patched(old, emptyMap(), listOf(row.copy(reps = 45)))
        assertEquals(null, doc(out)["added"]!!.jsonArray.single().jsonObject["id"])

        // And one that somehow reached the file is not trusted as real.
        val poisoned = withAdded("""{"id": "@1", "name": "plank"}, {"name": "burpee"}""")
        val ids = SessionPatch.addedIn(poisoned).map { it.id }
        assertEquals("ids must stay distinct", ids.size, ids.toSet().size)
    }

    @Test
    fun `how it felt and whether it was modified reach the file`() {
        // Both are read by the coach: `hard` blocks the next bump, and
        // three clean `easier` sets earn "try one as prescribed" — the
        // only progression a bodyweight movement has.
        val out = patched(original, mapOf(
            0 to SessionPatch.Entry(weight = 45.0, hard = true),
            1 to SessionPatch.Entry(reps = 10, easier = true),
        ))
        assertEquals(true, set(out, 0)["hard"]!!.jsonPrimitive.boolean)
        assertEquals(false, set(out, 0)["easier"]!!.jsonPrimitive.boolean)
        assertEquals(true, set(out, 1)["easier"]!!.jsonPrimitive.boolean)
        // And they come back, so a session finished in two sittings keeps
        // what the first sitting said.
        assertEquals(SessionPatch.Entry(weight = 45.0, hard = true), SessionPatch.entriesIn(out)[0])
        assertEquals(SessionPatch.Entry(reps = 10, easier = true), SessionPatch.entriesIn(out)[1])
    }

    @Test
    fun `a row that only says it happened is still a row that happened`() {
        // A timed hold prescribes no reps and takes no weight, so both
        // number boxes are empty by definition and every feel chip claims
        // something the owner may not mean. Before `done` the only honest
        // thing they could do was leave it alone -- and a blank row is
        // written blank, by design, so a finished plank reached history as
        // nothing at all.
        val out = patched(original, mapOf(0 to SessionPatch.Entry(done = true)))
        assertEquals(true, set(out, 0)["done"]!!.jsonPrimitive.boolean)
        assertNull(set(out, 0)["weight"]!!.jsonPrimitive.contentOrNull)
        assertNull(set(out, 0)["reps"]!!.jsonPrimitive.contentOrNull)
        // And it survives the round trip, so a session finished in two
        // sittings does not lose the first sitting's holds.
        assertEquals(SessionPatch.Entry(done = true), SessionPatch.entriesIn(out)[0])
        // The point of the field: this row is no longer blank, which is
        // what decides whether it is written at all.
        assertFalse(SessionPatch.Entry(done = true).blank)
    }

    @Test
    fun `a key the writer removed stays removed, rather than coming back`() {
        // `asRead` is pinned to the bytes the owner's first keystroke saw.
        // Copying unknown keys out of THAT put back a key the writer had
        // since deleted, resurrecting data nobody holds any more. The row
        // is found on disk by id now.
        val before = withAdded("""{"id": "abc", "name": "plank", "logged_by": "carry-2"}""")
        val row = SessionPatch.addedIn(before).single()
        assertEquals("carry-2", doc(before)["added"]!!.jsonArray.single()
            .jsonObject["logged_by"]!!.jsonPrimitive.content)

        // The coach rewrites the file and drops that key.
        val after = withAdded("""{"id": "abc", "name": "plank"}""")
        val out = patched(after, emptyMap(), listOf(row.copy(reps = 60)))
        val saved = doc(out)["added"]!!.jsonArray.single().jsonObject
        assertEquals(null, saved["logged_by"])
        assertEquals("60", saved["reps"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a key the writer ADDED since is kept`() {
        // The other direction, and the reason the keys are copied at all.
        val before = withAdded("""{"id": "abc", "name": "plank"}""")
        val row = SessionPatch.addedIn(before).single()
        val after = withAdded("""{"id": "abc", "name": "plank", "rpe": 8}""")
        val out = patched(after, emptyMap(), listOf(row.copy(reps = 60)))
        assertEquals("8", doc(out)["added"]!!.jsonArray.single()
            .jsonObject["rpe"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a row added to a long list does not vanish after the save`() {
        // A count cap had a cliff in it: with forty rows already there, a
        // row the owner typed was written and then dropped by the re-seed
        // -- in the file, and no longer reachable. The budget is in bytes,
        // which is what the draft actually has to carry, and a real added
        // row is a hundred-odd of them.
        val many = (0 until 60).joinToString(",") {
            """{"id": "r$it", "name": "move $it", "reps": 10}"""
        }
        val file = withAdded(many)
        val rows = SessionPatch.addedIn(file)
        assertEquals("all of them reach the screen", 60, rows.size)

        val out = patched(file, emptyMap(), rows + AddedSet(id = "new", name = "plank", reps = 60))
        assertEquals(61, doc(out)["added"]!!.jsonArray.size)
        assertEquals("and it is still there on the way back", 61, SessionPatch.addedIn(out).size)
    }

    /** The fixture with a different `added` array, so each case states only
     *  the rows it is about. */
    private fun withAdded(rows: String): String {
        val placeholder = "\"added\": []"
        assertTrue("fixture no longer has $placeholder", original.contains(placeholder))
        return original.replace(placeholder, "\"added\": [$rows]")
    }
}
