package spotter.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spotter.coach.Load
import spotter.coach.Past
import spotter.coach.PastClass
import spotter.coach.PastRow
import spotter.coach.Reading
import java.time.format.DateTimeFormatter
import java.util.Locale

private val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val shortDay = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** How many classes the list shows at once; "Show earlier" brings the next lot. */
internal const val CLASSES_SHOWN = 30

/**
 * Looking back, read only: the classes done, one class's rows, and one lift over time. Which of
 * the three shows is the caller's to keep ([openClass], [openLift]), so the sheet can open a
 * lift here and a rotation loses nothing. The phone's back button steps out of each.
 */
@Composable
fun PastView(
    classes: List<PastClass>,
    openClass: String?,
    openLift: String?,
    shown: Int,
    onClass: (String?) -> Unit,
    onLift: (String?) -> Unit,
    onMore: () -> Unit,
    /** Today's rows, so one never done in its own words still opens what its number is read from. */
    sheet: List<Reading.Known> = emptyList(),
) {
    val lift = openLift?.let { key -> Past.lift(key, classes, sheet).takeIf { it.isNotEmpty() } }
    val cls = classes.firstOrNull { it.id == openClass }
    when {
        lift != null -> {
            BackHandler { onLift(null) }
            Back(if (cls != null) "${cls.className} · ${cls.date.format(day)}" else "History") { onLift(null) }
            val name = Past.name(openLift, classes, sheet) ?: lift.first().row.name
            Text(name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = palette.ink, modifier = Modifier.semantics { heading() })
            Text("Each time it was done, its other wordings that take the same dumbbells included.", fontSize = 14.sp, color = palette.muted)
            lift.forEach { s ->
                Held(s.row, title = s.date.format(shortDay), under = listOfNotNull(s.row.name.takeIf { it != name }, "imported".takeIf { s.imported }).joinToString(" · "), onClick = null)
            }
        }
        cls != null -> {
            BackHandler { onClass(null) }
            Back("History") { onClass(null) }
            Text("${cls.className} · ${cls.date.format(day)}", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = palette.ink, modifier = Modifier.semantics { heading() })
            if (cls.imported) Text("From the imported history.", fontSize = 14.sp, color = palette.muted)
            cls.rows.forEach { row -> Held(row, title = row.name, under = "", onClick = { onLift(row.logged.key) }) }
        }
        classes.isNotEmpty() -> {
            Text("Past classes", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = palette.ink, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
            classes.take(shown).forEach { c ->
                val rows = if (c.rows.size == 1) "1 row" else "${c.rows.size} rows"
                Surface(
                    color = palette.card, shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = "open this class") { onClass(c.id) },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${c.date.format(day)} · ${c.className}", fontSize = 17.sp, color = palette.ink, fontWeight = FontWeight.SemiBold)
                            Text(rows + if (c.imported) " · imported" else " logged", fontSize = 14.sp, color = palette.muted)
                        }
                        Text("›", fontSize = 20.sp, color = palette.muted, modifier = Modifier.clearAndSetSemantics { })
                    }
                }
            }
            if (classes.size > shown) {
                OutlinedButton(onClick = onMore, modifier = Modifier.heightIn(min = 48.dp)) { Text("Show earlier classes (${classes.size - shown} more)") }
            }
        }
    }
}

@Composable
private fun Back(to: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Back to $to" }) { Text("‹ $to") }
}

// What was held leads, as on the sheet; what was done with it is beneath. Heard as one thing.
@Composable
private fun Held(row: PastRow, title: String, under: String, onClick: (() -> Unit)?) {
    val load = row.logged.load
    val spoken = listOf(title, words(load), row.detail, under).filter { it.isNotEmpty() }.joinToString(", ")
    Surface(
        color = palette.card, shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "see this lift over time", onClick = onClick) else Modifier)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(load.count?.let { "$it×${load.lb}" } ?: "${load.lb} lb", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.ink, modifier = Modifier.widthIn(min = 84.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 17.sp, color = palette.ink)
                listOf(row.detail, under).filter { it.isNotEmpty() }.joinToString(" · ").takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 14.sp, color = palette.muted) }
            }
            if (onClick != null) Text("›", fontSize = 20.sp, color = palette.muted)
        }
    }
}

private fun words(load: Load) = when (load.count) {
    2 -> "two ${load.lb} pound dumbbells"
    1 -> "one ${load.lb} pound dumbbell"
    else -> "${load.lb} pounds, one or two not said"
}
