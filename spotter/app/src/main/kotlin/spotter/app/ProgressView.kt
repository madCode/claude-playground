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
import spotter.coach.Load
import spotter.coach.Progress
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ink = Color(0xFF16181D)
private val muted = Color(0xFF5B606B)
private val day = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

/**
 * What has gone up lately, and what is about to. Nothing is shown until there is something to
 * say: an empty heading on a new install would only be noise under the sheet.
 */
@Composable
fun ProgressView(progress: Progress) {
    val advice = progress.advice
    if (progress.wentUp.isEmpty() && progress.next.isEmpty() && advice.numbered == 0) return
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Progress", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ink, modifier = Modifier.semantics { heading() })
            if (progress.wentUp.isNotEmpty()) {
                Text("Went up in the last ${Progress.WEEKS} weeks", fontSize = 14.sp, color = muted, modifier = Modifier.semantics { heading() })
                progress.wentUp.forEach {
                    Line(
                        it.exercise,
                        "${it.from.label} → ${it.to.label}${reps(it.reps)} · ${it.on.format(day)}",
                        "${it.exercise}: up from ${words(it.from)} to ${words(it.to)}${reps(it.reps)}, on ${it.on.format(day)}",
                    )
                }
            }
            if (progress.next.isNotEmpty()) {
                Text("Next", fontSize = 14.sp, color = muted, modifier = Modifier.padding(top = if (progress.wentUp.isEmpty()) 0.dp else 4.dp).semantics { heading() })
                progress.next.forEach {
                    if (it.ready) {
                        Line(it.exercise, "${it.load.label}${reps(it.reps)} next time ▲", "${it.exercise}: up to ${words(it.load)}${reps(it.reps)} next time")
                    } else {
                        Line(
                            it.exercise,
                            "one more clean session at ${it.load.label}${reps(it.reps)}",
                            "${it.exercise}: one more clean session at ${words(it.load)}${reps(it.reps)} and it goes up",
                        )
                    }
                }
            }
            if (advice.numbered > 0) {
                Text(
                    "The sheet's numbers, last ${Progress.WEEKS} weeks",
                    fontSize = 14.sp,
                    color = muted,
                    modifier = Modifier.padding(top = if (progress.wentUp.isEmpty() && progress.next.isEmpty()) 0.dp else 4.dp).semantics { heading() },
                )
                // Counts in plain words: what happened to the numbers, not a grade of them.
                Text("${advice.asSuggested} of ${advice.numbered} done as suggested", fontSize = 16.sp, color = ink)
                listOfNotNull(
                    "${advice.heavier} lifted heavier".takeIf { advice.heavier > 0 },
                    "${advice.lighterOrShort} lighter or short".takeIf { advice.lighterOrShort > 0 },
                ).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), fontSize = 15.sp, color = muted) }
            }
        }
    }
}

private fun reps(reps: Int?) = reps?.let { " for $it" }.orEmpty()

// "2×25" read aloud is "two times twenty-five"; say what is held.
private fun words(load: Load) = when (load.count) {
    2 -> "two ${load.lb} pound dumbbells"
    1 -> "one ${load.lb} pound dumbbell"
    else -> "${load.lb} pounds"
}

@Composable
private fun Line(exercise: String, detail: String, spoken: String) {
    // One thing to hear per exercise, not its name and then a row of symbols.
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Text(exercise, fontSize = 16.sp, color = ink)
        Text(detail, fontSize = 15.sp, color = muted)
    }
}
