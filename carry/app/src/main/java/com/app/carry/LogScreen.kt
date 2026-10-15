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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    /** Changes when a different session is loaded, so the text boxes reset
     *  with it rather than keeping the last workout's keystrokes. */
    sessionKey: String = session.sheetId,
) {
    // An added row counts once it holds anything, the same test the sets
    // use. Counting only named ones meant typing a weight into a row you
    // had not named yet left the tally reading "0 of 11 filled".
    val skipped = entries.count { it.value.skipped }
    // A skipped row is answered, not filled. Counting it as filled would
    // say the form is further along than it is; counting it as neither
    // would leave the tally stuck below the total with nothing to do.
    val filled = entries.count { !it.value.blank && !it.value.skipped } + added.count { !it.blank }
    // Blank added rows are left out of the file, so they are left out of
    // the count too: tapping "+ Add" used to make the tally read worse
    // ("1 of 12"), then quietly improve again after a save that wrote
    // nothing new.
    val total = session.sets.size + added.count { !it.blank }

    Column(modifier.fillMaxSize().background(Look.Slate).padding(horizontal = 14.dp)) {
        Text(
            "How did it go?", color = Look.Chalk, fontFamily = Look.Name,
            fontWeight = FontWeight.Bold, fontSize = Look.TitleSize, modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            listOfNotNull(
                "$filled of $total filled",
                if (skipped > 0) "$skipped skipped" else null,
                "partial is fine",
            ).joinToString(" · "),
            color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.SmallSize,
            modifier = Modifier.padding(top = 3.dp, bottom = 10.dp),
        )
        if (fileMoved) {
            Text(
                "\u26a0 The coach rewrote this session while you were filling it in. Your numbers are still here; check they line up before saving.",
                color = Look.BrickInk, fontFamily = Look.Name, fontSize = Look.SmallSize,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            // Without this the owner is stuck: if the rows no longer line
            // up the save refuses, every refresh re-raises the warning, and
            // the only exits were clearing app data or swiping the app
            // away -- which also destroys the numbers.
            Text(
                "Use the new sheet instead",
                color = Look.ChalkDim, fontFamily = Look.Name, fontSize = Look.SmallSize,
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
        LazyColumn(Modifier.weight(1f)) {
            if (borrowed != null) {
                item {
                    Text(
                        "\u26a0 No ${session.classTag ?: "intel"} intel \u2014 these are the " +
                            "$borrowed rows. Add what you actually did.",
                        color = Look.BrickInk, fontFamily = Look.Name, fontSize = Look.SmallSize,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                item { AddRow(onAdd, loud = true) }
                itemsIndexed(added, sessionKey, onEditAdded)
            }
            // No key: a file with two rows sharing a ref is real enough
            // that SessionPatch refuses it by name, and LazyColumn throws
            // on a duplicate key -- which would crash this tab before the
            // refusal could ever be shown.
            items(session.sets) { set ->
                LogRow(
                    name = set.displayName,
                    prescription = set.prescription,
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
                itemsIndexed(added, sessionKey, onEditAdded)
                item { AddRow(onAdd, loud = false) }
            }
            item { SaveButton(saving, onSave) }
            // Only once it is written down. Before that the post would be
            // offering to publish numbers that are not yet anywhere, and
            // the moment to hand intel over is when the logging is done.
            if (saving is SaveState.Saved) {
                IntelPost.compose(session, entries, added)?.let { post ->
                    item { ShareIntel(session.classTag, post, onShare) }
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
        fontSize = Look.SmallSize, textAlign = TextAlign.Center,
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
    sessionKey: String,
    onEditAdded: (Int, AddedSet) -> Unit,
) {
    items(added.size) { i ->
        val row = added[i]
        // Keyed by the row's own id, not by $i. Keyed by index, a box
        // kept its text when the list shrank under it -- save a session
        // where the first added row was left blank and it is dropped,
        // the second slides into slot 0, and its weight box renders the
        // blank row's empty string while the file holds 30. The screen
        // says "Saved" and shows a number that is not there.
        AddedRow(row, "$sessionKey-added-${row.id}") { onEditAdded(i, it) }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LogRow(
    name: String,
    /** "10 total x", "45 sec of" — what the board asked for, which is half
     *  of what the row is. Kept out of [name] so it stays out of the
     *  TalkBack labels on the number boxes, which name the movement. */
    prescription: String?,
    family: String?,
    entry: SessionPatch.Entry,
    key: String,
    /** The board asked for seconds, not reps, so there is no number to give
     *  and the row needs its own way to say it happened. */
    timed: Boolean,
    onChange: (SessionPatch.Entry) -> Unit,
) {
    // Two lines, not one. On one line the name shared ~400dp with two
    // number boxes and three 44dp chips, and the owner's own sheet
    // truncated: "bicep curl + lateral lu...". Everything here is read at
    // arm's length or tapped with cold hands, so neither the name nor the
    // targets can give up room to the other — the row gets taller instead,
    // which costs only scrolling on the screen where scrolling is fine.
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Look.family(family)?.let { hue ->
                Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(hue))
            }
            Text(
                // One Text, so a long name wraps around the prescription
                // instead of squeezing it off the row.
                prescription?.let { "$it $name" } ?: name,
                color = if (entry.skipped) Look.ChalkFaint else Look.Chalk,
                fontFamily = Look.Name, fontSize = Look.NameSize,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
        }
        // Wraps, because a timed row carries a sixth chip and six did not
        // fit on one 411dp line: the overflow was `skip` sliding off the
        // right edge, which is the one chip on the row that says a set did
        // not happen. A second line costs scrolling on the screen where
        // scrolling is fine; a chip the owner cannot reach costs a fact.
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 9.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // A skipped row offers no boxes and no feel chips. There is no
            // number to put in them, and no set to have found easy.
            if (!entry.skipped) {
                NumberField(entry.weight.asWeightField(), "$key-w", "$name weight in pounds") { text ->
                    onChange(entry.copy(weight = text.toDoubleOrNull()))
                }
                NumberField(entry.reps?.toString() ?: "", "$key-r", "$name reps") { text ->
                    // "8." is mid-typing, not a mistake: keep the last whole
                    // number rather than blanking what they already entered.
                    onChange(entry.copy(reps = text.trimEnd('.').toIntOrNull()))
                }
                // easy and hard are one question with two answers, so
                // turning one on turns the other off. A set is not both.
                Chip("easy", entry.easy, Look.Amber) {
                    onChange(entry.copy(easy = !entry.easy, hard = false))
                }
                Chip("hard", entry.hard, Look.BrickInk) {
                    onChange(entry.copy(hard = !entry.hard, easy = false))
                }
                // "I did the easier version" -- knees, a box under the
                // step-up. For a third of this owner's sets there is no
                // weight to progress, so this is the only axis they have.
                Chip("mod", entry.easier, Look.ChalkDim) {
                    onChange(entry.copy(easier = !entry.easier))
                }
                // Only where there is nothing else to say. A timed hold
                // prescribes no reps and takes no weight, so the boxes are
                // empty by definition and every other chip claims something
                // the owner may not mean -- a finished plank reached history
                // as nothing at all until this.
                //
                // Chalk, the loudest the palette goes, because on these
                // rows it is not a side note but the thing to tap. The
                // number boxes stay: the board prescribes no weight, but a
                // plank with a plate on your back is a real set, and
                // taking the boxes away would make that unsayable.
                if (timed) {
                    Chip("done", entry.done, Look.Chalk) {
                        onChange(entry.copy(done = !entry.done))
                    }
                }
                Chip("hurt", entry.hurt, Look.Brick) { onChange(entry.copy(hurt = !entry.hurt)) }
            }
            // Saying "I didn't do this" and leaving it blank are different
            // facts, and only one of them was sayable. `skipped` has been
            // in the schema, in the patch and in the importer -- which logs
            // those so the coach stops re-asking -- since before this
            // screen existed, with no way to set it.
            //
            // Clearing it clears the row with it: a weight against a set
            // you did not do is the wrong number this app exists to avoid.
            Chip("skip", entry.skipped, Look.ChalkDim) {
                onChange(
                    if (entry.skipped) entry.copy(skipped = false)
                    else SessionPatch.Entry(skipped = true)
                )
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Look.Rule))
}

@Composable
private fun AddedRow(row: AddedSet, key: String, onChange: (AddedSet) -> Unit) {
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
                textStyle = TextStyle(color = Look.Chalk, fontFamily = Look.Name, fontSize = Look.NameSize),
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
                            fontFamily = Look.Name, fontSize = Look.NameSize,
                        )
                    }
                    inner()
                },
                modifier = Modifier.weight(1f).semantics { contentDescription = "added exercise name" },
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 9.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NumberField(row.weight.asWeightField(), "$key-w", "added exercise weight in pounds") {
                onChange(row.copy(weight = it.toDoubleOrNull()))
            }
            NumberField(row.reps?.toString() ?: "", "$key-r", "added exercise reps") {
                onChange(row.copy(reps = it.trimEnd('.').toIntOrNull()))
            }
            Chip("easy", row.easy, Look.Amber) { onChange(row.copy(easy = !row.easy, hard = false)) }
            Chip("hard", row.hard, Look.BrickInk) { onChange(row.copy(hard = !row.hard, easy = false)) }
            Chip("mod", row.easier, Look.ChalkDim) { onChange(row.copy(easier = !row.easier)) }
            Chip("hurt", row.hurt, Look.Brick) { onChange(row.copy(hurt = !row.hurt)) }
            // No skip: a row you added is one you did. Skipping it is
            // clearing it, which the owner does by emptying the name.
        }
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
private fun NumberField(initial: String, key: Any, label: String, onChange: (String) -> Unit) {
    var text by rememberSaveable(key) { mutableStateOf(initial) }
    BasicTextField(
        value = text,
        onValueChange = { typed ->
            // '0'..'9' rather than isDigit(): a Hindi or Arabic keyboard
            // offers digits that pass isDigit() and then fail to parse, so
            // the box would blank on every keystroke with no explanation.
            val cleaned = typed.filter { it in '0'..'9' || it == '.' }
            if (cleaned.count { it == '.' } <= 1 && cleaned.length <= 6) {
                text = cleaned
                onChange(cleaned)
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = TextStyle(
            color = Look.Chalk, fontFamily = Look.Figure, fontSize = 15.sp,
            textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
        ),
        cursorBrush = SolidColor(Look.Amber),
        modifier = Modifier
            .width(50.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Look.SlateRaised)
            .border(1.dp, Look.RuleStrong, RoundedCornerShape(4.dp))
            .padding(vertical = 11.dp)
            .semantics { contentDescription = label },
    )
}

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
            fontFamily = Look.Figure, fontSize = 11.sp,
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
            color = Look.Chalk, fontFamily = Look.Name, fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Look.RuleStrong, RoundedCornerShape(4.dp))
                .clickable { open = !open }
                .padding(vertical = 12.dp),
        )
        Text(
            // The honest version of the claim. A name is typed by hand, so
            // "never your weights" is not something this screen can promise
            // on its own — what it can do is show you the lines where you
            // may have typed one.
            if (post.suspect.isEmpty()) "The movements only \u2014 never your weights."
            else "Check the lines in red \u2014 they look like they carry a weight.",
            color = if (post.suspect.isEmpty()) Look.ChalkFaint else Look.BrickInk,
            fontFamily = Look.Name, fontSize = Look.SmallSize,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        if (open) {
            Text(
                buildAnnotatedString {
                    post.text.lines().forEachIndexed { i, line ->
                        if (i > 0) append('\n')
                        if (line in post.suspect) {
                            withStyle(SpanStyle(color = Look.BrickInk)) { append(line) }
                        } else {
                            append(line)
                        }
                    }
                },
                color = Look.ChalkDim, fontFamily = Look.Figure, fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth().padding(top = 10.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Look.SlateRaised)
                    .padding(10.dp),
            )
            Text(
                "Send it",
                color = Look.Slate, fontFamily = Look.Name, fontWeight = FontWeight.Bold,
                fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth().padding(top = 10.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Look.Amber)
                    .clickable { onSend(post.text) }
                    .padding(vertical = 12.dp),
            )
        }
    }
}

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data object Saved : SaveState
    /** Not saved. [damaged] means the file itself needs rescuing, which is
     *  a different thing to tell someone than "try again". */
    data class Failed(val why: String, val damaged: Boolean) : SaveState
}

@Composable
private fun SaveButton(state: SaveState, onSave: () -> Unit) {
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
                color = if (state.damaged) Look.BrickInk else Look.ChalkDim,
                fontFamily = Look.Name, fontSize = Look.SmallSize,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Text(
            label, color = Look.Slate, fontFamily = Look.Name, fontWeight = FontWeight.Bold,
            fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(5.dp))
                .background(if (state == SaveState.Saved) Look.ChalkFaint else Look.Amber)
                .clickable(enabled = state != SaveState.Saving, onClick = onSave)
                .padding(vertical = 12.dp),
        )
    }
}

private fun Double?.asWeightField(): String = this?.let { asWeight() } ?: ""
