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
                easy || hard || easier || done || hurt || !note.isNullOrBlank() ||
                // An ADDED row's name is itself the answer: the sheet never
                // listed it, so typing "ybell deadlift" and nothing else is
                // a record that it happened. A sheet row's name is the
                // prescription rather than a reply, which is why this is
                // gated on `added` -- without the gate every blank row on
                // every sheet would count as filled in.
                (added && name.isNotBlank())

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
        /**
         * The set count [movements] was counted over -- NOT [sets], which
         * also counts hand-typed rows that [movements] deliberately
         * excludes. The band prints one over the other in a sentence, and
         * with [sets] as the denominator a borrowed day logged by hand read
         * "1 different movements in 5 sets": a wrong number on the one
         * screen whose job is explaining why the numbers are thin.
         */
        val movementSets: Int?,
        /**
         * Sessions left out of [sessions], [sets], [from], [to], [peaks],
         * [days], [movements] and [repeated] because their file did not
         * read cleanly.
         *
         * NOT [nearBump], which is taken from the newest sheet whether it
         * read SHORT or not — dropping a short day there would not leave
         * a gap, it would promote yesterday's sheet into the answer to
         * "where do I stand today". The band is deliberately the one
         * figure here that is about today rather than about the record,
         * so the sentence on screen says "these totals" rather than
         * "here".
         *
         * A newest sheet that will not parse at all is a third case and
         * [newestRead] carries it: there is nothing to take a streak
         * from, so the band is withheld rather than quietly becoming an
         * older day's.
         *
         * They are left out of the rest rather than counted short because
         * those figures are CLAIMS: "4 lifts, heaviest 50" over a day
         * whose heaviest row did not decode is a confident wrong number
         * and nothing downstream could tell it from a right one. The
         * Before and After tabs can do better — they show the rows that
         * read and say how many did not — because a sheet is a list
         * rather than a total.
         *
         * But excluding them SILENTLY is the thing this screen exists not
         * to do. It is the screen that explains why the numbers are thin,
         * so a session missing from it without a word is the same failure
         * as a confident wrong total, one step quieter.
         *
         * Counted in SESSIONS, not days: two classes on one date are two
         * files and two sessions, which is the word the rest of this
         * screen uses.
         */
        val short: Int = 0,
        /**
         * Sessions written by a coach newer than this build, counted
         * apart from [short] because they are not damaged.
         *
         * The Before tab already says "Carry is out of date ... update
         * Carry" about such a file. Folding them into [short] had this
         * screen calling the same intact file broken, which is worse than
         * either message alone — and unlike the rest of [short] it does
         * not settle on its own.
         */
        val newerSchema: Int = 0,
        /**
         * Whether anything at the newest date read, so [nearBump] can be
         * withheld rather than taken from an older day.
         *
         * Separate from [short] and [newerSchema], which are both about
         * the TOTALS. This one is about the single figure on this screen
         * that is a statement about now, and the screen says it where the
         * band would have been rather than in the line above.
         */
        val newestRead: NewestRead = NewestRead.FINE,
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

    /** How the newest day's sheets read, which this function cannot work
     *  out for itself -- see [of]'s `newestRead` parameter. */
    enum class NewestRead {
        FINE,
        /** Nothing at the newest date would parse. */
        DAMAGED,
        /** ...and the reason is a schema this build does not know, which
         *  is a different thing to tell someone: the file is intact and
         *  the app is the thing that is out of date. */
        NEWER_SCHEMA,
    }

    /**
     * @param files the raw text of every session file in the folder, in any
     *   order. Unreadable ones are skipped rather than failing the screen:
     *   a half-synced file is normal in a shared folder, and one bad file
     *   must not cost the owner the whole picture.
     *
     * @param newestRead whether anything at the newest DATE parsed, and
     *   if not, why. Passed in because this function only ever sees text:
     *   it cannot tell a newest sheet that failed to parse from a folder
     *   that simply ends earlier -- they are the same input -- and that
     *   difference decides whether [Since.nearBump] is an answer about
     *   now or an older day's answer wearing today's label.
     */
    fun of(files: List<String>, newestRead: NewestRead = NewestRead.FINE): Since {
        // Paired with the text it was parsed from, and with its rows, because
        // the rows the owner ADDED are not part of [Session] — a row with no
        // `name` key threw during deserialization once and cost the whole
        // day, so they are read out of the raw JSON by [SessionPatch]. On a
        // borrowed day those rows are the entire session.
        val read = files.mapNotNull { text ->
            SessionFiles.parse(text)?.let { Read(it, text, rowsOf(it, text)) }
        }
        // A file that read SHORT is left out of the HISTORY, as it was
        // when a bad row cost the whole file. The Before and After tabs
        // gain from showing what they can and saying what they could not;
        // the figures here do not, because they are claims -- "4 lifts,
        // heaviest 50" over a day whose heaviest row did not decode is a
        // confident wrong number and nothing downstream could tell.
        // Omitting the day is what this screen already did, and a missing
        // figure beats a wrong one. Saying so instead is on the backlog.
        val parsed = read.filter { SessionFiles.unreadableRows(it.text, it.session) == 0 }
        val sessions = parsed.map { it.session }
        val lifts = parsed.flatMap(::liftsOf)
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
        // Sheet rows AND added rows. The owner's call, 2026-10-16: "Yes if
        // the 2G was incomplete/completely off or I left a class early
        // after only doing the row session, it should still count." A
        // borrowed day whose printed sheet was for the other block, and
        // whose real content is four rows typed by hand, is a day they
        // trained -- it used to reach the history as nothing at all.
        val wasDone = { r: Read -> r.rows.any { it.answered } }
        val logged = parsed.filter(wasDone)
        val dates = logged.map { it.session.date }.filter { it.isNotBlank() }.sorted()

        // Sessions missing from the totals because their file did not read
        // cleanly. Two sources, one sentence, because to the owner they
        // are the same thing: a file that would not parse AT ALL, and one
        // that read short AND has evidence a session happened.
        //
        // That second test is `wasDone`, the same one `logged` uses, and it
        // is the difference between reporting a loss and inventing one. A
        // short sheet with no answered row among the rows that DID read
        // would not have been counted even undamaged -- eleven blank rows
        // from an early test is a real file in the real folder -- so
        // calling it a casualty asserts something the data cannot support,
        // on the one screen whose job is explaining thin numbers. What
        // cannot be known either way is whether the row that did NOT read
        // was the answered one; the quiet choice is the right one there.
        val unparsed = files.filterNot { it in read.map(Read::text) }
        val short = unparsed.count { !SessionFiles.looksLikeNewerSchema(it) } +
            read.count {
                SessionFiles.unreadableRows(it.text, it.session) > 0 &&
                    // ...or NO row read at all, which is not the same thing
                    // as no evidence. The blank-sheet scar `wasDone` guards
                    // against was eleven rows that read FINE; a file where
                    // every row failed carries no evidence either way, and
                    // treating "we could not look" as "nothing to see"
                    // reproduces exactly the silence this count exists to
                    // break -- on a well-formed session file whose date and
                    // class tag the app can read perfectly well.
                    (wasDone(it) || it.session.sets.isEmpty())
            }
        // A newer schema is not damage, and saying so matters because the
        // Before tab says the opposite about the same file: "Carry is out
        // of date ... update Carry". One screen telling the owner their
        // sheets are broken while the other tells them to update the app
        // is worse than either message alone, and this is the one case in
        // this group that is PERSISTENT rather than a sync settling.
        val newerSchema = unparsed.count(SessionFiles::looksLikeNewerSchema)

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
        //
        // From the UNFILTERED read, unlike everything above. Dropping a
        // short day here would not leave a gap, it would silently promote
        // yesterday's sheet into the answer to "where do I stand today" --
        // the exact substitution these three lines exist to prevent. The
        // rows that did decode carry the runner's own counts, so a short
        // newest sheet gives a shorter band rather than a wrong one.
        //
        // A newest sheet that will not parse AT ALL is not in `read`, so
        // the same substitution happens by the back door -- and silently,
        // because an older day's streak is a perfectly well-formed number.
        // There is nothing to compute from, so the band is WITHHELD and
        // the screen says why. A blank band is a missing answer; an older
        // day's band under the words "part way to a bump" is a wrong one.
        val newest = read.map { it.session }
            .takeIf { newestRead == NewestRead.FINE }?.maxByOrNull { it.date }
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
        // SHEET rows only, unlike `lifts` above, and deliberately. This
        // feeds `movements`/`repeated`, which are grouped by the runner's
        // own key -- and an added row has none, by design. Including them
        // would trip the withholding guard below and hide the count
        // entirely on any day with one hand-typed weight, which is a
        // worse answer than a count that says "among the movements the
        // runner named".
        val lifted = logged.flatMap { read -> read.session.sets.filter { !it.skipped && (it.weight ?: 0.0) > 0.0 } }
        val counts = lifted.mapNotNull { it.key }
            .takeIf { it.size == lifted.size }
            ?.groupingBy { it }?.eachCount()

        val days = logged.map { read ->
            val (session, text, rows) = read
            // This session's own lifts, not every lift sharing its date.
            // Two classes in one day is the case `sheet_id` exists for, and
            // the count and the heaviest now sit above that one session's
            // rows: summing across the date put the other class's heaviest
            // in a header with no such weight in the list under it.
            val mine = liftsOf(read)
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
            movementSets = counts?.let { lifted.size },
            short = short,
            newerSchema = newerSchema,
            newestRead = newestRead,
        )
    }


    /**
     * One session's weighted sets, from the ROWS rather than from
     * `session.sets` -- the rows already hold the sheet's and the owner's
     * own, with the same names and families.
     *
     * A blank row is not a zero, and `weight: 0` in the oldest records
     * meant both "bodyweight" and "skipped" — neither is a lift.
     *
     * Reading only `sets` meant a day logged entirely by hand rendered
     * "0 lifts" above a list with weights in it. An added row carries no
     * family, deliberately (Carry never invents an exercise's identity),
     * so it counts toward the day's lifts and its heaviest but never
     * reaches [Since.peaks], which is grouped by family.
     *
     * A weighted row with no name is left out: [Day.best] names the day on
     * this screen and reads aloud as "heaviest <name>, <n> pounds", so a
     * nameless row does not merely add itself, it DISPLACES the named lift
     * that would otherwise have described the day. The Log editor shows
     * such a row on purpose (the save refuses it, and a refusal the owner
     * cannot see is one they cannot act on), which is exactly why it must
     * not also become history.
     */
    private fun liftsOf(read: Read): List<Lift> =
        read.rows.filter { !it.skipped && it.name.isNotBlank() && (it.weight ?: 0.0) > 0.0 }
            .map { Lift(read.session.date, it.name, it.family, it.weight!!, it.reps) }

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
