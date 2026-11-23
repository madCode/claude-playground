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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spotter.app.today.Boards
import spotter.coach.UnknownWord

private val ink = Color(0xFF16181D)
private val muted = Color(0xFF5B606B)

/** How many words the card lists; the rest are counted. */
internal const val MOST_WORDS = 8

// As much of a stranger's text as is worth hearing.
private const val SPOKEN = 80

/**
 * The words on the boards kept on the phone that Spotter has no meaning for, the ones seen on
 * the most days first, each with a line it was on. Read only for now: it says what stands between
 * a row and its number. Hidden when every word was known.
 */
@Composable
fun UnknownWordsView(words: List<UnknownWord>) {
    if (words.isEmpty()) return
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Words Spotter doesn't know yet", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ink, modifier = Modifier.semantics { heading() })
            Text("From the last ${Boards.KEEP} boards on this phone. A row with these says \"not sure\" and gets no number.", fontSize = 15.sp, color = muted)
            words.take(MOST_WORDS).forEach { w ->
                val days = if (w.days == 1) "1 day" else "${w.days} days"
                // One thing to hear per entry: the words, how often, and where. Two lines at
                // most: the text is a stranger's, and a long one mustn't push the rest away.
                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "${w.word.take(SPOKEN)}: on $days, as in ${w.line.take(SPOKEN)}" }) {
                    Text("${w.word} · $days", fontSize = 16.sp, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(w.line, fontSize = 15.sp, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (words.size > MOST_WORDS) Text("and ${words.size - MOST_WORDS} more", fontSize = 15.sp, color = muted)
        }
    }
}
