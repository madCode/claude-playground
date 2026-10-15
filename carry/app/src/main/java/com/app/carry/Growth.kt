package com.app.carry

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What the folder adds up to.
 *
 * The folder is the history: one file per session, written once by the
 * runner and completed once here, plus back-filled records for the days
 * logged before this app existed. Nothing keeps a separate rolling total,
 * so there is no second copy to drift from the first — the sum is computed
 * from the files every time it is asked for.
 *
 * **It derives, it never coaches.** Totals, maxima and counts are facts
 * about rows that are already in the files. A streak is not: it comes from
 * a rule about what earns a bump, that rule lives in the runner, and a
 * second implementation here would eventually disagree with it about
 * someone's training. So [Since.nearBump] carries the runner's own
 * `streak_done`/`streak_needed` through and computes nothing.
 */
object Growth {

    /** One weighted set that actually happened. */
    data class Lift(
        val date: String,
        val name: String,
        val family: String?,
        val weight: Double,
        val reps: Int?,
    )

    /** The heaviest thing in a family, and the weights leading to it. */
    data class Peak(val family: String, val best: Lift, val trail: List<Double>)

    /**
     * One row of a past session, as the owner left it.
     *
     * A record, not a form: there are no edit hooks on it, and the only
     * session Carry can write is today's, on the Log screen. Two ways to
     * write the same file is how a wrong number gets in.
     *
     * Blank fields stay null. Nothing here fills one in or reads a blank
     * as a zero or a skip — the distinction is the whole point of the
     * screen that shows it.
     */
    data class Row(
        val name: String,
        val family: String?,
        /** "10 total x", "45 sec of" — what the board asked for, in its
         *  own words. Kept out of [name] so it stays out of the row's
         *  TalkBack label, which names the movement. */
        val prescription: String?,
        val weight: Double?,
        val reps: Int?,
        val easy: Boolean = false,
        val hard: Boolean = false,
        val easier: Boolean = false,
        val done: Boolean = false,
        val hurt: Boolean = false,
        val skipped: Boolean = false,
        val note: String? = null,
        /** The owner typed this row themselves; the sheet never listed it.
         *  On a borrowed day these ARE the session. */
        val added: Boolean = false,
    ) {
        /**
         * Anything at all was recorded against this row.
         *
         * Wider than [ExerciseSet.answered], which asks whether there is a
         * number — because for the third of this owner's sets that carry no
         * weight, the marker IS the record. *mod* is the only progression
         * axis a bodyweight movement has, so a plank with nothing but
         * `easier` on it is a row they answered, and calling it blank tells
         * them they skipped the one thing they did say.
         *
         * A row with none of this is blank, and is shown blank.
         */
        val answered: Boolean get() =
            weight != null || reps != null || skipped ||
                easy || hard || easier || done || hurt || !note.isNullOrBlank()

        /** The feel markers that are set, in the order the Log screen
         *  offers them, so the two screens read the same way round. */
        val marks: List<String> get() = buildList {
            if (easy) add("easy")
            if (hard) add("hard")
            if (easier) add("mod")
            if (done) add("done")
            if (hurt) add("hurt")
            if (skipped) add("skip")
        }
    }

    /** One day, as it would be read back months later. */
    data class Day(
        /** The session's own name for itself, and the only stable handle on
         *  it: the list's order changes as files arrive, and two classes on
         *  one day share a date. What the detail view is opened by. */
        val sheetId: String,
        val date: String,
        val classTag: String?,
        val lifts: Int,
        /** The heaviest thing done that day, which is what a person
         *  remembers a session by. Null on a day of only bodyweight. */
        val best: Lift?,
        /** A record of a day logged before this app existed, rather than a
         *  sheet it filled in. Shown as one, and never offered as a form. */
        val backfilled: Boolean = false,
        /**
         * Every row of that day's sheet in sheet order — blank ones
         * included, because "what did I lift on the 27th" is partly
         * answered by which lines have nothing on them — and then the rows
         * the owner added.
         *
         * The added half is whatever [SessionPatch.addedIn] shows, which
         * is not quite all of them: a row holding nothing is dropped (it
         * has nothing to show), and so is one too big or too deep for the
         * Log screen's draft to carry. Those caps belong to the editor
         * rather than to this screen, but a second definition of "the rows
         * the owner added" is the two-implementations-drift failure this
         * project has already paid for once, so there is one.
         *
         * Carried here rather than re-read by document id when the detail
         * view opens. The parse has already happened, on the IO thread
         * that read the folder; a second read would add a loading state, a
         * missing-file state and an SAF round trip to a screen whose data
         * is already in hand. A session is a few KB.
         */
        val rows: List<Row> = emptyList(),
    )

    /** A row the runner says is close to earning more weight. */
    data class Bump(val name: String, val family: String?, val weight: Double, val done: Int, val needed: Int)

    data class Since(
        val sessions: Int,
        val sets: Int,
        val from: String?,
        val to: String?,
        val peaks: List<Peak>,
        val nearBump: List<Bump>,
        /** Newest first: "what did I do on the 27th" is a question about a
         *  day, and the screen has no answer for it without this. */
        val days: List<Day>,
        /**
         * Distinct movements, and how many have been done more than once
         * at the same variant. The second is why the coach is so often
         * quiet, and showing the first without it would read as an
         * accusation.
         *
         * Null when any lifted set came from a file with no `key` on it.
         * The runner decides what counts as the same exercise; a count
         * assembled where that is unknown would be a confident wrong
         * number on the one screen whose job is to explain why the
         * numbers are thin.
         */
        val movements: Int?,
        val repeated: Int?,
    ) {
        /**
         * There is nothing to show at all.
         *
         * Not `sets == 0`. [sets] counts WEIGHTED lifts, and a class can be
         * entirely real with none of them — a bodyweight block, a
         * 45-second hold ticked off with `done`. The screen returns early
         * on this, so asking the wrong question hid the whole of it,
         * *every session* band included, from exactly the sessions that
         * have the least else to show for themselves.
         */
        val empty: Boolean get() = days.isEmpty()

        /**
         * Whether the "why the coach is often quiet" band still has a job.
         *
         * It exists because almost nothing repeats, so the streak rule
         * almost never fires and the sheets look empty. That is worth
         * explaining while it is true. Once a reasonable share of
         * movements have come round again the quiet explains itself, and a
         * band still saying it would be telling the owner off for variety
         * the studio chose for them. So it retires on its own rather than
         * on anybody remembering to take it out.
         */
        val explainsTheQuiet: Boolean
            get() {
                val seen = movements ?: return false
                val again = repeated ?: return false
                return seen >= MIN_TO_EXPLAIN && again * 100 < seen * REPEAT_PERCENT
            }
    }

    /**
     * One file, read once: what [Session] could parse, the bytes it came
     * from, and the rows the screen shows.
     *
     * All three because they answer different questions and two of them
     * need the raw text — `added` rows and the `backfilled` flag are both
     * outside [Session] — and because deciding whether anyone filled the
     * sheet in means looking at the same rows the screen does.
     */
    private data class Read(val session: Session, val text: String, val rows: List<Row>)

    /**
     * @param files the raw text of every session file in the folder, in any
     *   order. Unreadable ones are skipped rather than failing the screen:
     *   a half-synced file is normal in a shared folder, and one bad file
     *   must not cost the owner the whole picture.
     */
    fun of(files: List<String>): Since {
        // Paired with the text it was parsed from, and with its rows, because
        // the rows the owner ADDED are not part of [Session] — a row with no
        // `name` key threw during deserialization once and cost the whole
        // day, so they are read out of the raw JSON by [SessionPatch]. On a
        // borrowed day those rows are the entire session.
        val parsed = files.mapNotNull { text ->
            SessionFiles.parse(text)?.let { Read(it, text, rowsOf(it, text)) }
        }
        val sessions = parsed.map { it.session }
        val lifts = sessions.flatMap(::liftsOf)
        // A sheet nobody filled in is not a session they did. One of those
        // was sitting in the real folder — eleven blank rows from an early
        // test — and it counted as a session AND contributed eleven
        // movements to the very figure that explains why the coach is
        // quiet. A stat that counts what was never done is worse than none.
        //
        // But the test is [Row.answered], not "a number was entered", and
        // the two are not the same thing: a class where every row was
        // bodyweight and the only record is `mod`, or a 45-second hold
        // ticked off with `done`, is a class the owner DID. Asking for a
        // number threw away the whole day, not just the row — which is the
        // same mistake as calling a marked row blank, one layer up.
        //
        // Sheet rows only, deliberately. Whether a session logged ENTIRELY
        // by hand belongs on this screen is a separate, open question — it
        // moves the aggregates rather than fixing a wrong one — and
        // answering it by accident here is how a decision gets made without
        // anyone deciding it.
        val logged = parsed.filter { read -> read.rows.any { !it.added && it.answered } }
        val dates = logged.map { it.session.date }.filter { it.isNotBlank() }.sorted()

        val peaks = lifts.filter { it.family != null }
            .groupBy { it.family!! }
            .map { (family, inFamily) ->
                Peak(
                    family = family,
                    best = inFamily.maxBy { it.weight },
                    // Oldest to newest, so the bar on the right is now.
                    trail = inFamily.sortedBy { it.date }.map { it.weight }.takeLast(MAX_TRAIL),
                )
            }
            .sortedByDescending { it.best.weight }

        // Only from the newest sheet: a streak is a statement about where
        // the owner stands TODAY, and an older file's is a fact about a
        // day that has already been and gone.
        val newest = sessions.maxByOrNull { it.date }
        val nearBump = newest?.sets.orEmpty().mapNotNull { set ->
            // A row they said they did not do, the same exclusion `lifted`
            // and `liftsOf` make below. The streak behind it is real, but
            // the band is capped now, so a movement declined today would
            // take a slot from one they actually worked on.
            if (set.skipped) return@mapNotNull null
            val needed = set.streakNeeded ?: return@mapNotNull null
            val done = set.streakDone ?: 0
            val weight = set.suggestedWeight ?: return@mapNotNull null
            // The count was written before the class and the owner has
            // since filled the row in, so these two say the streak is
            // about to break: the runner's own rules are that `hard`
            // blocks the next bump, and a lighter set is not a set at
            // this weight. Either way "part way to a bump" is no longer
            // true of the row, and the band is capped, so it would be
            // taking a slot from one that is.
            //
            // Logged AT the weight is left alone. That count is stale by
            // one and understates, which is the safe direction -- and
            // re-deriving it here is the one thing this file must not do.
            if (set.hard || (set.weight != null && set.weight < weight)) return@mapNotNull null
            if (done <= 0 || done >= needed) null
            else Bump(set.displayName, set.family, weight, done, needed)
            // How many sets away, not how far along. The row's own line is
            // "another clean set moves it", and under a fraction a row at
            // 8 of 10 (two sets away) outranks one at 2 of 3 (one set
            // away) -- so with the band capped, the one movement a single
            // set would bump could be the one cut. Not reachable while
            // every `needed` is 3; the runner's rule is free to differ.
            //
            // Ties keep the sheet's own order: `sortedWith` is stable, and
            // the input is the sheet's rows in file order. Most sheets are
            // one rung all the way down, so this is usually not a ranking
            // at all -- see the cap's own note.
        }.sortedWith(compareBy({ it.needed - it.done }, { -(it.done.toDouble() / it.needed) }))

        // Over what was actually LIFTED, not over every row on every sheet.
        // Grouped by the runner's own key, or not at all: all of them carry
        // one or the count is withheld.
        val lifted = logged.flatMap { read -> read.session.sets.filter { !it.skipped && (it.weight ?: 0.0) > 0.0 } }
        val counts = lifted.mapNotNull { it.key }
            .takeIf { it.size == lifted.size }
            ?.groupingBy { it }?.eachCount()

        val days = logged.map { (session, text, rows) ->
            // This session's own lifts, not every lift sharing its date.
            // Two classes in one day is the case `sheet_id` exists for, and
            // the count and the heaviest now sit above that one session's
            // rows: summing across the date put the other class's heaviest
            // in a header with no such weight in the list under it.
            val mine = liftsOf(session)
            Day(
                sheetId = session.sheetId,
                date = session.date,
                classTag = session.classTag,
                lifts = mine.size,
                best = mine.maxByOrNull { it.weight },
                backfilled = backfilledIn(text),
                rows = rows,
            )
        }.sortedByDescending { it.date }

        return Since(
            sessions = logged.size,
            sets = lifts.size,
            from = dates.firstOrNull(),
            to = dates.lastOrNull(),
            peaks = peaks,
            nearBump = nearBump,
            days = days,
            movements = counts?.size,
            repeated = counts?.count { it.value > 1 },
        )
    }


    /** One session's weighted sets. A blank row is not a zero, and
     *  `weight: 0` in the oldest records meant both "bodyweight" and
     *  "skipped" — neither is a lift. */
    private fun liftsOf(session: Session): List<Lift> =
        session.sets.filter { !it.skipped && (it.weight ?: 0.0) > 0.0 }
            .map { Lift(session.date, it.displayName, it.family, it.weight!!, it.reps) }

    /**
     * One day's sheet, row by row, for the screen that reads it back.
     *
     * Sheet order, then the rows the owner added — the order the Log
     * screen offered them in on a normal day, so a session reads the same
     * way months later as it did when it was filled in.
     */
    private fun rowsOf(session: Session, text: String): List<Row> =
        session.sets.map { set ->
            Row(
                name = set.displayName,
                family = set.family,
                prescription = set.prescription,
                weight = set.weight,
                reps = set.reps,
                easy = set.easy, hard = set.hard, easier = set.easier,
                done = set.done, hurt = set.hurt, skipped = set.skipped, note = set.note,
            )
        } + SessionPatch.addedIn(text).map { row ->
            Row(
                // Through `readable()`, like a sheet row's name: this one
                // was typed by hand on another machine, and a bidi
                // override in it would make the line render in an order
                // other than the one stored.
                name = row.name.readable(),
                // No family and no prescription, and neither is guessed.
                // An added row is one the sheet never listed, so nothing
                // parsed it and Carry does not invent an identity.
                family = null,
                prescription = null,
                weight = row.weight,
                reps = row.reps,
                easy = row.easy, hard = row.hard, easier = row.easier,
                hurt = row.hurt, note = row.note,
                added = true,
            )
        }

    /**
     * Whether this file is a record of a day logged before Carry existed.
     *
     * Read out of the raw JSON rather than off [Session], for the same
     * reason `added` is: a value the model cannot decode — `null`, a 1, the
     * string "true" — throws during deserialization, and the loader turns
     * that into "Can't read that session", which takes the Before tab's
     * sheet with it. The field is written by whatever back-filled the day,
     * not by the runner and not by Carry, so it is the likeliest in the
     * file to arrive in an unexpected shape. Anything that is not a JSON
     * `true` simply means "not backfilled", which is the safe reading: it
     * costs a line of wording, not a session.
     */
    private fun backfilledIn(text: String): Boolean = runCatching {
        val flag = (Session.json.parseToJsonElement(text) as? JsonObject)?.get("backfilled")
        (flag as? JsonPrimitive)?.takeIf { !it.isString }?.content == "true"
    }.getOrDefault(false)

    /** Below this there is not enough training to say anything about its
     *  shape, and the band would be reading tea leaves. */
    private const val MIN_TO_EXPLAIN = 8

    /** Once this share of movements has come round again, the coach is no
     *  longer mostly quiet and the band has nothing left to explain. */
    private const val REPEAT_PERCENT = 25

    /** Enough bars to show a direction; more is a chart, and six sessions
     *  do not support one. */
    private const val MAX_TRAIL = 8
}
