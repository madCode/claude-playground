package spotter.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spotter.coach.UnknownWord

private val ink = Color(0xFF16181D)
private val muted = Color(0xFF5B606B)

/** How many words the card lists; the rest are counted. */
internal const val MOST_WORDS = 8

/**
 * The words on the last week's boards that Spotter has no meaning for, the ones costing the
 * most rows first, each with a line it was on. Read only for now: it says what stands between
 * a row and its number. Hidden when every word was known.
 */
@Composable
fun UnknownWordsView(words: List<UnknownWord>) {
    if (words.isEmpty()) return
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Words Spotter doesn't know yet", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ink, modifier = Modifier.semantics { heading() })
            Text("From the last week's boards. A row with these says \"not sure\" and gets no number.", fontSize = 15.sp, color = muted)
            words.take(MOST_WORDS).forEach { w ->
                val rows = if (w.rows == 1) "1 row" else "${w.rows} rows"
                // One thing to hear per word: the word, how often, and where.
                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "${w.word}: $rows, as in ${w.line}" }) {
                    Text("${w.word} · $rows", fontSize = 16.sp, color = ink)
                    Text(w.line, fontSize = 15.sp, color = muted)
                }
            }
            if (words.size > MOST_WORDS) Text("and ${words.size - MOST_WORDS} more", fontSize = 15.sp, color = muted)
        }
    }
}
