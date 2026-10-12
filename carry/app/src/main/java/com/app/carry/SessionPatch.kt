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
        val hurt: Boolean = false,
        val skipped: Boolean = false,
        val note: String? = null,
    ) {
        val blank: Boolean get() =
            weight == null && reps == null && !easy && !hurt && !skipped && note == null
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
        // The `added` array is REPLACED wholesale below, so a row this
        // build cannot read is not merely skipped -- it is deleted, and it
        // held the owner's own typing from an earlier sitting. `sets`
        // already refuses for exactly this reason.
        if ((doc["added"] as? JsonArray)?.oversized() == true) {
            return Outcome.Refused(
                "That file's added-exercise list is far bigger than anything Carry wrote, so it wasn't touched."
            )
        }
        val unreadable = (doc["added"] as? JsonArray).orEmptyList().count { !it.readableBack() }
        if (unreadable > 0) {
            return Outcome.Refused(
                "That file has $unreadable added exercise${if (unreadable == 1) "" else "s"} " +
                    "Carry can't read, and saving would delete ${if (unreadable == 1) "it" else "them"}."
            )
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
        // `addedIn` drops it and the next save deletes it. Rather than
        // lose the typing, say so: the row is on screen, so naming it or
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
        val addedChanged = keep.map(::encode) != addedNow
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
            put("added", JsonArray(keep.map(::encode)))
        }
        return runCatching {
            Outcome.Patched(Session.json.encodeToString(JsonObject.serializer(), patched))
        }.getOrElse { Outcome.Refused("Carry couldn't write that back safely, so the file is unchanged.") }
    }

    private fun JsonArray?.orEmptyList(): List<JsonElement> = this?.toList() ?: emptyList()

    private fun encode(row: AddedSet): JsonObject = buildJsonObject {
        // Copy FIRST, then overwrite, as the `sets` path does: a key a
        // newer Carry wrote on this row survives an edit by this one.
        row.asRead?.forEach { (key, value) -> put(key, value) }
        put("name", JsonPrimitive(row.name))
        put("weight", row.weight?.let(::number) ?: JsonNull)
        put("reps", row.reps?.let(::JsonPrimitive) ?: JsonNull)
        put("easy", JsonPrimitive(row.easy))
        put("hurt", JsonPrimitive(row.hurt))
        put("note", row.note?.let(::JsonPrimitive) ?: JsonNull)
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
    private fun normalise(element: JsonElement?, depth: Int = 0): String = when {
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
    fun addedIn(original: String): List<AddedSet> {
        val rows = runCatching {
            (Session.json.parseToJsonElement(original) as? JsonObject)?.get("added") as? JsonArray
        }.getOrNull() ?: return emptyList()
        if (rows.oversized()) return emptyList()
        // Kept on "holds something", not on "has a name". A row with a
        // weight and no name has to come BACK to the screen: the save
        // refuses it, and a row the owner cannot see is a refusal they
        // cannot act on. Only a row holding nothing is dropped.
        return rows.mapNotNull { element ->
            runCatching { element.decodeAdded()?.takeIf { !it.blank } }.getOrNull()
        }
    }

    private fun JsonElement.decodeAdded(): AddedSet? {
        val row = this as? JsonObject ?: return null
        return AddedSet(
            name = (row["name"] as? JsonPrimitive)?.content.orEmpty(),
            weight = (row["weight"] as? JsonPrimitive)?.content?.toDoubleOrNull(),
            reps = (row["reps"] as? JsonPrimitive)?.content?.toIntOrNull(),
            easy = (row["easy"] as? JsonPrimitive)?.content.toBoolean(),
            hurt = (row["hurt"] as? JsonPrimitive)?.content.toBoolean(),
            note = (row["note"] as? JsonPrimitive)?.takeIf { it != JsonNull }?.content,
            asRead = row,
        )
    }

    /**
     * Whether a save would hand this element back as it found it.
     *
     * The save replaces the whole array, so a row [addedIn] drops is not
     * skipped but deleted. One kind of row is safe to drop: one holding
     * nothing at all, which costs nobody anything and is not worth
     * blocking a save over, whoever wrote it.
     */
    private fun JsonElement.readableBack(): Boolean {
        val decoded = runCatching { decodeAdded() }.getOrNull() ?: return false
        if (!decoded.blank) return true          // kept, and its keys carried
        // Dropped. Fine only if there was nothing in it to begin with --
        // an unknown key is still something, even next to empty fields.
        return (this as JsonObject).keys.all { it in WRITTEN_KEYS }
    }

    /** The keys [encode] writes, so [readableBack] can tell "an old empty
     *  row" from "a row carrying something this build cannot read". */
    private val WRITTEN_KEYS = setOf("name", "weight", "reps", "easy", "hurt", "note")

    /**
     * Too big to carry.
     *
     * [AddedSet.asRead] holds each row verbatim, and the rows live in the
     * draft -- which is re-encoded into saved instance state on EVERY
     * keystroke and crosses a Binder transaction to get there. The draft
     * used to hold the whole file text and died of exactly that past about
     * half a megabyte, destroying the typing it existed to protect.
     *
     * Carry writes these rows itself and a real one is a hundred-odd
     * bytes, so this is roughly five hundred of them. But the file is
     * synced from another machine, so its size is not ours to assume --
     * and a save that cannot carry them refuses rather than deletes them.
     */
    private fun JsonArray.oversized(): Boolean = toString().length > MAX_ADDED_BYTES

    private const val MAX_ADDED_BYTES = 64 * 1024
}
