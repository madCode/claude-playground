package com.app.carry

import java.text.Normalizer

/**
 * The post that tells the subreddit what this class actually did.
 *
 * The coach reads r/orangetheory for the day's floor block, and intel for
 * the owner's class is posted on a small minority of days. That is why most
 * of their logged entries are borrowed from the other class's block: nobody
 * posts theirs. This is the only path that gets one of their sessions into
 * the corpus at all, and it helps whoever is in the later classes.
 *
 * Three rules, and the first two are why this is not simply "print the
 * sheet". A review of the first version found it breaking all three.
 *
 * **It never republishes a block as another class's.** On a borrowed day
 * the sheet IS the other class's block — that is what borrowed means — so
 * printing those lines under this class's heading would fabricate intel,
 * and the coach would scrape it back next week as genuine. The thing this
 * project exists to fix would become self-sustaining and public. So on a
 * borrowed day the post carries movements and no rep counts, and says
 * plainly that it is a report rather than the board.
 *
 * **It publishes nothing of the owner's.** No weights, and no performed
 * reps either: an added row's `reps` is what they managed, not what the
 * class was told to do, so printing "8 x deadlift" would leak a personal
 * number AND invent a prescription. Only [ExerciseSet.prescription] — what
 * the board itself said — is ever a rep count here, and only on a day with
 * real intel.
 *
 * **It is built from fields this app owns.** `canonical` and `components`
 * are parsed out by the runner; `line` is the sheet text verbatim and this
 * app does not own what goes in it. The sibling field `suggested` already
 * holds "try 45 lb (up from 40, held 3x)", so a runner revision folding an
 * annotation into `line` would publish a weight with no change here and no
 * test failing. A row this app cannot name itself is left out.
 */
object IntelPost {

    /**
     * The text to hand over, plus the lines worth a second look before it
     * goes.
     *
     * [suspect] holds bullets whose MOVEMENT NAME carries a digit —
     * "ybell deadlift 26". The screen promises "the movements only, never
     * your weights", and that promise is one this app cannot keep on its
     * own: a name is typed by hand. Rather than strip trailing numbers,
     * which would silently mangle "bicep curl 21s" (a real movement), the
     * preview calls them out where the owner is already deciding whether
     * to send. The prescription is excluded from the test — "10 total x"
     * is digits by design, and flagging on it would mark every line.
     */
    data class Post(val text: String, val suspect: Set<String> = emptySet())

    /**
     * The post to hand over, or null when there is nothing honest to post
     * — an empty form has no post in it, and offering one invites a guess.
     */
    fun compose(
        session: Session,
        entries: Map<Int, SessionPatch.Entry>,
        added: List<AddedSet>,
    ): Post? {
        // The sheet is another class's, so its rep counts are not this
        // class's prescription. Movements only.
        val prescribing = session.borrowedFrom == null

        val did = session.sets.mapNotNull { set ->
            val entry = entries[set.ref] ?: return@mapNotNull null
            // A number, or `done` ON A TIMED ROW. This goes to the
            // subreddit, and the question is "was this movement in the
            // block" — which a number answers, and which `done` ("I did
            // it, and there is nothing to say about it") answers for a
            // timed hold that takes no weight and prescribes no reps.
            // Asked and answered by the owner, 2026-10-16: a timed block
            // used to offer no post at all, because every row was empty by
            // definition.
            //
            // `set.timed` is in the test on purpose and is not
            // belt-and-braces. The `done` chip is drawn only for a timed
            // row (LogScreen), so that is the only shape the owner can set
            // it on — but `done` is ALSO seeded from the raw file, and a
            // runner re-run that drops `prescribed_duration_sec` leaves
            // the flag on a row whose chip has vanished. Without this
            // clause that row publishes a bullet the owner can neither see
            // nor clear, and `prescription` falls back to the rep text, so
            // it reads "10 x plank" — a prescription nobody answered in
            // this sitting.
            //
            // `hurt` deliberately does NOT qualify: tapping it while
            // deciding is not a record that the movement was done. Nor do
            // `easy`/`hard`/`easier`, which do imply a set happened — but
            // the owner named `done`, and widening their answer is a
            // backlog question rather than one to take here. The same
            // question leaves a wholly BODYWEIGHT (untimed) class still
            // unpostable, because such a row has no `done` chip at all.
            val qualifies = entry.weight != null || entry.reps != null || (entry.done && set.timed)
            if (entry.skipped || !qualifies) return@mapNotNull null
            val name = set.ownName() ?: return@mapNotNull null
            // `prescription`, never `prescribedReps`: that one is the
            // runner's math number, so publishing it would tell the
            // subreddit "5 x alt reverse lunge" where the board said "10
            // total" — half the reps, for most rows in the corpus.
            val asked = if (prescribing) set.prescription?.post()?.takeIf(String::isNotEmpty) else null
            Named(name, asked, typed = false)
        }
        // Added rows FIRST: on a borrowed day they are the session, and
        // the cap below must never be what drops them.
        //
        // These are not held to `did`'s "answered with a number" test, and
        // that asymmetry is deliberate: a sheet row was listed by someone
        // else, so a number is what says the owner did it, while an added
        // row exists only because the owner typed its name. Requiring a
        // number here would drop every bodyweight movement from the post
        // on exactly the days when added rows ARE the session.
        val extra = added.mapNotNull {
            it.name.post()?.takeIf(String::isNotEmpty)?.let { n -> Named(n, null, typed = true) }
        }
        // A line that reads the same is one bullet, whoever wrote it: the
        // owner asked for a repeated line to be shown once (2026-10-16).
        // "The same" is the rendered line with case folded, so a typed
        // "10 total x Squat" (a phone keyboard capitalises) matches the
        // sheet's "10 total x squat"; whitespace is already collapsed by
        // `post()`. Nothing looser: "push up" and "pushup", or "10 x" and
        // "12 x", stay two bullets, because only identical words are
        // certainly one movement.
        //
        // Where a typed line meets the sheet's, the SHEET's wins, in the
        // typed one's place: the cap below must never drop an added row's
        // slot, and the typed copy would be flagged for a digit that is
        // the board's own prescription. The save is untouched — both rows
        // stay in the file.
        val all = extra + did
        val sheetByLine = did.distinctBy { it.key }.associateBy { it.key }
        val lines = all.distinctBy { it.key }
            .map { sheetByLine[it.key] ?: it }
            .take(MAX_LINES)
        if (lines.isEmpty()) return null

        val text = buildString {
            append(heading(session, prescribing))
            append("\n\n")
            lines.forEach { append("- ").append(it.line).append('\n') }
            if (!prescribing) append("\n(Reported after the fact — no intel was posted for this class.)\n")
        }
        return Post(text, lines.filter { it.carriesANumber }.map { "- " + it.line }.toSet())
    }

    /** A bullet, split so the digit test can see the name on its own. */
    private data class Named(val name: String, val asked: String?, val typed: Boolean) {
        val line: String get() = asked?.let { "$it $name" } ?: name

        /**
         * What two bullets must share to be one: the line, case folded.
         * NFC first, so an "é" typed as e + accent matches the sheet's
         * single "é" — the same text in two encodings, not two words.
         */
        val key: String get() = Normalizer.normalize(line, Normalizer.Form.NFC).lowercase()

        /**
         * Only a name the OWNER typed can be flagged, because a typed name
         * is the only place a weight can have been entered: a canonical
         * comes from the coach's knowledge base, which holds movements and
         * not loads.
         *
         * The reasoning is NOT "runner names legitimately hold digits, so
         * exempt them" — that was the first version of this note and it is
         * backwards. `bicep curl 21s`, `90/90 hip stretch` and `3 way
         * shoulder` all hold a digit legitimately, and all three are
         * flagged when the owner types one into "+ Add something the sheet
         * missed", which is exactly where such a name gets typed. So the
         * false alarm this gate was meant to avoid is still reachable, on
         * the one path that can reach it. What the gate buys is that it
         * cannot fire on an intel day the owner had no hand in — which is
         * most of them.
         *
         * A false alarm costs the guard its meaning, which is the one
         * thing it cannot afford, so narrowing it is on the backlog as a
         * question rather than a patch.
         */
        val carriesANumber: Boolean get() = typed && name.any(Char::isDigit)
    }

    /** The name the runner parsed, never the raw sheet line. Null when
     *  there is none, which leaves the row out rather than falling back to
     *  text this app does not own. */
    private fun ExerciseSet.ownName(): String? =
        (canonical ?: components?.takeIf { it.isNotEmpty() }?.joinToString(" + "))?.post()?.takeIf(String::isNotEmpty)

    private fun heading(session: Session, prescribing: Boolean): String {
        val who = session.classTag?.post()?.takeIf(String::isNotEmpty)
        val day = SessionFiles.formatDate(session.date).post()
        val what = if (prescribing) "floor block" else "did"
        return listOfNotNull(who?.let { "$it $what" } ?: what.replaceFirstChar(Char::uppercase), day)
            .joinToString(" — ")
    }

    /**
     * One line of plain text, short enough to be read.
     *
     * [String.readable] strips the marks that reorder text on a screen, but
     * a newline is harmless on a screen and not here: this text is one
     * bullet per movement, so a name holding "squat\n- bench press\n- my
     * 1RM is 185" would publish two movements the class never did.
     *
     * It CUTS at the break rather than folding it to a space. Folding
     * stopped the extra bullets and still published the rest of the
     * string, which was the real problem — a movement name is one line,
     * and whatever follows a line break in one is not part of it. The
     * result is clipped too: an unbounded string from a synced file
     * otherwise ends up in another app's intent.
     */
    private fun String.post(): String =
        readable().takeWhile { !it.isISOControl() && it != ' ' && it != ' ' }
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_NAME)

    /** A floor block is a dozen movements. Past this the file is not
     *  describing a workout. */
    private const val MAX_LINES = 30

    /** No exercise name is longer than this, and the text leaves the app. */
    private const val MAX_NAME = 80
}
