package com.app.carry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The session file, as the companion script defines it.
 *
 * Carry does not own this shape and must not extend it locally -- a field
 * invented here is a field the importer drops. The runner writes each set
 * with its identity attached and `weight` null; Carry fills in the numbers
 * and writes the same file back.
 *
 * [ExerciseSet.variant] is deliberately an opaque [JsonElement]. It carries
 * the variant words the coach matches on, and Carry has no business reading
 * or reconstructing them -- it only has to hand them back byte-for-byte.
 * That is the "never invent an exercise's identity" rule, enforced by the
 * type rather than by care.
 */
@Serializable
data class Session(
    /**
     * Nullable so that coercion cannot vouch for a file.
     *
     * [SessionFiles.parse] decides a file is ours by comparing this to
     * [SCHEMA]. Were it non-nullable with a default, `coerceInputValues`
     * would turn some other tool's `"schema": null` INTO "gym-session/1"
     * and the gate would pass its own default back to itself.
     */
    val schema: String? = SCHEMA,
    @SerialName("sheet_id") val sheetId: String,
    val date: String,
    @SerialName("class") val classTag: String? = null,
    @SerialName("borrowed_from") val borrowedFrom: String? = null,
    val sets: List<ExerciseSet> = emptyList(),
) {
    companion object {
        const val SCHEMA = "gym-session/1"

        /** Lenient on read so a newer runner can add fields without breaking
         *  an older build; explicit on write so nothing is silently dropped. */
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = true
            prettyPrint = true
            // A `null` where this build expects a plain value falls back to
            // the default instead of throwing. Without it a single
            // `"done": null` from another writer took down the WHOLE day --
            // the decode threw, `SessionFiles.parse` turned that into null,
            // and the owner got "Can't read that session" for a file whose
            // every number was intact. For these fields the fallback is
            // also the right answer: `false` is exactly what an unset flag
            // means, and a null `sets` then reads the same as an absent one.
            //
            // It does NOT rescue a value of the wrong type -- `"done":
            // "yes"` still costs the day -- which is the wider version of
            // this and is on the backlog with its own evidence.
            coerceInputValues = true
        }
    }
}

@Serializable
data class ExerciseSet(
    val ref: Int,
    /**
     * What "the same exercise" means, decided by the runner and carried
     * here as one opaque string.
     *
     * Carry must not work this out for itself. The key is a deliberately
     * chosen subset of the variant — the runner leaves out intensity, grip
     * and reps — and a version of [Growth] that hashed the whole variant
     * instead made 20 of the owner's 20 lifts look distinct when one pair
     * was the same movement, which silently doubled the figure the growth
     * screen uses to explain itself. Compared, never read.
     */
    val key: String? = null,
    val line: String,
    val canonical: String? = null,
    val components: List<String>? = null,
    val variant: JsonElement? = null,
    @SerialName("prescribed_reps") val prescribedReps: Int? = null,
    /**
     * What the BOARD said, as written: "8", "10 total", "6-10 each".
     *
     * [prescribedReps] is the number the runner's weight math uses — an
     * alternating "10 total" is 5 a side, a range is its floor — and for
     * most rows the two differ. Showing or publishing that number as the
     * prescription tells the owner, and the subreddit, to do half the reps.
     * So anything a person reads uses this; [prescribedReps] is for
     * comparing rows, not for display.
     */
    @SerialName("prescribed_text") val prescribedText: String? = null,
    /** Seconds, when the line prescribes time instead of reps ("45 sec of
     *  low plank"). Exclusive with the two above. */
    @SerialName("prescribed_duration_sec") val prescribedDurationSec: Int? = null,
    /** What the coach said to try, verbatim, e.g. "try 45 lb (up from 40, held 3x)". */
    val suggested: String? = null,
    /** Parsed out by the runner so the screen never has to read prose. */
    @SerialName("suggested_weight") val suggestedWeight: Double? = null,
    @SerialName("previous_weight") val previousWeight: Double? = null,
    /** legs | push | pull | core | combo -- from the knowledge base's muscle groups. */
    val family: String? = null,
    /** Clean sets held at this weight, and how many a bump needs. */
    @SerialName("streak_done") val streakDone: Int? = null,
    @SerialName("streak_needed") val streakNeeded: Int? = null,

    // --- what Carry fills in ---
    val weight: Double? = null,
    val reps: Int? = null,
    val easy: Boolean = false,
    val hard: Boolean = false,
    val easier: Boolean = false,
    /** "I did it, and there is nothing to say about it" -- the only record a
     *  timed hold can carry, since it prescribes no reps and takes no
     *  weight. Read back, so a finished plank is not shown blank. */
    val done: Boolean = false,
    val hurt: Boolean = false,
    val skipped: Boolean = false,
    val note: String? = null,
) {
    /** A row the owner actually filled in. Blank stays blank: never "skipped". */
    val answered: Boolean get() = weight != null || reps != null || skipped

    /**
     * The board asked for seconds, not reps.
     *
     * Same bound [prescription] applies, so the one row shape that shows
     * "45 sec of" is the one row shape offered the `done` chip -- a
     * nonsense duration must not quietly turn a normal row into a timed
     * one.
     */
    val timed: Boolean get() = prescribedDurationSec?.let { it in 1..MAX_DURATION_SEC } == true

    /** The amber case, and the only number you would get wrong from memory --
     *  by feel you repeat last session's weight. */
    val change: Double?
        get() {
            val now = suggestedWeight ?: return null
            val before = previousWeight ?: return null
            return (now - before).takeIf { it != 0.0 }
        }

    val displayName: String
        get() = (canonical ?: components?.joinToString(" + ") ?: line).readable()

    /**
     * The prescription as the board wrote it — "10 total x", "45 sec of" —
     * or null when the line states neither. Walking in, this is half of
     * what the row is: a 30-second plank and a 90-second one are different
     * asks, and "10 total" means five a side.
     */
    val prescription: String?
        get() = prescribedDurationSec?.takeIf { it in 1..MAX_DURATION_SEC }?.let { "${durationText(it)} of" }
            ?: prescribedText?.asScheme()?.let { "$it x" }
}

/**
 * Strips the characters that can make text render in an order other than
 * the one stored: bidi overrides and embeddings, and zero-width marks.
 *
 * These arrive by accident rather than by attack — the names come from
 * Reddit comments by way of another machine — but this is an app whose
 * stated worst outcome is a wrong number in someone's head, and a line
 * that reads backwards is the same kind of failure.
 */
/**
 * A set is never longer than this. The field arrives from a file written on
 * another machine, so `0`, a negative and `Int.MAX_VALUE` all have to be
 * refused rather than rendered: `0 sec of squat` and `-45 sec of squat`
 * would go on the Log row AND into a public post.
 */
private const val MAX_DURATION_SEC = 60 * 60

/** "45 sec", "1 min" — the way the sheet writes it. */
internal fun durationText(seconds: Int): String =
    if (seconds % 60 == 0 && seconds >= 60) "${seconds / 60} min" else "$seconds sec"

/**
 * A rep scheme, or null if it does not look like one.
 *
 * `prescribed_text` is free text from the same file, and the same writer,
 * as `line` and `suggested` — and `suggested` holds "try 45 lb (up from 40,
 * held 3x)". This field is both shown to the owner and published, so it is
 * the one place a runner revision could put a weight in a post under the
 * words "never your weights". A scheme is digits, separators and the words
 * "total"/"each"; anything else is refused outright rather than trusted,
 * and the row simply carries no prescription.
 */
private val SCHEME = Regex("""\d{1,3}(?:\s*[-/]\s*\d{1,3}){0,4}(?:\s+(?:total|each|per side))?""")

internal fun String.asScheme(): String? = trim().takeIf { SCHEME.matches(it) }

internal fun String.readable(): String = filter { c ->
    c.code !in 0x202A..0x202E && c.code !in 0x2066..0x2069 &&
        c.code != 0x200B && c.code != 0x200E && c.code != 0x200F && c.code != 0xFEFF
}

/**
 * Something the sheet never listed. On a day with no intel for the owner's
 * class, these are the session rather than an afterthought.
 *
 * Deliberately NOT part of [Session]. The file's `added` array is read and
 * written only by [SessionPatch], against the raw JSON. Decoding it here
 * as well made the whole day unreadable over one odd row -- a row with no
 * `name` key threw `MissingFieldException`, the loader turned that into
 * "Can't read that session", and the owner lost the sheet along with it.
 */
@Serializable
data class AddedSet(
    /**
     * This row's own name for itself, so nothing has to identify it by
     * where it sits in a list.
     *
     * Position was the identity before, and it was wrong in three ways at
     * once: the text boxes were keyed by index, so after the list shrank
     * a row showed the previous occupant's number — the screen saying 30
     * while the file said nothing; a row could be written twice when the
     * file was rewritten underneath and it changed category between
     * reads; and there was no way to tell "the row the owner is editing"
     * from "the row in that slot".
     *
     * Written into the file, which the owner approved on 2026-10-12. The
     * importer reads named keys and ignores the rest, so it is inert
     * there — this is Carry's own bookkeeping, kept where it survives a
     * reload.
     */
    val id: String = "",
    val name: String = "",
    val weight: Double? = null,
    val reps: Int? = null,
    val easy: Boolean = false,
    val hard: Boolean = false,
    val easier: Boolean = false,
    val hurt: Boolean = false,
    val note: String? = null,
    /**
     * The row exactly as the file held it, carried so that a key this
     * build has never heard of is handed back rather than dropped.
     *
     * The `sets` path copies unknown keys through already; this one did
     * not, and the whole `added` array is replaced on every save, so a
     * field a newer Carry wrote would be deleted by an older one. It also
     * made a no-op save rewrite the file: the re-encoded row never
     * compared equal to the row in the file, so "has anything changed"
     * was always yes.
     *
     * Opaque on purpose. This build cannot know what those keys mean, so
     * it never reads them -- the same bargain as [ExerciseSet.variant].
     */
    @SerialName("as_read") val asRead: JsonObject? = null,
) {
    /** A row the owner added and never filled in. Nothing to save, and
     *  nothing lost by leaving it out of the file. */
    val blank: Boolean get() =
        name.isBlank() && weight == null && reps == null &&
            !easy && !hard && !easier && !hurt && note.isNullOrBlank()

    companion object {
        /** Short, because it goes in the file next to a hand-typed name
         *  and the owner may read it. Collisions do not corrupt anything
         *  — two rows sharing an id are interchangeable to every caller
         *  — so there is no reason to spend 36 characters on one. */
        fun newId(): String = java.util.UUID.randomUUID().toString().take(8)
    }
}
