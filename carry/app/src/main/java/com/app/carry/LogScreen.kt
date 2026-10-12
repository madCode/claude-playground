package com.app.carry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
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
            fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.padding(top = 16.dp),
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
        LazyColumn(Modifier.weight(1f)) {
            // No key: a file with two rows sharing a ref is real enough
            // that SessionPatch refuses it by name, and LazyColumn throws
            // on a duplicate key -- which would crash this tab before the
            // refusal could ever be shown.
            items(session.sets) { set ->
                LogRow(
                    name = set.displayName,
                    family = set.family,
                    entry = entries[set.ref] ?: SessionPatch.Entry(),
                    key = "$sessionKey-${set.ref}",
                    onChange = { onEdit(set.ref, it) },
                )
            }
            itemsIndexed(added, sessionKey, onEditAdded)
            item {
                Text(
                    "+ Add something the sheet missed",
                    color = Look.ChalkDim, fontFamily = Look.Name, fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth().padding(top = 12.dp)
                        .border(1.dp, Look.RuleStrong, RoundedCornerShape(4.dp))
                        .clickable(onClick = onAdd)
                        .padding(vertical = 10.dp),
                )
            }
            item { SaveButton(saving, onSave) }
        }
    }
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
private fun LogRow(
    name: String,
    family: String?,
    entry: SessionPatch.Entry,
    key: String,
    onChange: (SessionPatch.Entry) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Look.family(family)?.let { hue ->
            Box(Modifier.width(3.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(hue))
        }
        Text(
            name, color = if (entry.skipped) Look.ChalkFaint else Look.Chalk,
            fontFamily = Look.Name, fontSize = 13.5.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        // A skipped row offers no boxes. There is no number to put in them
        // -- that is what skipped means -- and taking them away gives the
        // name back the width the third chip cost it, on exactly the rows
        // that no longer need it.
        if (!entry.skipped) {
            NumberField(entry.weight.asWeightField(), "$key-w", "$name weight in pounds") { text ->
                onChange(entry.copy(weight = text.toDoubleOrNull()))
            }
            NumberField(entry.reps?.toString() ?: "", "$key-r", "$name reps") { text ->
                // "8." is mid-typing, not a mistake: keep the last whole
                // number rather than blanking what they already entered.
                onChange(entry.copy(reps = text.trimEnd('.').toIntOrNull()))
            }
        }
        if (!entry.skipped) {
            Chip("easy", entry.easy, Look.Amber) { onChange(entry.copy(easy = !entry.easy)) }
            Chip("hurt", entry.hurt, Look.Brick) { onChange(entry.copy(hurt = !entry.hurt)) }
        }
        // Saying "I didn't do this" and leaving it blank are different
        // facts, and only one of them was sayable. `skipped` has been in
        // the schema, in the patch and in the importer -- which logs those
        // so the coach stops re-asking -- since before this screen
        // existed, with no way to set it. A movement deliberately skipped
        // was indistinguishable from one not got to, so the coach kept
        // suggesting it for ever.
        //
        // Clearing it clears the row with it: a weight against a set you
        // did not do is the wrong number this app exists to avoid.
        Chip("skip", entry.skipped, Look.ChalkDim) {
            onChange(
                if (entry.skipped) entry.copy(skipped = false)
                else SessionPatch.Entry(skipped = true)
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Look.Rule))
}

@Composable
private fun AddedRow(row: AddedSet, key: String, onChange: (AddedSet) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.width(3.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(Look.ChalkFaint))
        BasicTextField(
            value = row.name,
            onValueChange = { onChange(row.copy(name = it)) },
            singleLine = true,
            textStyle = TextStyle(color = Look.Chalk, fontFamily = Look.Name, fontSize = 13.5.sp),
            cursorBrush = SolidColor(Look.Amber),
            decorationBox = { inner ->
                // isBlank, not isEmpty: the save refuses on isBlank, so a
                // name of one space showed neither hint while still
                // blocking the save -- told to name a row that looked
                // named, with no way to tell which.
                if (row.name.isBlank()) {
                    // Once there are numbers in the row, the missing name
                    // is the thing that will refuse the save -- so it asks
                    // here, in the box that fixes it, rather than letting
                    // the owner find out at the save button.
                    val asking = !row.blank
                    Text(
                        if (asking) "name this" else "what was it?",
                        color = if (asking) Look.BrickInk else Look.ChalkFaint,
                        fontFamily = Look.Name, fontSize = 13.5.sp,
                    )
                }
                inner()
            },
            modifier = Modifier.weight(1f).semantics { contentDescription = "added exercise name" },
        )
        NumberField(row.weight.asWeightField(), "$key-w", "added exercise weight in pounds") {
            onChange(row.copy(weight = it.toDoubleOrNull()))
        }
        NumberField(row.reps?.toString() ?: "", "$key-r", "added exercise reps") {
            onChange(row.copy(reps = it.trimEnd('.').toIntOrNull()))
        }
        Chip("easy", row.easy, Look.Amber) { onChange(row.copy(easy = !row.easy)) }
        Chip("hurt", row.hurt, Look.Brick) { onChange(row.copy(hurt = !row.hurt)) }
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
    // And a 44dp box around 10sp of text, because this is filled in a car
    // with cold hands.
    Box(
        Modifier
            .size(44.dp)
            .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (on) accent else Look.ChalkFaint,
            fontFamily = Look.Figure, fontSize = 10.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .border(1.dp, if (on) accent else Look.RuleStrong, RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 5.dp),
        )
    }
}

/** What the save button is saying right now. */
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
