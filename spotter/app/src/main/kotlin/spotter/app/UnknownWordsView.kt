package spotter.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spotter.app.today.Boards
import spotter.coach.UnknownWord
import spotter.coach.WordAnswer
import spotter.coach.WordAnswers

private val ink = Color(0xFF16181D)
private val muted = Color(0xFF5B606B)

/** How many words the card lists; the rest are counted. */
internal const val MOST_WORDS = 8

/** How many exercises a search offers: enough to find one, few enough to see past the keyboard. */
internal const val MOST_HITS = 6

// As much of a stranger's text as is worth hearing.
private const val SPOKEN = 80

/**
 * The words on the boards kept on the phone that Spotter has no meaning for, the ones seen on
 * the most days first, each with a line it was on, and the person's answers about them. A word
 * is sorted by tapping it: passed over from now on, or another name for an exercise picked
 * from the list. A word that is neither is left: it needs the lists themselves. Hidden when
 * every word was known and nothing has been answered.
 *
 * [reads] says how a line would read with an answer added, or null while it still wouldn't.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnknownWordsView(
    words: List<UnknownWord>,
    answers: List<WordAnswer> = emptyList(),
    movements: List<String> = emptyList(),
    reads: (line: String, answer: WordAnswer) -> String? = { _, _ -> null },
    onAnswer: (WordAnswer) -> Unit = {},
    onUndo: (String) -> Unit = {},
) {
    if (words.isEmpty() && answers.isEmpty()) return
    // Kept across a rotation: which word is open, and how far its answer has got.
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    // The answer waiting for a yes: "" passes the word over, else the exercise it names.
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    fun close() { open = null; searching = false; typed = ""; picked = null }
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (words.isNotEmpty()) {
                Text("Words Spotter doesn't know yet", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ink, modifier = Modifier.semantics { heading() })
                Text("From the last ${Boards.KEEP} boards on this phone. A row with these says \"not sure\" and gets no number. Tap one to say what it is.", fontSize = 15.sp, color = muted)
            }
            words.take(MOST_WORDS).forEach { w ->
                val days = if (w.days == 1) "1 day" else "${w.days} days"
                // One thing to hear per entry: the words, how often, and where. Two lines at
                // most: the text is a stranger's, and a long one mustn't push the rest away.
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClickLabel = "say what this word is") { if (open == w.word) close() else { close(); open = w.word } }
                        .semantics(mergeDescendants = true) { contentDescription = "${w.word.take(SPOKEN)}: on $days, as in ${w.line.take(SPOKEN)}" },
                ) {
                    Text("${w.word} · $days", fontSize = 16.sp, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(w.line, fontSize = 15.sp, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (open == w.word) {
                    val spoken = w.word.take(SPOKEN)
                    val choice = picked
                    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            choice != null -> {
                                val answer = WordAnswer(w.word, choice.ifEmpty { null })
                                val line = reads(w.line, answer)
                                val what = if (answer.movement == null) "\"${w.word}\" will be passed over on every board."
                                else "\"${w.word}\" will be read as ${answer.movement} on every board, with its history and weight."
                                val then = if (line != null) " This line will read as $line." else " This line stays \"not sure\": another word on it isn't known yet."
                                Text(what + then, fontSize = 15.sp, color = ink, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onAnswer(answer); close() }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "$spoken: yes, read it that way" }) { Text("Yes, read it that way") }
                                    OutlinedButton(onClick = { picked = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
                                }
                            }
                            searching -> {
                                OutlinedTextField(
                                    value = typed,
                                    onValueChange = { typed = it.take(WordAnswers.MAX_CHARS).replace("\n", " ") },
                                    label = { Text("Which exercise? Type part of its name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                val wanted = typed.trim().lowercase()
                                val hits = if (wanted.isEmpty()) emptyList() else movements.filter { wanted in it }.sortedBy { !it.startsWith(wanted) }.take(MOST_HITS)
                                if (wanted.isNotEmpty() && hits.isEmpty()) {
                                    Text("No exercise by that name.", fontSize = 15.sp, color = muted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    hits.forEach { name ->
                                        OutlinedButton(onClick = { picked = name }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "$spoken is another name for $name" }) { Text(name) }
                                    }
                                    OutlinedButton(onClick = { searching = false; typed = "" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
                                }
                            }
                            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { picked = "" }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "$spoken: ignore this word" }) { Text("Ignore this word") }
                                OutlinedButton(onClick = { searching = true }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "$spoken: it's another name for an exercise" }) { Text("It's another name for…") }
                                OutlinedButton(onClick = { close() }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "$spoken: neither, leave it" }) { Text("Neither: leave it") }
                            }
                        }
                    }
                }
            }
            if (words.size > MOST_WORDS) Text("and ${words.size - MOST_WORDS} more", fontSize = 15.sp, color = muted)
            if (answers.isNotEmpty()) {
                Text("Your answers", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ink, modifier = Modifier.semantics { heading() })
                // The newest first: the one most likely to be undone.
                answers.asReversed().forEach { a ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${a.word}: " + (a.movement?.let { "another name for $it" } ?: "ignored"),
                            fontSize = 15.sp, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { onUndo(a.word) }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Undo the answer for ${a.word.take(SPOKEN)}" }) { Text("Undo") }
                    }
                }
            }
        }
    }
}
