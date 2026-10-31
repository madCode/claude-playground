package com.app.carry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The session file, as the companion script defines it.
 *
 * Carry does not own this shape and must not extend it locally -- a field
 * invented here is a field the importer drops. The runner writes each set
 * with its identity attached and `weight` null; Carry fills in the numbers
 * and writes the same file back.
 *
 * [ExerciseSet.variant] is deliberately an opaque [JsonElement]. It carries
 * the variant words the coach matches on, and Carry has no business
 * reconstructing them -- it only has to hand them back byte-for-byte.
 * That is the "never invent an exercise's identity" rule, enforced by the
 * type rather than by care. One key is read, never written: `implement`,
 * to tell a no-weight row from a weighted one ([ExerciseSet.offersMod]),
 * because `suggested_weight` is null on most weighted rows too.
 */
@Serializable
data class Session(
    /**
     * Not the gate: [SessionFiles.parse] decides a file is ours from the
     * raw JSON ([SessionFiles.labelOf]) before this is decoded, so this
     * default cannot vouch for a file with no label. It is here so that
     * anything encoding a [Session] writes the label.
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
     * instead made EVERY one of the owner's lifts look distinct when a pair
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
    /**
     * A ladder's rungs as the board wrote them, `[8, 6, 4]`, or null for one
     * round. From the coach, so Carry never picks a ladder out of
     * [prescribedText]. Kept raw and read by [roundCount]: a value of the
     * wrong shape costs the hint, not the whole row.
     */
    val rounds: JsonElement? = null,
    /** What the coach said to try, verbatim, e.g. "try 45 lb (up from 40, held 3x)". */
    val suggested: String? = null,
    /** Parsed out by the runner so the screen never has to read prose. */
    @SerialName("suggested_weight") val suggestedWeight: Double? = null,
    @SerialName("previous_weight") val previousWeight: Double? = null,
    /**
     * The nearest thing the coach could find when this movement has no
     * history of its own: "goblet forward lunge 30 lb, 10/10".
     *
     * Verbatim, and shown verbatim. It names the OTHER movement it came
     * from, and that provenance is the whole point -- "~30" on its own
     * reads as a suggestion, and a wrong number is worse than no number.
     * Carry must not strip it down to the figure.
     */
    val related: String? = null,
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
    /**
     * The board asked for seconds, not reps, so the row is offered `done`.
     *
     * Any positive duration counts, even one outside the range
     * [prescription] will print: the runner marked the row timed, and for a
     * hold that takes no weight `done` is the only record it can carry.
     * Withholding the chip over a nonsense number lost that record; showing
     * an extra chip on a row that also has boxes costs nothing.
     */
    val timed: Boolean get() = prescribedDurationSec?.let { it > 0 } == true

    /** How many rounds this one row stands for, or null for a single round or
     *  a [rounds] that isn't two or more counts a set could have. */
    val roundCount: Int?
        get() {
            val rungs = rounds as? JsonArray ?: return null
            if (rungs.size !in 2..MAX_ROUNDS) return null
            val counts = rungs.all { r -> (r as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull in 1..MAX_REPS }
            return rungs.size.takeIf { counts }
        }

    /** The variant's `implement` ("bodyweight", "double_dumbbell"...), or
     *  null when the variant is not an object or the key is missing or not
     *  a string. */
    val implement: String?
        get() = (variant as? JsonObject)?.string("implement")

    /**
     * Whether the After page offers *mod* ("I did the easier version"),
     * whatever is typed: on the coach's no-weight implements, and never on
     * a dumbbell or kettlebell row. Without an implement it falls back to
     * the runner giving no weight, which is the best this file says.
     */
    val offersMod: Boolean
        get() = implement?.let { it in NO_WEIGHT_IMPLEMENTS } ?: (suggestedWeight == null)

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
        get() = duration?.let { "$it of" } ?: prescribedText?.asScheme()?.let { "$it x" }

    /**
     * [prescription] as the Before sheet shows it beside the name: "20 total",
     * "45 sec", and "12 reps" for a bare count, which next to a weight would
     * otherwise read as a second weight. The trailing "x" / "of" only reads
     * as a sentence leading into the name, as it does on the Log row.
     */
    val sheetPrescription: String?
        get() = duration ?: prescribedText?.asScheme()
            // "0 reps" is a writer's slip, not an ask, and this is on every row.
            ?.takeIf { s -> s.any { it in '1'..'9' } }
            ?.let { if (it.last().isDigit()) "$it reps" else it }

    private val duration: String?
        get() = prescribedDurationSec?.takeIf { it in 1..MAX_DURATION_SEC }?.let(::durationText)

    /**
     * The variant's words that make this row differ from the plain move,
     * "1 leg", "bosu", in a fixed order, for the Before sheet: they are what
     * explains a weight lighter than the owner expects.
     *
     * Read from the coach's own [variant] fields, never from [line], and only
     * where a field differs from the coach's default for it. A word the name
     * already says is left out, so "single-arm row" does not also get "1 arm".
     */
    val variantWords: List<String>
        get() {
            val fields = variant as? JsonObject ?: return emptyList()
            val name = " " + displayName.asWords() + " "
            return VARIANT_FIELDS.mapNotNull { field ->
                val value = fields.string(field.key)?.trim()
                    ?.takeIf { it != field.default && VARIANT_VALUE.matches(it) }
                    ?: return@mapNotNull null
                val word = field.words[value] ?: VariantWord(value.asWords())
                // From the start of a word, so "paused" and "lunges" count too.
                word.shown.takeIf { word.said.none { " ${it.asWords()}" in name } }
            }
        }
}

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Lower case, with hyphens, underscores and dots as single spaces. */
private fun String.asWords(): String = lowercase().replace(NOT_A_WORD, " ").trim()

/** How a variant value reads on the sheet, and the phrases that mean the
 *  name already says it. */
private class VariantWord(val shown: String, val said: List<String> = listOf(shown))

/** A variant field the Before sheet names, with the coach's default for it. */
private class VariantField(val key: String, val default: String, val words: Map<String, VariantWord> = emptyMap())

/**
 * The fields the Before sheet names, with gym-coach's defaults
 * (`variants._DEFAULTS`). A value not listed in `words` is shown as stored,
 * underscores as spaces.
 *
 * Not shown: `implement` (which dumbbell is the figure's job), `mode`,
 * `intensity` and `grip`, which are not among the ones the owner approved
 * for this line (#213).
 */
private val VARIANT_FIELDS = listOf(
    VariantField(
        "stance", "both", mapOf(
            "single" to VariantWord("1 leg", listOf("1 leg", "single leg", "one leg")),
            // Every lunge is split stance; the coach leaves it unsaid too.
            "split" to VariantWord("split stance", listOf("split", "lunge")),
            "staggered" to VariantWord("staggered stance", listOf("staggered")),
            "sumo" to VariantWord("sumo stance", listOf("sumo")),
            "wide" to VariantWord("wide stance"),
        ),
    ),
    VariantField("arms", "both", mapOf("single" to VariantWord("1 arm", listOf("1 arm", "single arm", "one arm", "singlearm")))),
    VariantField("stability", "stable"),
    VariantField("position", "none"),
    VariantField("tempo", "normal"),
    VariantField("difficulty", "none"),
)

/**
 * The coach's variant values are a word or two of letters ("bosu",
 * "low_plank"). Anything else came from some other writer: digits would
 * read as a rep count beside the prescription, a line break would hide the
 * rest of the row, and a long value would push the prescription off it.
 */
private val VARIANT_VALUE = Regex("""\p{L}[\p{L}_ -]{0,23}""")

private val NOT_A_WORD = Regex("""[^\p{L}\p{N}]+""")

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

/** The importer's own ceiling on a rep count, and a ladder longer than any board writes. */
private const val MAX_REPS = 1000
private const val MAX_ROUNDS = 20

/** "45 sec", "1 min" — the way the sheet writes it. */
internal fun durationText(seconds: Int): String =
    if (seconds % 60 == 0 && seconds >= 60) "${seconds / 60} min" else "$seconds sec"

/**
 * A rep scheme, or null if it does not look like one.
 *
 * `prescribed_text` is free text from the same file, and the same writer,
 * as `line` and `suggested` — and `suggested` holds "try 45 lb (up from 40,
 * held 3x)". This field is both shown to the owner and published, so it is
 * the one place a runner revision could put a weight in a post the owner
 * reads as "the movements only". A scheme is digits, separators and the words
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
    /** As [SessionPatch.Entry.fractionalReps]: reps typed as 8.5, held so
     *  the save is refused rather than writing null under a box showing it. */
    val fractionalReps: Double? = null,
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
        name.isBlank() && weight == null && reps == null && fractionalReps == null &&
            !easy && !hard && !easier && !hurt && note.isNullOrBlank()

    companion object {
        /** Short, because it goes in the file next to a hand-typed name
         *  and the owner may read it. Collisions do not corrupt anything
         *  — two rows sharing an id are interchangeable to every caller
         *  — so there is no reason to spend 36 characters on one. */
        fun newId(): String = java.util.UUID.randomUUID().toString().take(8)
    }
}

/** gym-coach's `NO_WEIGHT_IMPLEMENTS` (gym_lib.py), kept the same. */
internal val NO_WEIGHT_IMPLEMENTS = setOf("bodyweight", "band", "trx")

/**
 * The added rows after a tap on Add: one more blank row, unless the last
 * one is still blank. A second tap then means "take me to it" rather than
 * a second empty row the owner has to find, which is what a tap that
 * seemed to do nothing used to produce.
 */
fun List<AddedSet>.withRowAdded(): List<AddedSet> =
    if (lastOrNull()?.blank == true) this else this + AddedSet(id = AddedSet.newId())
