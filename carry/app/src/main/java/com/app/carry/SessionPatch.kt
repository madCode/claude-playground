package com.app.carry

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Writes the owner's numbers into the file the runner wrote, by PATCHING
 * its JSON rather than re-serialising [Session].
 *
 * Cycle 1 proved re-encoding is lossy in two ways, both of which break
 * CLAUDE.md's promise that unfilled rows survive a round trip untouched: a
 * field a newer runner adds is dropped, and a weight of `45` comes back as
 * `45.0`. A model cannot faithfully reproduce a document it only partly
 * describes. So every key the runner wrote is copied through and only the
 * six a person can edit are replaced.
 *
 * **It refuses rather than guesses.** A review of the first version found
 * three ways it would quietly destroy data: a `sets` key that was not an
 * array became `[]`, taking the whole workout with it; a `ref` it could
 * not read dropped that row's numbers while still reporting success; and
 * two rows sharing a `ref` got the same weight written against both. All
 * three now return [Outcome.Refused] and leave the file alone. A training
 * history can survive a missing number; it cannot survive a wrong one,
 * because nothing downstream can tell.
 */
object SessionPatch {

    /** What a person can change about one row. */
    @kotlinx.serialization.Serializable
    data class Entry(
        val weight: Double? = null,
        val reps: Int? = null,
        val easy: Boolean = false,
        /** Exclusive with [easy]: a set is not both. Blocks the next bump
         *  rather than earning one — the coach could hear "easy" and push,
         *  and had nothing at all to hear for the other direction. */
        val hard: Boolean = false,
        /** "I did the easier version" — knees, a box under the step-up.
         *  The only progression a bodyweight movement has, and a third of
         *  this owner's sets carry no weight at all. */
        val easier: Boolean = false,
        val hurt: Boolean = false,
        val skipped: Boolean = false,
        val note: String? = null,
    ) {
        val blank: Boolean get() =
            weight == null && reps == null && !easy && !hard && !easier &&
                !hurt && !skipped && note == null
    }

    sealed interface Outcome {
        /** [text] is what to save; null means nothing changed, so do not write. */
        data class Patched(val text: String?) : Outcome

        /** The file is not shaped the way this app understands, so it was
         *  not touched. [why] is for the owner, not a log. */
        data class Refused(val why: String) : Outcome
    }

    private fun number(value: Double): JsonPrimitive? = when {
        value.isNaN() || value.isInfinite() -> null   // the encoder rejects these
        value == value.toLong().toDouble() -> JsonPrimitive(value.toLong())
        else -> JsonPrimitive(value)
    }

    private fun Entry.fields(): Map<String, JsonElement> = buildMap {
        put("weight", weight?.let(::number) ?: JsonNull)
        put("reps", reps?.let(::JsonPrimitive) ?: JsonNull)
        put("easy", JsonPrimitive(easy))
        put("hard", JsonPrimitive(hard))
        put("easier", JsonPrimitive(easier))
        put("hurt", JsonPrimitive(hurt))
        put("skipped", JsonPrimitive(skipped))
        put("note", note?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun refOf(set: JsonObject): Int? =
        (set["ref"] as? JsonPrimitive)?.takeIf { it.isString.not() }?.content?.toIntOrNull()

    /**
     * @param edits the desired state of each row it names, not a list of
     *   changes. A blank [Entry] for a row that currently holds a number
     *   therefore CLEARS it — the first version filtered blanks out
     *   entirely, so a mistyped weight could never be unmade.
     * @param added the complete list of rows the owner added, replacing
     *   whatever the file holds. Appending made a second save duplicate
     *   every added row.
     */
    fun apply(original: String, edits: Map<Int, Entry>, added: List<AddedSet> = emptyList()): Outcome {
        val doc = runCatching { Session.json.parseToJsonElement(original) as? JsonObject }.getOrNull()
            ?: return Outcome.Refused("That file isn't JSON this app can read, so nothing was changed.")

        val rawSets = doc["sets"]
        if (rawSets != null && rawSets !is JsonArray) {
            return Outcome.Refused("That file's exercise list isn't shaped the way Carry expects.")
        }
        if (doc["added"] != null && doc["added"] !is JsonArray) {
            return Outcome.Refused("That file's added-exercise list isn't shaped the way Carry expects.")
        }
        val setsIn = (rawSets as? JsonArray)?.map { it as? JsonObject ?: return Outcome.Refused(
            "One of the exercises in that file isn't shaped the way Carry expects."
        ) } ?: emptyList()

        val refs = setsIn.mapNotNull(::refOf)
        if (refs.size != refs.toSet().size) {
            return Outcome.Refused("That file lists two exercises with the same id, so Carry can't tell them apart.")
        }
        val unplaceable = edits.keys - refs.toSet()
        if (unplaceable.isNotEmpty()) {
            return Outcome.Refused("Carry couldn't find ${unplaceable.size} of those exercises in the file any more. It may have been replaced while you were filling it in.")
        }

        // An added row with numbers but no name cannot be read back --
        // nothing downstream can match it to an exercise. Rather than lose
        // the typing, say so: the row IS on screen, so naming it or
        // clearing it is one tap away. A row with nothing in it at all is
        // simply left out.
        val keep = added.filterNot { it.blank }
        val unnamed = keep.count { it.name.isBlank() }
        if (unnamed > 0) {
            return Outcome.Refused(
                "Name the ${if (unnamed == 1) "exercise" else "$unnamed exercises"} you added, " +
                    "or clear ${if (unnamed == 1) "its" else "their"} numbers, before saving."
            )
        }

        val current = entriesIn(original)
        val changed = edits.filter { (ref, entry) -> entry != (current[ref] ?: Entry()) }
        val addedNow = (doc["added"] as? JsonArray).orEmptyList()
        // Whatever the screen never saw goes back untouched, at the end.
        // An earlier version REFUSED the save instead, which looked like
        // the right kind of caution and was a trap: the offending row is
        // by definition one the owner cannot see, so there was nothing
        // they could do about it and the day's real numbers could never
        // be written. Carrying loses nothing and blocks nothing.
        val (_, carried) = (doc["added"] as? JsonArray).splitAdded()
        // Deliberately NOT deduped against `keep` by id, though the ids
        // now make that possible. A draft pinned to older bytes can hold
        // the owner's copy of a row the current file carries, and
        // dropping the file's copy would write the draft's stale `asRead`
        // over it -- destroying a key added since, which is the one thing
        // the carry path exists to prevent. A visible duplicate is worse
        // than it sounds and far better than a silent deletion, so the
        // duplicate stays logged in the backlog rather than traded for a
        // worse bug. Honouring it needs the file's row, not the draft's.
        val writtenAdded = keep.map(::encode) + carried
        val addedChanged = writtenAdded != addedNow
        if (changed.isEmpty() && !addedChanged) return Outcome.Patched(null)

        val sets = setsIn.map { set ->
            val edit = refOf(set)?.let(changed::get) ?: return@map set
            // Copy FIRST, then overwrite: anything the runner wrote that
            // this app has never heard of survives untouched.
            buildJsonObject {
                set.forEach { (key, value) -> put(key, value) }
                edit.fields().forEach { (key, value) -> put(key, value) }
            }
        }

        val patched = buildJsonObject {
            doc.forEach { (key, value) -> put(key, value) }
            put("sets", JsonArray(sets))
            put("added", JsonArray(writtenAdded))
        }
        return runCatching {
            Outcome.Patched(Session.json.encodeToString(JsonObject.serializer(), patched))
        }.getOrElse { Outcome.Refused("Carry couldn't write that back safely, so the file is unchanged.") }
    }

    private fun JsonArray?.orEmptyList(): List<JsonElement> = this?.toList() ?: emptyList()

    /** Marks an id this app made up for a row that arrived without one.
     *  Never written to the file; see [encode]. */
    const val STAND_IN = "@"

    /** True for an id that is this app's own guess rather than the row's
     *  own name, so a caller can replace it with a real one. */
    fun isStandIn(id: String): Boolean = id.isBlank() || id.startsWith(STAND_IN)

    private fun encode(row: AddedSet): JsonObject {
        val asRead = row.asRead
        // Untouched? Hand back the bytes that came in. Re-encoding is
        // lossy in both directions: it normalises `45.0` to `45`, so a
        // save that changed nothing would rewrite a file a sync is
        // watching, and it flattens anything this build cannot parse --
        // a `reps` of `12.5` becomes null -- on a row nobody edited.
        // `copy(id = row.id)`: a file written before rows had ids decodes
        // with a positional one, and the row now carries a real one. That
        // difference alone is not an edit -- but it IS a reason to write,
        // so the id is excluded from the comparison and the fresh encode
        // below puts it in the file.
        if (asRead != null && asRead.decodeAdded(0)?.copy(id = row.id) == row) return asRead
        return buildJsonObject {
            // Copy FIRST, then overwrite, as the `sets` path does: a key a
            // newer Carry wrote on this row survives an edit by this one.
            asRead?.forEach { (key, value) -> put(key, value) }
            // A real one only. Writing `"id": ""` would put a key in the
            // file that says nothing, and writing a POSITIONAL stand-in
            // is worse: persisted as `"@1"`, it collides with the next
            // read's stand-in for whatever row then sits at index 1, and
            // two rows sharing an id is worse than neither having one --
            // the screen keys boxes by it and the save dedupes by it.
            // Stand-ins stay in memory, where they are unique by
            // construction.
            if (row.id.isNotBlank() && !row.id.startsWith(STAND_IN)) put("id", JsonPrimitive(row.id))
            put("name", JsonPrimitive(row.name))
            put("weight", row.weight?.let(::number) ?: JsonNull)
            put("reps", row.reps?.let(::JsonPrimitive) ?: JsonNull)
            put("easy", JsonPrimitive(row.easy))
            put("hard", JsonPrimitive(row.hard))
            put("easier", JsonPrimitive(row.easier))
            put("hurt", JsonPrimitive(row.hurt))
            put("note", row.note?.let(::JsonPrimitive) ?: JsonNull)
        }
    }

    /**
     * What the file already records, so a session finished in two sittings
     * shows what the first sitting entered.
     *
     * Guarded per row, not once around the whole loop: one junk element
     * used to erase every other row's recorded answers, and the owner
     * would have re-entered them from memory.
     */
    fun entriesIn(original: String): Map<Int, Entry> {
        val sets = runCatching {
            (Session.json.parseToJsonElement(original) as? JsonObject)?.get("sets") as? JsonArray
        }.getOrNull() ?: return emptyMap()
        return sets.mapNotNull { element ->
            runCatching {
                val set = element as? JsonObject ?: return@runCatching null
                val ref = refOf(set) ?: return@runCatching null
                val entry = Entry(
                    weight = (set["weight"] as? JsonPrimitive)?.content?.toDoubleOrNull(),
                    reps = (set["reps"] as? JsonPrimitive)?.content?.toIntOrNull(),
                    easy = (set["easy"] as? JsonPrimitive)?.content.toBoolean(),
                    hard = (set["hard"] as? JsonPrimitive)?.content.toBoolean(),
                    easier = (set["easier"] as? JsonPrimitive)?.content.toBoolean(),
                    hurt = (set["hurt"] as? JsonPrimitive)?.content.toBoolean(),
                    skipped = (set["skipped"] as? JsonPrimitive)?.content.toBoolean(),
                    note = (set["note"] as? JsonPrimitive)?.takeIf { it != JsonNull }?.content,
                )
                if (entry.blank) null else ref to entry
            }.getOrNull()
        }.toMap()
    }

    /**
     * True when every named row still describes the same exercise in both
     * texts.
     *
     * `ref` is only an index, so a runner that re-runs and renumbers its
     * rows turns an edit meant for a deadlift into one against a lateral
     * raise. [apply] refuses a ref that has gone missing but cannot see a
     * renumbering, which is the quieter and worse case.
     */
    fun identityHolds(before: String, after: String, refs: Set<Int>): Boolean =
        identityHolds(identities(before), after, refs)

    fun identityHolds(before: Map<Int, String>, after: String, refs: Set<Int>): Boolean {
        val b = identities(after)
        return refs.all { before[it] != null && before[it] == b[it] }
    }

    /** Key order must not matter: `variant` is an opaque object the runner
     *  may re-emit in any order, and comparing its printed form would
     *  refuse saves for a file that had not meaningfully changed. */
    internal fun normalise(element: JsonElement?, depth: Int = 0): String = when {
        element == null -> "\u0001absent"
        // The file is written elsewhere and `variant` is deliberately
        // opaque, so its nesting is not ours to trust. Overflowing the
        // stack here would be swallowed into "the exercises no longer line
        // up" and make saving impossible for good.
        depth > MAX_DEPTH -> "\u0001deep"
        element is JsonObject -> element.entries.sortedBy { it.key }
            .joinToString(",", "{", "}") { "${it.key}:${normalise(it.value, depth + 1)}" }
        element is JsonArray -> element.joinToString(",", "[", "]") { normalise(it, depth + 1) }
        else -> element.toString()
    }

    /** A variant is a flat bag of words; anything deeper is not meaningful. */
    private const val MAX_DEPTH = 12

    /** The identities a draft needs to remember, which is far less than
     *  the whole file it used to keep. */
    fun identitiesOf(text: String): Map<Int, String> = identities(text)

    private fun identities(text: String): Map<Int, String> = runCatching {
        ((Session.json.parseToJsonElement(text) as JsonObject)["sets"] as JsonArray)
            .mapNotNull { element ->
                val set = element as? JsonObject ?: return@mapNotNull null
                val ref = refOf(set) ?: return@mapNotNull null
                // prescribed_reps included: the same movement appears in two
                // blocks of one workout often enough that `line` alone can
                // make two different rows compare equal, and a renumbering
                // that swapped exactly those would slip through.
                ref to listOf("line", "canonical", "components", "variant", "prescribed_reps")
                    .joinToString("\u0000") { normalise(set[it]) }
            }.toMap()
    }.getOrDefault(emptyMap())

    /** The rows the owner added, so a second save neither duplicates nor
     *  forgets them. */
    fun addedIn(original: String): List<AddedSet> = runCatching {
        (Session.json.parseToJsonElement(original) as? JsonObject)?.get("added") as? JsonArray
    }.getOrNull().splitAdded().first

    /**
     * The file's `added` array, split into the rows the screen gets and
     * the elements carried back out untouched.
     *
     * Both halves come from here so they cannot drift: anything not shown
     * is carried, and the save writes shown + carried. That is the whole
     * safety property -- the array is replaced wholesale, so a row this
     * build does not hand back is a row it has deleted.
     */
    private fun JsonArray?.splitAdded(): Pair<List<AddedSet>, List<JsonElement>> {
        val shown = mutableListOf<AddedSet>()
        val carried = mutableListOf<JsonElement>()
        for ((at, element) in orEmptyList().withIndex()) {
            val decoded = runCatching { element.decodeAdded(at) }.getOrNull()
            when {
                // Kept on "holds something", not on "has a name". A row
                // with a weight and no name has to come BACK to the
                // screen: the save refuses it, and a row the owner cannot
                // see is a refusal they cannot act on.
                decoded != null && !decoded.blank &&
                    shown.size < MAX_ADDED_ROWS && element.bulk() <= MAX_ROW_BYTES -> shown += decoded
                element.holdsNothing() -> Unit
                else -> carried += element
            }
        }
        return shown to carried
    }

    private fun JsonElement.decodeAdded(at: Int): AddedSet? {
        val row = this as? JsonObject ?: return null
        return AddedSet(
            // A file written before rows had ids gets a positional one.
            // Stable for as long as that file is, which is until the
            // first save — after which it carries real ones. NOT derived
            // from the row's content: that changes on every keystroke,
            // and the text boxes are keyed by this.
            id = (row["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?.takeIf { it.isNotBlank() && !it.startsWith(STAND_IN) } ?: "$STAND_IN$at",
            // takeIf, as `note` does: `JsonNull.content` is the string
            // "null", so `{"name": null}` used to reach the screen as an
            // exercise called null and be WRITTEN back as one. Carry
            // inventing an identity is the one thing it must never do.
            name = (row["name"] as? JsonPrimitive)?.takeIf { it != JsonNull }?.content.orEmpty(),
            weight = (row["weight"] as? JsonPrimitive)?.content?.toDoubleOrNull(),
            reps = (row["reps"] as? JsonPrimitive)?.content?.toIntOrNull(),
            easy = (row["easy"] as? JsonPrimitive)?.content.toBoolean(),
            hard = (row["hard"] as? JsonPrimitive)?.content.toBoolean(),
            easier = (row["easier"] as? JsonPrimitive)?.content.toBoolean(),
            hurt = (row["hurt"] as? JsonPrimitive)?.content.toBoolean(),
            note = (row["note"] as? JsonPrimitive)?.takeIf { it != JsonNull }?.content,
            asRead = row,
        )
    }

    /** Provably empty, so dropping it rather than carrying it loses
     *  nothing. Every key is one [encode] writes and every value is one
     *  an empty row is written with. It is not enough to check the key
     *  NAMES: `{"name": "", "weight": "45 lb"}` decodes blank, because
     *  "45 lb" is not a number, and used to be deleted on that basis. */
    private fun JsonElement.holdsNothing(): Boolean {
        val row = this as? JsonObject ?: return false
        return row.all { (key, value) ->
            key in WRITTEN_KEYS && value is JsonPrimitive && value.empty()
        }
    }

    /** What [encode] writes for a field nobody filled in. Anything else
     *  is content, whether or not this build can parse it. */
    private fun JsonPrimitive.empty(): Boolean =
        this == JsonNull || content.isBlank() || (!isString && content == "false")

    /** The keys [encode] writes, so [holdsNothing] can tell an empty row
     *  from one carrying something this build cannot read. */
    private val WRITTEN_KEYS = setOf("id", "name", "weight", "reps", "easy", "hard", "easier", "hurt", "note")

    /**
     * Roughly how many bytes a row would cost to hold, without building
     * the string to find out.
     *
     * [AddedSet.asRead] keeps each shown row verbatim, and shown rows live
     * in the draft -- re-encoded into saved instance state on EVERY
     * keystroke, across a Binder transaction. The draft once held the
     * whole file text and died of exactly that past about half a
     * megabyte, destroying the typing it exists to protect.
     *
     * Depth-capped, and that is not belt-and-braces: `toString()` on a
     * two-thousand-deep object overflows the stack, and this runs inside
     * the read on every resume, where the throw would be a crash loop on
     * a file the phone cannot edit. A row too big or too deep is carried
     * rather than shown, so nothing is lost by refusing to hold it.
     */
    private fun JsonElement.bulk(depth: Int = 0): Int {
        if (depth > MAX_DEPTH) return TOO_BIG
        var n = 2
        when (this) {
            is JsonObject -> for ((key, value) in this) {
                n += key.length + 3 + value.bulk(depth + 1)
                if (n >= TOO_BIG) return TOO_BIG
            }
            is JsonArray -> for (value in this) {
                n += 1 + value.bulk(depth + 1)
                if (n >= TOO_BIG) return TOO_BIG
            }
            else -> n += toString().length
        }
        return n
    }

    /** Big enough that nothing Carry wrote comes close -- a real added row
     *  is a hundred-odd bytes -- and small enough that forty of them do
     *  not threaten the transaction the draft crosses. [MAX_DEPTH] is
     *  borrowed from [normalise]; a legitimately deeper added row is
     *  carried rather than shown, which no writer has yet produced. */
    private const val MAX_ROW_BYTES = 2048
    private const val MAX_ADDED_ROWS = 40
    private const val TOO_BIG = Int.MAX_VALUE / 4
}
