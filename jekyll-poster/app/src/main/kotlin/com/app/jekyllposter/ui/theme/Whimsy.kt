package com.app.jekyllposter.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** How lists of posts are drawn. */
enum class Rows { Lines, Dots, Cards }

/** A section heading with the style's mark before it and, if it has one, a squiggle under it. */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    val whimsy = LocalWhimsy.current
    val color = MaterialTheme.colorScheme.primary
    Text(
        whimsy.mark + text,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        // The mark is decoration; TalkBack hears just the heading.
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 6.dp)
            .semantics { heading(); contentDescription = text }
            .drawWithCache {
                // Built once per size, not on every draw.
                val y = size.height + 3.dp.toPx()
                val wave = 3.dp.toPx()
                val period = 6.dp.toPx()
                val path = Path().apply {
                    moveTo(0f, y)
                    var x = 0f
                    while (x <= size.width) {
                        lineTo(x, y + wave * sin(x / period * PI.toFloat() / 2f))
                        x += 2f
                    }
                }
                val stroke = Stroke(width = 1.5.dp.toPx())
                onDrawBehind { if (whimsy.squiggle) drawPath(path, color.copy(alpha = 0.55f), style = stroke) }
            },
    )
}

/** Between rows, in the style's manner; nothing for cards, which are apart already. */
@Composable
fun RowDivider() {
    when (LocalWhimsy.current.rows) {
        Rows.Lines -> HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        Rows.Dots -> {
            val color = MaterialTheme.colorScheme.outlineVariant
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(vertical = 1.dp).drawWithCache {
                    val dots = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))
                    val width = 2.dp.toPx()
                    onDrawBehind { drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = width, pathEffect = dots) }
                },
            )
        }
        Rows.Cards -> Unit
    }
}

/** A row's frame: a card for [Rows.Cards], plain otherwise. */
@Composable
fun RowFrame(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (LocalWhimsy.current.rows == Rows.Cards) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) { Column(modifier, content = content) }
    } else {
        Column(modifier, content = content)
    }
}

/** Which of [size] colours a term gets. floorMod, not abs(): abs(Int.MIN_VALUE) is negative, and real names hash to it. */
internal fun termSlot(term: String, size: Int): Int = Math.floorMod(term.lowercase().hashCode(), size)

/** A category or tag's own colour, the same everywhere it appears. */
@Composable
fun termColor(term: String): Color {
    val whimsy = LocalWhimsy.current
    val palette = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) whimsy.paletteDark else whimsy.palette
    if (palette.isEmpty()) return MaterialTheme.colorScheme.surfaceContainerHigh
    return palette[termSlot(term, palette.size)]
}

/** Text on a term's colour: near-black ink on the light pills, near-white on the dark ones. */
@Composable
fun termInk(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFFF4EC) else Color(0xFF231815)

/** A small coloured label for a category, as on a post row. */
@Composable
fun TermPill(term: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    val base = Modifier.background(if (term == "All") MaterialTheme.colorScheme.surfaceContainerHigh else termColor(term), shape)
        .let { if (selected) it.border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), shape) else it }
        .let {
            if (onClick == null) it else it
                // A comfortable target without making the pill itself bigger.
                .clip(shape).clickable(onClickLabel = if (term == "All") "Show all posts" else "Show posts in $term", onClick = onClick)
                .semantics { this.selected = selected }
        }
    Text(
        term,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        color = termInk(),
        modifier = base.padding(horizontal = if (onClick != null) 12.dp else 8.dp, vertical = if (onClick != null) 6.dp else 2.dp),
    )
}

/**
 * The main button. Styles with [Whimsy.stamp] draw it as a tilted rubber stamp with a hard,
 * offset shadow, like a print slightly out of register.
 */
@Composable
fun PosterFab(text: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    val whimsy = LocalWhimsy.current
    if (!whimsy.stamp) {
        ExtendedFloatingActionButton(text = { Text(text) }, icon = icon, onClick = onClick, modifier = Modifier.rotate(whimsy.tilt))
        return
    }
    val shape = MaterialTheme.shapes.large
    val shadow = MaterialTheme.colorScheme.secondary
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ink = (if (dark) whimsy.stampInkDark else whimsy.stampInk) ?: MaterialTheme.colorScheme.primary
    Box(Modifier.rotate(whimsy.tilt)) {
        Box(Modifier.matchParentSize().offset(4.dp, 4.dp).background(shadow, shape))
        ExtendedFloatingActionButton(
            text = { Text(text, style = MaterialTheme.typography.titleMedium) },
            icon = icon,
            onClick = onClick,
            shape = shape,
            containerColor = ink,
            contentColor = if (whimsy.stampInk != null) whimsy.stampText else MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            modifier = Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground), shape),
        )
    }
}

/** The editor's main action: a small pill in the stamp's ink, the moment that matters. */
@Composable
fun InkButton(text: String, onClick: () -> Unit) {
    val whimsy = LocalWhimsy.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ink = (if (dark) whimsy.stampInkDark else whimsy.stampInk)
    if (ink == null) {
        androidx.compose.material3.TextButton(onClick = onClick) { Text(text) }
        return
    }
    androidx.compose.material3.Button(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = ink, contentColor = whimsy.stampText),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onBackground),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        modifier = Modifier.padding(end = 4.dp),
    ) { Text(text, maxLines = 1, style = MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) }
}
