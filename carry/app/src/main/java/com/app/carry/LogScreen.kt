package com.app.carry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What you did. Four things per row, and the two chips are the two that
 * cannot be derived from the numbers:
 *
 *  - *easy* -- finishing says nothing about what was left in reserve, and
 *    it is the only signal measured as able to lift the owner above their
 *    own habit.
 *  - *hurt* -- "couldn't finish" is already derivable from reps against
 *    prescribed reps, but a set can be COMPLETED and still hurt, and a
 *    bodyweight line often has no prescribed count to compare against.
 *
 * A blank row stays blank. Nothing here ever fills one in on the owner's
 * behalf, because a wrong number in a training history is worse than a
 * missing one.
 */
@Composable
fun LogScreen(
    session: Session,
    entries: Map<Int, SessionPatch.Entry>,
    added: List<AddedSet>,
    onEdit: (Int, SessionPatch.Entry) -> Unit,
    onAdd: () -> Unit,
    onEditAdded: (Int, AddedSet) -> Unit,
    onSave: () -> Unit,
    saving: SaveState,
    onShare: (String) -> Unit = {},
    /** The file changed under numbers that have not been saved. */
    fileMoved: Boolean = false,
    onDiscard: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** Rows the file lists that this build could not read. Said here as
     *  well as on the Before tab, because here the consequence is a box
     *  that is not on screen: without it the owner looks for the row they
     *  did and concludes they already filled it in. */
    unreadable: Int = 0,
    /** Changes when a different session is loaded, so the text boxes reset
     *  with it rather than keeping the last workout's keystrokes. */
    sessionKey: String = session.sheetId,
) {
    // "Noted" is what says a set happened ([noted]); anything else on the
    // sheet counts as not done, and the line says so, because there is no
    // control for "didn't do it" any more. Added rows count once they hold
    // anything: their name is the claim.
    val noted = entries.values.count { it.noted() }
    val addedCount = added.count { !it.blank }
    val total = session.sets.size

    Column(modifier.fillMaxSize().background(Look.Slate).padding(horizontal = 14.dp)) {
        Text(
            "How did it go?", color = Look.Chalk, fontFamily = Look.Name,
            fontWeight = FontWeight.Bold, fontSize = Look.TitleSize, modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            listOfNotNull(
                "$noted of $total noted",
                if (addedCount > 0) "$addedCount added" else null,
                "anything untouched counts as not done",
            ).joinToString(" · "),
            color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.After.Tally,
            modifier = Modifier.padding(top = 3.dp, bottom = 10.dp),
        )
        SessionFiles.unreadableNote(unreadable)?.let {
            Text(
                "\u26a0 $it",
                color = Look.BrickInk, fontFamily = Look.Name, fontSize = Look.After.Note,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (fileMoved) {
            Text(
                "\u26a0 The coach rewrote this session while you were filling it in. Your numbers are still here; check they line up before saving.",
                color = Look.BrickInk, fontFamily = Look.Name, fontSize = Look.After.Note,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            // Without this the owner is stuck: if the rows no longer line
            // up the save refuses, every refresh re-raises the warning, and
            // the only exits were clearing app data or swiping the app
            // away -- which also destroys the numbers.
            Text(
                "Use the new sheet instead",
                color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.After.Note,
                modifier = Modifier
                    .border(1.dp, Look.RuleStrong, RoundedCornerShape(4.dp))
                    .clickable(onClick = onDiscard)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        // On a borrowed day the eleven rows below are ANOTHER class's
        // block, and the session is whatever the owner adds by hand. Intel
        // for their class is posted on a small minority of days, so this is
        // the common case rather than the edge one -- and the button that
        // matters most on those days was at the bottom, under eleven rows
        // of the wrong workout.
        val borrowed = session.borrowedFrom
        val list = rememberLazyListState()
        // Add takes the owner to the row it made (or to the blank one it
        // declined to duplicate): that row's name box gets focus, and the
        // keyboard with it. Plain `remember`, so it is spent by the one tap:
        // a rotation or a restored draft composes afresh and moves nothing.
        var focusLast by remember { mutableStateOf(false) }
        // Counts taps, so the scroll below runs for each one and isn't
        // cancelled by the row taking the focus first.
        var adds by remember { mutableStateOf(0) }
        val focusId = if (focusLast) added.lastOrNull()?.id else null
        val add = { onAdd(); adds += 1; focusLast = true }
        val setKeys = remember(sessionKey, session.sets) { rowKeys("$sessionKey/set", session.sets.map { it.ref }) }
        val addedIds = added.map { it.id }
        val addedKeys = remember(sessionKey, addedIds) { rowKeys("$sessionKey/added", addedIds) }
        LaunchedEffect(adds) {
            if (adds == 0) return@LaunchedEffect
            // Mirrors the items below: the warning and the button lead on
            // a borrowed day, the sheet's rows on a normal one.
            val index = (if (borrowed != null) 2 else session.sets.size) + added.lastIndex
            // An effect runs before the frame that lays the new row out, so
            // the layout on hand is the old one, where this index still
            // belonged to the row the new one pushed down.
            withFrameNanos {}
            if (!list.fullyShows(index)) list.scrollToItem(index)
            // The row asks for the focus itself, once it is composed and its
            // box can take it; see [AddedRow]. Spent here as well, in case
            // the row never got the chance (flung away at once), so it can't
            // take the cursor whenever it is next scrolled past.
            repeat(FOCUS_TRIES + 2) { withFrameNanos {} }
            focusLast = false
        }
        LazyColumn(Modifier.weight(1f), state = list) {
            if (borrowed != null) {
                item(key = "borrowed") {
                    Text(
                        "\u26a0 No ${session.classTag ?: "intel"} intel \u2014 these are the " +
                            "$borrowed rows. Add what you actually did.",
                        color = Look.BrickInk, fontFamily = Look.Name, fontSize = Look.After.Note,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                item(key = "add") { AddRow(add, loud = true) }
                itemsIndexed(added, addedKeys, sessionKey, onEditAdded, focusId) { focusLast = false }
            }
            // Keyed by row, so a row's typing (saved while it is scrolled
            // off) stays with it when an added row above pushes it down;
            // keyed by place, it would come back in another row's box.
            items(session.sets.size, key = { setKeys[it] }) { i ->
                val set = session.sets[i]
                LogRow(
                    name = set.displayName,
                    prescription = set.prescription,
                    // Only where a weight is logged: on a bodyweight ladder every
                    // round has the same load, so "heaviest" picks nothing, and
                    // which count to log there is the owner's call.
                    rounds = set.roundCount?.takeIf { !set.offersMod && !set.timed },
                    // A range has no single count to fill: its floor would
                    // go into history as the reps done.
                    prescribedReps = set.prescribedReps.takeIf { set.prescribedText?.isRange() != true },
                    tapFills = set.suggestedWeight == null,
                    offersMod = set.offersMod,
                    timed = set.timed,
                    family = set.family,
                    entry = entries[set.ref] ?: SessionPatch.Entry(),
                    key = "$sessionKey-${set.ref}",
                    onChange = { onEdit(set.ref, it) },
                )
            }
            // Only once, and at whichever end is the useful one: on a
            // borrowed day the added rows ARE the session and lead, on a
            // normal day they are the afterthought the name suggests.
            if (borrowed == null) {
                itemsIndexed(added, addedKeys, sessionKey, onEditAdded, focusId) { focusLast = false }
                item(key = "add") { AddRow(add, loud = false) }
            }
            item(key = "save") { SaveButton(saving, onSave, added, onDiscard) }
            // Only once it is written down. Before that the post would be
            // offering to publish numbers that are not yet anywhere, and
            // the moment to hand intel over is when the logging is done.
            if (saving is SaveState.Saved) {
                IntelPost.compose(session, entries, added)?.let { post ->
                    item(key = "share") { ShareIntel(session.classTag, post, onShare) }
                }
            }
        }
    }
}

@Composable
private fun AddRow(onAdd: () -> Unit, loud: Boolean) {
    Text(
        if (loud) "+ Add what you did" else "+ Add something the sheet missed",
        color = if (loud) Look.Slate else Look.ChalkDim,
        fontFamily = Look.Name, fontWeight = if (loud) FontWeight.Bold else FontWeight.Medium,
        fontSize = Look.After.Button, textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth().padding(top = if (loud) 0.dp else 12.dp, bottom = if (loud) 10.dp else 0.dp)
            .clip(RoundedCornerShape(4.dp))
            .then(if (loud) Modifier.background(Look.Amber) else Modifier.border(1.dp, Look.RuleStrong, RoundedCornerShape(4.dp)))
            .clickable(onClick = onAdd)
            .padding(vertical = 11.dp),
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    added: List<AddedSet>,
    keys: List<String>,
    sessionKey: String,
    onEditAdded: (Int, AddedSet) -> Unit,
    focusId: String?,
    onFocused: () -> Unit,
) {
    items(added.size, key = { keys[it] }) { i ->
        val row = added[i]
        val focus = focusId != null && row.id == focusId && i == added.lastIndex
        // Keyed by the row's own id, not by $i. Keyed by index, a box
        // kept its text when the list shrank under it -- save a session
        // where the first added row was left blank and it is dropped,
        // the second slides into slot 0, and its weight box renders the
        // blank row's empty string while the file holds 30. The screen
        // says "Saved" and shows a number that is not there.
        AddedRow(row, "$sessionKey-added-${row.id}", focus, onFocused) { onEditAdded(i, it) }
    }
}

/**
 * A key per row, unique even where the file repeats a ref or an id: a
 * file with two rows sharing a ref is real enough that SessionPatch
 * refuses it by name, and LazyColumn throws on a duplicate key -- which
 * would crash this tab before the refusal could be shown. The count
 * leads, so no id can spell another row's key.
 */
private fun rowKeys(kind: String, ids: List<Any>): List<String> {
    val seen = HashMap<Any, Int>()
    return ids.map { id ->
        val n = seen.merge(id, 1, Int::plus)!!
        "$kind:$n:$id"
    }
}

/** Frames the new row's name box is given to take the focus. */
private const val FOCUS_TRIES = 10

/** Partly on screen is not enough: the box being focused may be the half cut off. */
private fun LazyListState.fullyShows(index: Int): Boolean {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    return info.offset >= layoutInfo.viewportStartOffset &&
        info.offset + info.size <= layoutInfo.viewportEndOffset
}

/**
 * Whether the owner said this set happened (#127, mockup v4). There is no
 * separate "did it" control: each row says it with its own fields.
 *
 * - A weight, or reps (tapping the faint prescription fills them).
 * - `done` in a timed row's time slot.
 * - *easy* or *mod*, which can't be said of a set that didn't happen.
 *
 * Not *hurt*: tapping it while deciding not to do a movement is the
 * commonest reason to tap it. Not an old file's `hard` or `skipped`
 * either: this page has no chip for them, so a row only they answer
 * looks untouched here. gym-coach's importer still counts a `hard` row
 * as a set, which is why a past session on Since shows and counts it.
 */
internal fun SessionPatch.Entry.noted(): Boolean =
    weight != null || reps != null || fractionalReps != null || done || easy || easier

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LogRow(
    name: String,
    /** "10 total x", "45 sec of" — what the board asked for, which is half
     *  of what the row is. Kept out of [name] so it stays out of the
     *  TalkBack labels on the number boxes, which name the movement. */
    prescription: String?,
    /** [ExerciseSet.roundCount]: the row stands for several rounds, and the
     *  record wants one true weight and count, so the row says which. */
    rounds: Int?,
    /** The runner's rep count, shown faintly in an empty reps box. The
     *  coach's number, not the board's words: "10 total" is 5 a side, and
     *  the importer compares reps against this. Null for a range ("6-12"),
     *  whose floor would go into history as the count done. */
    prescribedReps: Int?,
    /** A tap on the empty reps box fills in [prescribedReps]. Only where the
     *  runner suggests no weight: on a weighted row a stray tap would record
     *  a set with no weight, and blank reps already mean "all of them". */
    tapFills: Boolean,
    /** [ExerciseSet.offersMod]. Decided by the row, not by the weight box,
     *  which made the chip vanish on the first digit and the row jump. */
    offersMod: Boolean,
    family: String?,
    entry: SessionPatch.Entry,
    key: String,
    /** The board asked for seconds, not reps: the reps slot holds a `done`
     *  tag instead, since there is no count to give. */
    timed: Boolean,
    onChange: (SessionPatch.Entry) -> Unit,
) {
    val weightProblem = LogCheck.row(entry.weight, null, null).firstOrNull()
    val repsProblem = LogCheck.row(null, entry.reps, entry.fractionalReps).firstOrNull()
    // Any change clears an older file's `skipped`: there is no skip control
    // any more, an untouched row already means "not done", and a skipped
    // row with a weight on it would be two answers at once.
    val change = { e: SessionPatch.Entry -> onChange(e.copy(skipped = false)) }
    // Two lines: the name and *hurt*, then the fields. Everything is read at
    // arm's length or tapped with cold hands, so neither the name nor the
    // targets give up room; the row gets taller instead.
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Look.family(family)?.let { hue ->
                Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(hue))
            }
            Text(
                // One Text, so a long name wraps around the prescription
                // instead of squeezing it off the row. Faint when the row
                // says it wasn't done: an old skip, or hurt with nothing else.
                prescription?.let { "$it $name" } ?: name,
                color = if (!entry.noted() && (entry.hurt || entry.skipped)) Look.ChalkFaint else Look.Chalk,
                fontFamily = Look.Name, fontSize = Look.After.Name,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            // At the end of the name line, beside the movement it's about.
            // On an untouched row it says why the row was left.
            Chip("hurt", entry.hurt, Look.Brick) { change(entry.copy(hurt = !entry.hurt)) }
        }
        rounds?.let {
            Text(
                "$it rounds \u00b7 log your heaviest set",
                color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.After.Note,
                modifier = Modifier.padding(start = 9.dp, top = 2.dp),
            )
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 9.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            // Chips sit level with the boxes, not with the labels above them.
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            NumberField(entry.weight.asWeightField(), "$key-w", "$name, lb", "lb", weightProblem) { text ->
                change(entry.copy(weight = text.toDoubleOrNull()))
            }
            if (timed) {
                DoneTag(entry.done, "$name, time") { change(entry.copy(done = !entry.done)) }
            } else {
                val count = prescribedReps?.takeIf { it > 0 }?.toString()
                NumberField(
                    repsField(entry.reps, entry.fractionalReps), "$key-r", "$name, reps", "reps", repsProblem,
                    hint = count, fill = count.takeIf { tapFills },
                ) { text ->
                    val (reps, fractional) = parseReps(text)
                    change(entry.copy(reps = reps, fractionalReps = fractional))
                }
            }
            // No `hard` (madCode, 2026-10-16), but tapping `easy` clears an
            // older file's: the owner's fresh answer wins.
            Chip("easy", entry.easy, Look.Amber) { change(entry.copy(easy = !entry.easy, hard = false)) }
            // "I did the easier version" -- knees, a box under the step-up.
            // Only on a no-weight row, since that is the only axis a
            // bodyweight movement has; kept while it is on, so it can't be
            // left set where nobody can see it.
            if (offersMod || entry.easier) {
                Chip("mod", entry.easier, Look.ChalkDim) { change(entry.copy(easier = !entry.easier)) }
            }
        }
        RowProblem(listOfNotNull(weightProblem, repsProblem))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Look.Rule))
}

/**
 * A timed row's way to say it happened, in the reps box's slot under a
 * "time" label: a hold prescribes no count, so there is nothing to type.
 * Writes the existing `done` field.
 */
@Composable
private fun DoneTag(on: Boolean, label: String, onToggle: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FieldLabel("time")
        Box(
            Modifier
                .widthIn(min = 50.dp).heightIn(min = 44.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (on) Look.SlateRaised else Look.Slate)
                .border(1.dp, if (on) Look.Chalk else Look.RuleStrong, RoundedCornerShape(4.dp))
                .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle() })
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "done", color = if (on) Look.Chalk else Look.ChalkFaint,
                fontFamily = Look.Figure, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** The visible name of a box, above it, and staying put once something is
 *  typed. Hidden from TalkBack because the box's own label says the same. */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text, color = Look.ChalkDim, fontFamily = Look.Figure, fontSize = Look.After.Label,
        modifier = Modifier.padding(bottom = 2.dp).semantics { hideFromAccessibility() },
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AddedRow(
    row: AddedSet, key: String,
    /** Add just made this row, or went back to it: its name box takes the focus. */
    focus: Boolean, onFocused: () -> Unit,
    onChange: (AddedSet) -> Unit,
) {
    val nameFocus = remember { FocusRequester() }
    if (focus) {
        LaunchedEffect(Unit) {
            // From the row, not the list: a request made before the row
            // is composed finds no box and is dropped without a word.
            // A few frames' grace for a box still being attached.
            repeat(FOCUS_TRIES) {
                if (nameFocus.requestFocus(FocusDirection.Enter)) return@LaunchedEffect onFocused()
                withFrameNanos {}
            }
            onFocused()
        }
    }
    val weightProblem = LogCheck.row(row.weight, null, null).firstOrNull()
    val repsProblem = LogCheck.row(null, row.reps, row.fractionalReps).firstOrNull()
    // Same two-line shape as a sheet row, for the same reason: a name the
    // owner types needs the width more than a one-line row needs the
    // height, and the two kinds of row sitting differently on the same
    // screen read as a mistake.
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Look.ChalkFaint))
            BasicTextField(
                value = row.name,
                onValueChange = { onChange(row.copy(name = it)) },
                singleLine = true,
                textStyle = TextStyle(color = Look.Chalk, fontFamily = Look.Name, fontSize = Look.After.Name),
                cursorBrush = SolidColor(Look.Amber),
                decorationBox = { inner ->
                    if (row.name.isBlank()) {
                        // Once there are numbers in the row, the missing
                        // name is what will refuse the save -- so it asks
                        // here, in the box that fixes it, rather than
                        // letting the owner find out at the save button.
                        val asking = !row.blank
                        Text(
                            if (asking) "name this" else "what was it?",
                            color = if (asking) Look.BrickInk else Look.ChalkFaint,
                            fontFamily = Look.Name, fontSize = Look.After.Name,
                        )
                    }
                    inner()
                },
                modifier = Modifier.weight(1f)
                    .focusRequester(nameFocus)
                    .semantics { contentDescription = "added exercise name" },
            )
            Chip("hurt", row.hurt, Look.Brick) { onChange(row.copy(hurt = !row.hurt)) }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 9.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            NumberField(row.weight.asWeightField(), "$key-w", "added exercise, lb", "lb", weightProblem) {
                onChange(row.copy(weight = it.toDoubleOrNull()))
            }
            NumberField(repsField(row.reps, row.fractionalReps), "$key-r", "added exercise, reps", "reps", repsProblem) {
                val (reps, fractional) = parseReps(it)
                onChange(row.copy(reps = reps, fractionalReps = fractional))
            }
            Chip("easy", row.easy, Look.Amber) { onChange(row.copy(easy = !row.easy, hard = false)) }
            // The coach suggests nothing for an added row, so the weight
            // box is the only sign of whether it carried one.
            if (row.weight == null || row.easier) {
                Chip("mod", row.easier, Look.ChalkDim) { onChange(row.copy(easier = !row.easier)) }
            }
            // No skip: a row you added is one you did. Clearing it is
            // emptying the name.
        }
        RowProblem(listOfNotNull(weightProblem, repsProblem))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Look.Rule))
}

/**
 * Holds the TEXT, not the parsed number.
 *
 * It used to render `entry.weight` back into the box, which meant the
 * decimal point could not be typed at all: "12." parses to 12.0, renders
 * as "12", and the next keystroke makes 125. A half-pound dumbbell became
 * a weight ten times too large, silently, in the app whose first rule is
 * that a wrong number is worse than a missing one.
 *
 * [key] resets the box when a different session is loaded; within a
 * session the owner's keystrokes are the only thing that changes it.
 */
@Composable
private fun NumberField(
    initial: String, key: Any, label: String, unit: String, problem: String?,
    /** Shown faintly while the box is empty. */
    hint: String? = null,
    /** Put in by a tap on the empty box (or TalkBack's "Fill in" action),
     *  selected so that typing replaces it. A tap, not focus: focus also
     *  arrives by Tab or D-pad, which would record sets nobody did. */
    fill: String? = null,
    onChange: (String) -> Unit,
) {
    var value by rememberSaveable(key, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial)) }
    val fillNow = {
        if (fill != null && value.text.isEmpty()) {
            value = TextFieldValue(fill, TextRange(0, fill.length))
            onChange(fill)
        }
    }
    // The gesture loop outlives recompositions (it restarts only when `fill`
    // changes), so it calls the latest fill: an old one would carry an old
    // `onChange`, writing reps over the row as it was before later edits.
    val latestFill by rememberUpdatedState(fillNow)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FieldLabel(unit)
        BasicTextField(
            value = value,
            onValueChange = { typed ->
                // '0'..'9' rather than isDigit(): a Hindi or Arabic keyboard
                // offers digits that pass isDigit() and then fail to parse, so
                // the box would blank on every keystroke with no explanation.
                val cleaned = typed.text.filter { it in '0'..'9' || it == '.' }
                if (cleaned.count { it == '.' } <= 1 && cleaned.length <= 6) {
                    val changed = cleaned != value.text
                    value = typed.copy(text = cleaned)
                    if (changed) onChange(cleaned)
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = TextStyle(
                color = Look.Chalk, fontFamily = Look.Figure, fontSize = 15.sp,
                textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
            ),
            cursorBrush = SolidColor(Look.Amber),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (value.text.isEmpty() && hint != null) {
                        // Regular weight in the faint colour, against the
                        // semi-bold chalk of a typed number: readable in
                        // glare, and never taken for one already entered.
                        Text(
                            hint, color = Look.ChalkFaint, fontFamily = Look.Figure, fontSize = 15.sp,
                            modifier = Modifier.semantics { hideFromAccessibility() },
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier
                .width(50.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Look.SlateRaised)
                .border(if (problem != null) 2.dp else 1.dp, if (problem != null) Look.BrickInk else Look.RuleStrong, RoundedCornerShape(4.dp))
                .pointerInput(fill) {
                    // Watched on the Initial pass and not consumed, so the
                    // field still takes the tap and focuses as usual. A drag
                    // (the list scrolling) cancels; a long-press, which asks
                    // for Paste, does not fill.
                    awaitEachGesture {
                        awaitFirstDown(pass = PointerEventPass.Initial)
                        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation(PointerEventPass.Initial)
                        }
                        if (up != null) latestFill()
                    }
                }
                .semantics {
                    contentDescription = label
                    // The faint number is hidden from TalkBack, so say it,
                    // and offer the fill as an action.
                    if (hint != null && value.text.isEmpty()) stateDescription = "empty, prescribed $hint"
                    if (fill != null && value.text.isEmpty()) {
                        customActions = listOf(CustomAccessibilityAction("Fill in $fill") { fillNow(); true })
                    }
                    // Spoken on the box itself, so a listener who lands on it
                    // hears what is wrong without finding the line below.
                    if (problem != null) error(problem)
                }
                .padding(vertical = 11.dp),
        )
    }
}

/**
 * Under the row it belongs to, in words as well as colour: the box's red
 * border alone says nothing in glare, on a greyscale display or to anyone
 * who cannot tell brick from grey.
 */
@Composable
private fun RowProblem(problems: List<String>) {
    val message = LogCheck.rowMessage(problems) ?: return
    Text(
        message,
        color = Look.BrickInk, fontFamily = Look.Name, fontSize = Look.After.Note,
        modifier = Modifier.padding(start = 9.dp, top = 4.dp),
    )
}

/**
 * The reps box's text as a rep count, or as a fractional count the save
 * will refuse. "8." is mid-typing and keeps 8; "8.5" is not 8 and not
 * blank, because either would store something the box does not show.
 */
private fun parseReps(text: String): Pair<Int?, Double?> {
    val n = text.trimEnd('.').toDoubleOrNull() ?: return null to null
    return if (n == Math.floor(n)) n.toInt() to null else null to n
}

private fun repsField(reps: Int?, fractional: Double?): String =
    reps?.toString() ?: fractional?.asWeight() ?: ""

@Composable
private fun Chip(text: String, on: Boolean, accent: androidx.compose.ui.graphics.Color, onToggle: () -> Unit) {
    // toggleable, not clickable: TalkBack otherwise announces "easy" the
    // same whether it is on or off, leaving colour as the only signal.
    // 44dp because this is filled in a car with cold hands -- but as a
    // MINIMUM, not a fixed square: a longer word wrapped inside the box
    // rather than widening it, which looked broken.
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 44.dp)
            .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            maxLines = 1,
            color = if (on) accent else Look.ChalkFaint,
            fontFamily = Look.Figure, fontSize = Look.After.Chip,
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .border(1.dp, if (on) accent else Look.RuleStrong, RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 5.dp),
        )
    }
}

/**
 * Hands the block over to be posted. Intel for this class is posted on a
 * small minority of days, which is why most of the owner's history is
 * borrowed from the other class's block — so this is the one action here
 * that helps somebody other than them. (The share of days is a fact about
 * the owner's training and this repository is public, so it stays out of
 * here; see CLAUDE.md, "Privacy".)
 */
@Composable
private fun ShareIntel(classTag: String?, post: IntelPost.Post, onSend: (String) -> Unit) {
    // Shown before it is sent, always. The claim under the button is one
    // this screen cannot verify on its own -- the text is built from a
    // file written on another machine -- and the only assurance worth
    // anything is the owner reading the thing before it leaves.
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(top = 14.dp, bottom = 24.dp)) {
        Text(
            if (open) "Hide" else "Post what ${classTag ?: "this class"} did",
            color = Look.Chalk, fontFamily = Look.Name, fontSize = Look.After.Button,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Look.RuleStrong, RoundedCornerShape(4.dp))
                .clickable { open = !open }
                .padding(vertical = 12.dp),
        )
        // Not "never your weights": a name is typed by hand, and nothing
        // here can promise what it holds. The save already showed each typed
        // name back and asked (see [TypedBack]).
        Text(
            "The movements only. A name you typed goes out as you typed it.",
            color = Look.ChalkFaint, fontFamily = Look.Name, fontSize = Look.After.Note,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        if (open) {
            // No `contentDescription`: Compose offers LINE and PAGE
            // navigation only to a text node whose description is empty,
            // so a listener can step through the post a bullet at a time.
            Text(
                post.text,
                color = Look.ChalkDim, fontFamily = Look.Figure, fontSize = Look.After.Preview,
                modifier = Modifier
                    .fillMaxWidth().padding(top = 10.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Look.SlateRaised)
                    .padding(10.dp),
            )
            Text(
                "Send it",
                color = Look.Slate, fontFamily = Look.Name, fontWeight = FontWeight.Bold,
                fontSize = Look.After.Button, textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth().padding(top = 10.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Look.Amber)
                    .clickable { onSend(post.text) }
                    .padding(vertical = 12.dp),
            )
        }
    }
}

/**
 * Each name the owner typed, shown back once the save has landed, with the
 * one question a typed name raises: is the weight in the weight box?
 *
 * Carry can't tell a load from a movement in free text (a digit test
 * missed "deadlift twenty six" and "½ rack" and flagged "bicep curl 21s"),
 * and it never guesses an identity, so it asks. A line, not a dialog: a
 * dialog on every save with an added row is one the owner learns to
 * dismiss unread. It shows with "Saved" and goes with it, at the next
 * edit or re-read. Announced politely, so TalkBack reads it without
 * moving focus. What is written to the file is unchanged.
 */
@Composable
private fun TypedBack(added: List<AddedSet>) {
    // Unicode separators as well as ASCII whitespace, so U+2028 and friends
    // fold rather than breaking the sentence across lines. NOT `(?U)`: that
    // inline flag compiles on the JVM and throws on Android, whose regex is
    // ICU's, so every unit test passed and the app died on the phone -- on
    // the recomposition after a save that had already written the file.
    val names = added.mapNotNull { row ->
        row.name.readable().replace(SPACES, " ").trim().takeIf { it.isNotEmpty() }?.let(::clip)
    }.distinct()
    if (names.isEmpty()) return
    Text(
        typedBack(names),
        color = Look.Chalk, fontFamily = Look.Name, fontSize = Look.After.Note,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .border(1.dp, Look.RuleStrong, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** At [MAX_TYPED_NAME] code points, with an ellipsis so the cut shows
 *  and never half a character. */
private fun clip(name: String): String =
    if (name.codePointCount(0, name.length) <= MAX_TYPED_NAME) name
    else name.substring(0, name.offsetByCodePoints(0, MAX_TYPED_NAME)) + "\u2026"

/**
 * The words of [TypedBack], kept apart so a test can read them whole.
 * Each name is a directional isolate (U+2068 to U+2069), so a right-to-left
 * name doesn't pull the quotes, the comma or a digit to the wrong side.
 */
internal fun typedBack(names: List<String>): String {
    val quoted = names.joinToString(", ") { "\"\u2068$it\u2069\"" }
    return if (names.size == 1) "You typed: $quoted. Is the weight in the weight box?"
    else "You typed: $quoted. Is each weight in its weight box?"
}

/** A typed name is shown back whole up to this, well past any movement's
 *  name, so a weight typed at the end of a long one still shows. */
private const val MAX_TYPED_NAME = 200

/** Drops the typing and loads the file as it now stands. Its label says
 *  what is lost, since it is one tap and the numbers can't be re-derived. */
@Composable
private fun NewFileButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .border(1.dp, Look.RuleStrong, RoundedCornerShape(4.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Drop my numbers, load the new file",
            color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.After.Note,
        )
    }
}

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data object Saved : SaveState
    /** Not saved. [damaged] means the file itself needs rescuing, which is
     *  a different thing to tell someone than "try again". */
    data class Failed(
        val why: String,
        val damaged: Boolean,
        /** What was typed, one line per row, shown under [why] so the
         *  numbers survive whatever the owner does next. */
        val typed: List<String> = emptyList(),
        /** The file on disk is newer than the one on screen: offer it. */
        val newFile: Boolean = false,
    ) : SaveState
}

@Composable
private fun SaveButton(state: SaveState, onSave: () -> Unit, added: List<AddedSet>, onDiscard: () -> Unit) {
    val label = when (state) {
        SaveState.Idle -> "Save"
        SaveState.Saving -> "Saving…"
        SaveState.Saved -> "Saved"
        is SaveState.Failed -> "Try again"
    }
    Column(Modifier.padding(top = 14.dp, bottom = 24.dp)) {
        if (state is SaveState.Failed) {
            Text(
                (if (state.damaged) "⚠ " else "") + state.why,
                color = Look.BrickInk,
                fontFamily = Look.Name, fontSize = Look.After.Note,
                // Announced when it appears: the tap that caused it gives a
                // listener no other sign that nothing was saved.
                modifier = Modifier.padding(bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
            state.typed.forEach {
                Text(
                    it, color = Look.Chalk, fontFamily = Look.Name, fontSize = Look.After.Note,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            // Well clear of Try again, which is what the thumb is about to
            // tap again, since this one throws the typing away.
            if (state.newFile) NewFileButton(onDiscard, Modifier.padding(top = 8.dp, bottom = 28.dp))
        }
        // In the button's own item and above it, where the owner just
        // tapped: a separate item below could land off the bottom of the
        // list, and an item that isn't composed is neither seen nor read.
        if (state is SaveState.Saved) TypedBack(added)
        Text(
            label, color = Look.Slate, fontFamily = Look.Name, fontWeight = FontWeight.Bold,
            fontSize = Look.After.Button, textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(5.dp))
                .background(if (state == SaveState.Saved) Look.ChalkFaint else Look.Amber)
                .clickable(enabled = state != SaveState.Saving, onClick = onSave)
                .padding(vertical = 12.dp),
        )
    }
}

/** "6-12", "8/10": a range of counts rather than one. */
private fun String.isRange(): Boolean = any { it == '-' || it == '/' }

private fun Double?.asWeightField(): String = this?.let { asWeight() } ?: ""

/**
 * ASCII whitespace plus every Unicode separator, so a name carrying U+2028 or
 * a non-breaking space folds to one ordinary space instead of breaking the
 * sentence across lines.
 *
 * Built once rather than per row, and as a character class rather than the
 * `(?U)` flag it replaces: Android's regex is ICU's, which rejects that inline
 * flag outright, while the JVM accepts it — so unit tests and Robolectric both
 * passed and the phone threw PatternSyntaxException instead.
 */
internal val SPACES = Regex("[\\p{Z}\\s]+")
