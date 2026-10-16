package com.app.carry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What the folder adds up to. Read at home, on the couch, not in a hurry —
 * the opposite of the other two screens, which is why this one may spend
 * space on context they cannot.
 *
 * **It does not open with a chart.** Measured against the owner's real
 * history, where almost every movement appears once and barely any repeat
 * at the same variant: a weight-over-time line would be a row
 * of disconnected dots, and would read as "you are not progressing" when
 * the truth is that the studio almost never repeats a movement. So the
 * screen leads with what is nearly earned, then with the axis the data is
 * actually dense on — what a movement trains, rather than which movement
 * it was — and then says plainly why there is not more. Without that last
 * part every empty row looks like a broken app.
 */
@Composable
fun SinceScreen(
    since: Growth.Since,
    modifier: Modifier = Modifier,
    newerBuild: Int? = null,
    installing: Boolean = false,
    onGetBuild: () -> Unit = {},
) {
    // There is no nav library here and this does not justify adding one:
    // the app is three tabs, and one nullable id inside this screen keeps
    // it that way.
    //
    // Saveable, so the session stays open across a rotation and a process
    // death -- the owner reads this screen at home, where being thrown
    // back to the list for turning the phone is the kind of thing that
    // makes an app feel broken. By the session's OWN id rather than by its
    // place in the list: the folder is re-read on every resume, a sync can
    // drop a file in, and the list reorders under a remembered index.
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    // Hoisted out of the LazyColumn below, which is disposed while a
    // session is open: its own remembered position goes with it, so
    // closing the session landed the owner back at the top of a year of
    // history, above two other bands, every time. Here it outlives the
    // detail view.
    //
    // This one IS an index, unlike the selection above, and a file
    // arriving does shift what it points at. That costs a scroll position,
    // which is the cheapest thing on this screen to get wrong -- the
    // selection is the one that must not drift, because it decides which
    // session is on screen.
    val list = rememberLazyListState()
    val open = opened?.let { id -> since.days.firstOrNull { it.sheetId == id } }
    // A file can stop being there between reads -- a sync replaces rather
    // than edits, and the owner can delete one. Drop the selection in that
    // case rather than hold it: held, the detail view would spring open
    // again over whatever the owner was reading the moment the file came
    // back.
    LaunchedEffect(since.days, opened) {
        if (opened != null && since.days.none { it.sheetId == opened }) opened = null
    }
    if (open != null) {
        PastSession(open, onBack = { opened = null }, modifier = modifier)
        return
    }
    Column(modifier.fillMaxSize().background(Look.Slate).padding(horizontal = 14.dp)) {
        Text(
            if (since.sessions == 1) "1 session" else "${since.sessions} sessions",
            color = Look.Chalk, fontFamily = Look.Name, fontWeight = FontWeight.Bold,
            fontSize = Look.TitleSize, maxLines = 1, modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            span(since), color = Look.ChalkDim, fontFamily = Look.Name,
            fontSize = Look.SmallSize, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp, bottom = 4.dp),
        )

        // At the top and above the empty check: a folder the app can't read is when a newer
        // build is most likely the fix, and the bottom of a long history is where nobody looks.
        if (newerBuild != null) NewerBuild(newerBuild, installing, onGetBuild)

        // ABOVE the empty check, deliberately: if every file in the folder
        // read short there are no days at all, and "Nothing logged yet" on
        // its own would be a flat lie about a folder full of sessions.
        listOfNotNull(shortNote(since.short), newerSchemaNote(since.newerSchema)).forEach {
            Text(
                "\u26a0 $it", color = Look.BrickInk, fontFamily = Look.Name,
                fontSize = Look.SmallSize,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        }
        if (since.empty) {
            Text(
                // Not the never-used-folder copy when the folder is full of
                // sessions that would not read: "this fills in as you save
                // sessions" is advice that cannot work, and it is the
                // sentence that tells the owner what to do next.
                // "Nothing read cleanly" would be a lie whenever one of
                // the files read perfectly and simply had nothing in it --
                // a blank sheet is a real shape, eleven of its rows sat in
                // the real folder once. "Could not be counted" is true of
                // both, and this is the sentence that tells the owner what
                // to do next, so it has to survive the mixed case.
                if (since.short > 0 || since.newerSchema > 0) "Nothing in that folder could be counted."
                else "Nothing logged yet. This fills in as you save sessions.",
                color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.SmallSize,
                modifier = Modifier.padding(top = 14.dp),
            )
            return@Column
        }

        LazyColumn(Modifier.weight(1f), state = list) {
            if (since.nearBump.isNotEmpty() || since.bandCut) {
                // "Close to a bump" was a claim the rows could not
                // support. The runner counts a streak from the first clean
                // set, so a movement done ONCE arrives here at 1 of 3 --
                // and this owner starts nearly every movement fresh,
                // because the studio changes the setup most classes. So
                // most of a real sheet lands here at one pip of three, all
                // on the same rung, under a heading that said they were
                // about to go up.
                //
                // The pips are worth keeping; the word "close" was not --
                // and nor was "each one", which read as every movement
                // trained. Each movement is its newest listing across the
                // sheets, and that listing must be: not skipped, a streak
                // the runner wrote, one strictly in progress, a suggested
                // weight (so a bodyweight movement part way through is
                // dropped); then five of those. A row from an older sheet
                // says its date. This claims nothing but what every row IS.
                item { Band("Part way to a bump") }
                // Fewest sets away first, and capped so the band cannot
                // take the screen from the ones under it. On a sheet where
                // everything is on the same rung -- which is the usual case
                // -- the order is the board's, so the cap says how many are
                // not shown rather than claiming the rest are behind.
                items(since.nearBump.take(MAX_BUMP_ROWS)) { BumpRow(it) }
                if (since.nearBump.size > MAX_BUMP_ROWS) {
                    item { More(since.nearBump.size - MAX_BUMP_ROWS) }
                }
                if (since.bandCut) {
                    item {
                        Text(
                            "A sheet didn't read, so nothing older than it is shown here.",
                            color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.SmallSize,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                }
            } else if (since.newestRead != Growth.NewestRead.FINE) {
                // The heading with nothing under it, which the band never
                // shows otherwise. It is the difference between "nothing
                // is close" and "we cannot tell", and only one of those is
                // true here. Said where the band would have been rather
                // than in the line above the list, because the line above
                // is about the TOTALS and this is the one figure on the
                // screen that is a claim about today.
                item { Band("Part way to a bump") }
                item {
                    Text(
                        // "The newest sheet", not "today's": the newest
                        // file is the newest BY NAME, which on a rest day
                        // is last week's and can be dated ahead. The
                        // Before tab is careful about exactly this, and
                        // was made so because the owner complained about
                        // "a cheat sheet for a past day".
                        //
                        // And a newer schema is not damage -- the file is
                        // intact and the APP is what is out of date,
                        // which is what the other screen says about the
                        // same file.
                        if (since.newestRead == Growth.NewestRead.NEWER_SCHEMA) {
                            "The newest sheet needs a newer Carry. Rather than show an older day's, this is blank."
                        } else {
                            "The newest sheet didn't read. Rather than show an older day's, this is blank."
                        },
                        color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.SmallSize,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }
            if (since.peaks.isNotEmpty()) {
                item { Band("Heaviest, by what it trains") }
                items(since.peaks) { PeakRow(it) }
            }
            if (since.days.isNotEmpty()) {
                item { Band("Every session") }
                items(since.days) { day -> DayRow(day) { opened = day.sheetId } }
            }
            if (since.explainsTheQuiet) item { Quiet(since) }
            item { Version() }
        }
    }
}

private fun span(since: Growth.Since): String {
    val from = since.from?.let(SessionFiles::formatDate)
    val to = since.to?.let(SessionFiles::formatDate)
    val dates = when {
        from == null -> null
        from == to -> from
        else -> "$from – $to"
    }
    // Omitted at zero rather than printed as "0 sets", which is the one
    // count that reads as "nothing happened" — and a bodyweight class, or
    // a hold ticked off with `done`, is a class that happened with no
    // weighted set in it. The bands below say what there was.
    val sets = when {
        since.sets == 0 -> null
        since.sets == 1 -> "1 set"
        else -> "${since.sets} sets"
    }
    return listOfNotNull(dates, sets).joinToString(" · ")
}

@Composable
private fun Band(text: String) {
    Text(
        text.uppercase(), color = Look.ChalkFaint, fontFamily = Look.Figure,
        fontSize = Look.LabelSize, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

/** What the cap left out, said rather than silently dropped: a list that
 *  stops at five looks like a folder with five things in it.
 *
 *  Its own semantics node, because out of its row this reads as a fragment
 *  -- TalkBack lands on "and 6 more" with nothing to attach it to. */
@Composable
private fun More(rest: Int) {
    Text(
        "and $rest more",
        color = Look.ChalkFaint, fontFamily = Look.Name, fontSize = Look.SmallSize,
        modifier = Modifier
            .semantics { contentDescription = "and $rest more, not shown" }
            .padding(top = 8.dp, bottom = 2.dp),
    )
}

/** How a progress row reads out: the pips are the content, and they are
 *  drawn, not written. "pounds" said explicitly, for the same reason the
 *  day rows say it -- the column header the sighted layout relies on is
 *  not read. */
private fun spoken(bump: Growth.Bump): String = listOfNotNull(
    bump.name,
    "${bump.done} of ${bump.needed} clean sets",
    // "next at", not a bare "25 pounds". The two numbers are not about
    // the same thing and this label used to read as though they were:
    // the pips count clean sets, and the weight is what the runner says
    // to use TODAY. On the runner's converted-streak rule those differ --
    // two sets held at 30 for 8 reps earn "2 of 3" beside an estimate of
    // 25 for 12, and nothing was ever held at 25. The sighted row never
    // made that claim; it shows three things side by side. This label
    // joined them into a sentence, which is how it became one.
    "next at ${figure(bump.weight)} pounds",
    bump.asOf?.let { "as of ${asOfText(it)}" },
).joinToString(", ")

/** A [Growth.Bump.asOf] in words: the sheet's date, or "an older sheet"
 *  when its date can't be shown. */
private fun asOfText(asOf: String): String =
    if (asOf == Growth.OLDER_SHEET) "an older sheet" else SessionFiles.formatDate(asOf)

/** The one actionable thing in the data: another clean set moves it. */
@Composable
private fun BumpRow(bump: Growth.Bump) {
    Row(
        Modifier
            .fillMaxWidth()
            // One label for the whole row, as the day rows have. The pips
            // are bare Boxes with no semantics, so unlabelled this row
            // reads as "movement 1" then "21" -- the streak, which is the
            // entire answer to the heading above it, is inaudible, and the
            // weight is a bare number with no unit.
            .semantics(mergeDescendants = true) { contentDescription = spoken(bump) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Look.family(bump.family)?.let { hue ->
            Box(Modifier.width(3.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(hue))
        }
        // The date on a line of its own under the name, so neither cuts the
        // other at large text: it is what says this count may have moved.
        Column(Modifier.weight(1f)) {
            Text(
                bump.name, color = Look.Chalk, fontFamily = Look.Name, fontSize = Look.NameSize,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(),
            )
            bump.asOf?.let {
                Text(
                    "as of ${asOfText(it)}", color = Look.ChalkDim, fontFamily = Look.Name,
                    fontSize = Look.SmallSize, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Past ten, pips cannot tell the truth and are not countable at a
        // glance anyway. Clamping `needed` alone LIES: 12 of 40 draws ten
        // lit pips and no unlit ones -- the picture of a bump already
        // earned -- under a heading that says part way. Say the numbers.
        if (bump.needed > MAX_PIPS) {
            Text(
                "${bump.done} of ${bump.needed}", color = Look.ChalkDim,
                fontFamily = Look.Figure, fontSize = 11.sp, maxLines = 1,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(bump.needed) { i ->
                    Box(
                        Modifier.size(5.dp).clip(RoundedCornerShape(50))
                            .background(if (i < bump.done) Look.AmberDim else Look.RuleStrong)
                    )
                }
            }
        }
        Text(figure(bump.weight), color = Look.Chalk, fontFamily = Look.Figure,
            fontWeight = FontWeight.SemiBold, fontSize = Look.FigureSize)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Look.Rule))
}

/**
 * The heaviest in a family, as a number alone. No chart of the weights
 * before it: a family's sets are different movements at different reps,
 * so bars of them on no scale read as progress they don't show.
 */
@Composable
private fun PeakRow(peak: Growth.Peak) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Look.family(peak.family)?.let { hue ->
            Box(Modifier.width(3.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(hue))
        }
        Text(
            buildAnnotatedString {
                append(peak.family)
                withStyle(SpanStyle(color = Look.ChalkDim)) { append(" · ${peak.best.name}") }
            },
            color = Look.Chalk, fontFamily = Look.Name, fontSize = Look.NameSize,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text(figure(peak.best.weight), color = Look.Chalk, fontFamily = Look.Figure,
            fontWeight = FontWeight.SemiBold, fontSize = Look.FigureSize)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Look.Rule))
}

/**
 * One past session, named by its heaviest lift, and the way into it.
 *
 * "What did I do on the 27th" is a question about a day, and nothing else
 * on this screen can answer it — the bands above are all cut across days.
 * A session is remembered by its hardest thing, so that is what it is
 * labelled with rather than by a count alone. Tapping it opens the day
 * itself, row by row, which is the rest of the answer.
 */
@Composable
private fun DayRow(day: Growth.Day, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            // A row is now a target, not a label. 48dp is Android's
            // minimum and the content came to about 38.
            .heightIn(min = 48.dp)
            .clickable(onClick = onOpen, role = Role.Button)
            // One label for the whole row: unlabelled, TalkBack reads the
            // date, the count and the weight as three separate things and
            // the tap target as a fourth with no name at all.
            .semantics { contentDescription = spoken(day) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Look.family(day.best?.family)?.let { hue ->
            Box(Modifier.width(3.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(hue))
        }
        Text(
            buildAnnotatedString {
                append(listOfNotNull(day.classTag, SessionFiles.formatDate(day.date)).joinToString(" · "))
                day.best?.let {
                    withStyle(SpanStyle(color = Look.ChalkDim)) { append("  ${it.name}") }
                }
            },
            color = Look.Chalk, fontFamily = Look.Name, fontSize = Look.NameSize,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text(
            if (day.lifts == 1) "1 lift" else "${day.lifts} lifts",
            color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.SmallSize,
        )
        day.best?.let {
            Text(figure(it.weight), color = Look.Chalk, fontFamily = Look.Figure,
                fontWeight = FontWeight.SemiBold, fontSize = Look.FigureSize)
        }
        // Says the row goes somewhere. Without it nothing distinguishes
        // these rows from the two bands above, which do not open anything,
        // and a tap target nobody knows about is one nobody uses.
        Text(
            "›", color = Look.ChalkFaint, fontFamily = Look.Name,
            fontSize = Look.NameSize, maxLines = 1,
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Look.Rule))
}

/** The whole row as one sentence, with the units the layout leaves to the
 *  column a figure sits in. */
private fun spoken(day: Growth.Day): String = listOfNotNull(
    day.classTag,
    SessionFiles.formatDate(day.date),
    if (day.lifts == 1) "1 lift" else "${day.lifts} lifts",
    day.best?.let { "heaviest ${it.name}, ${it.weight.asWeight()} pounds" },
).joinToString(", ")

/**
 * Why there is not more here.
 *
 * Load-bearing, not an apology — the owner has seen rows with no number on
 * them all week, and without this they would reasonably conclude the app
 * is broken rather than that the studio never repeats a movement.
 *
 * But it earns its place only while it is still explaining something, so
 * it retires itself: see [Growth.Since.explainsTheQuiet]. Once enough
 * movements come round again the quiet stops needing an explanation, and
 * a band that stayed would be telling the owner off for variety that
 * isn't theirs to choose.
 */
@Composable
private fun Quiet(since: Growth.Since) {
    // Withheld rather than guessed. These counts turn on what the runner
    // calls the same exercise, and on files that predate it saying so
    // there is no honest number — on the one band whose whole job is to
    // explain why the numbers are thin, a wrong one would be worse than
    // none.
    val movements = since.movements ?: return
    val repeated = since.repeated ?: return
    // Not since.sets: that counts hand-typed rows this movement count
    // deliberately excludes, so the two numbers in one sentence came from
    // different populations.
    val inSets = since.movementSets ?: return
    Column(Modifier.padding(top = 18.dp, bottom = 24.dp)) {
        Band("Why the coach is often quiet")
        Stat("$movements", "different movements in $inSets sets")
        Stat("$repeated", if (repeated == 1) "you have done twice at the same variant"
             else "you have done more than once at the same variant")
        Text(
            "A bump needs three. Until then the sheet shows the nearest thing you have lifted instead.",
            color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.SmallSize,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** In chalk, not amber: amber is kept for the number the owner came for. */
@Composable
private fun NewerBuild(build: Int, installing: Boolean, onGet: () -> Unit) {
    Text(
        if (installing) "Downloading build $build\u2026" else "Build $build is out \u2014 install it",
        color = Look.Chalk, fontFamily = Look.Figure, fontSize = Look.SmallSize,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !installing, role = androidx.compose.ui.semantics.Role.Button, onClick = onGet)
            .padding(vertical = 14.dp),
    )
}

/**
 * Which build this is, tappable to copy.
 *
 * Every app here carries one, because the owner reports what they see on
 * their phone and neither of us can tell which version that was. "This
 * text feels small" is a different sentence depending on whether the
 * build predates the change that enlarged it, and guessing wrong costs a
 * round trip every time.
 *
 * On the Since tab rather than behind a menu: this is the screen read at
 * home and not in a hurry, and the other two have a job to do.
 */
@Composable
private fun Version() {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val text = "Carry ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    Text(
        "$text \u2014 tap to copy",
        color = Look.ChalkFaint, fontFamily = Look.Figure, fontSize = Look.LabelSize,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
            }
            .padding(vertical = 18.dp),
    )
}

@Composable
private fun Stat(number: String, label: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            number, color = Look.Chalk, fontFamily = Look.Figure, fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp, modifier = Modifier.width(34.dp),
        )
        Text(label, color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.SmallSize,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun figure(weight: Double): String = weight.asWeight()

/** How many rows the progress band may show, so it cannot take the screen
 *  from the three bands under it. NOT "the rest are furthest away": when
 *  every row is on the same rung -- the usual case -- the sort is a no-op
 *  and the five shown are simply the first five the board listed, which is
 *  why the line below counts the others rather than ranking them. */
private const val MAX_BUMP_ROWS = 5
private const val MAX_PIPS = 10

/**
 * What to say about sessions left out of the totals, or null when none
 * were.
 *
 * Not the same sentence as the sheet's, and not shared with it. There the
 * missing thing is a ROW and the screen still shows the rest of the sheet;
 * here it is a whole SESSION, absent from totals the owner has no other
 * way to check. Saying "a row didn't read" on a screen that never shows
 * rows would explain the wrong thing.
 *
 * Three words in it are load-bearing and were all wrong first time.
 * "Sessions", not days: two classes on one date are two files, and
 * "session" is the word the rest of this screen uses. "These totals", not
 * "here": the band above IS drawn from a short newest sheet on purpose,
 * so "isn't counted here" would be contradicted by the first thing under
 * it. And no "part of": a file where EVERY row failed still parses, so
 * "part" is false in exactly the worst case, and there is no sheet beside
 * it to tell the owner otherwise.
 */
internal fun shortNote(sessions: Int): String? = when {
    sessions <= 0 -> null
    sessions == 1 -> "1 session isn't in these totals: its sheet didn't read cleanly. " + SETTLES
    else -> "$sessions sessions aren't in these totals: their sheets didn't read cleanly. " + SETTLES
}

/**
 * The half the Before tab says and this one was leaving out.
 *
 * A file being replaced by a sync is readable as zero bytes for a moment,
 * and the history is re-read on every resume -- so this line can fire on
 * something that will be fine by the time the owner looks again. Without
 * this clause a red warning asserts permanent loss about a file that is
 * merely mid-write, which is the false alarm this project already learned
 * the cost of on the Before tab.
 */
private const val SETTLES = "A sync part way through can do that."

/**
 * Said apart from [shortNote] because a newer schema is not damage.
 *
 * The Before tab says "Carry is out of date ... update Carry" about the
 * same file. Folding it into the damage count had this screen calling an
 * intact file broken while the other screen told the owner to update the
 * app, and unlike the rest of [shortNote] it does not settle on its own.
 */
internal fun newerSchemaNote(sessions: Int): String? = when {
    sessions <= 0 -> null
    sessions == 1 -> "1 session was written by a newer coach than this app understands. Update Carry."
    else -> "$sessions sessions were written by a newer coach than this app understands. Update Carry."
}
