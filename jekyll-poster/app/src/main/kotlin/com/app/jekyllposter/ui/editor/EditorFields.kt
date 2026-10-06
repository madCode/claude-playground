package com.app.jekyllposter.ui.editor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.jekyllposter.core.jekyll.parseJekyllDate
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The date a shared note carries, which the post keeps; dropping it dates the post when published.
 * Not offered once a commit was tried: the post then keeps its name and date, so it can't go twice.
 */
@Composable
internal fun NoteDate(written: String, canDrop: Boolean, onDrop: () -> Unit) {
    // The day as written: the site's time zone, which can move it, is applied when publishing.
    val day = parseJekyllDate(written, ZoneOffset.UTC)?.let {
        LocalDate.parse(written.trim().take(10)).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
    }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (day != null) "Dated $day, from the note" else "The note's date, “$written”, isn't one the app can read: it's dated when published",
            Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canDrop && day != null) TextButton(onClick = onDrop) { Text("Use the publish day") }
    }
}

/** Asks for the Obsidian vault folder, once, so a shared note's photos can be found in it. */
@Composable
internal fun VaultBanner(count: Int, onChoose: () -> Unit, onSkip: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                if (count == 1) "This note has a photo from Obsidian. Choose your vault folder, once, and it's added from there."
                else "This note has $count photos from Obsidian. Choose your vault folder, once, and they're added from there.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = onSkip) { Text("Not now") }
                TextButton(onClick = onChoose) { Text("Choose folder") }
            }
        }
    }
}

/**
 * The post's other front matter as YAML, folded to a line naming its keys. For the writer who
 * wants `image:` or `excerpt:` without leaving the phone.
 */
@Composable
internal fun MoreFrontMatter(
    yaml: String,
    problem: String?,
    editable: Boolean,
    onChange: (String) -> Unit,
    forceOpen: Boolean = false,
    onOpened: () -> Unit = {},
    note: String? = null,
) {
    if (!editable && yaml.isBlank() && note == null) return
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(forceOpen) { if (forceOpen) { open = true; onOpened() } }
    val keys = remember(yaml) {
        yaml.lines().mapNotNull { Regex("""^([^\s#\-][^:]*):""").find(it)?.groupValues?.get(1) }
    }
    Row(
        Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Front matter", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
        Text(
            if (keys.isEmpty()) "Add image, excerpt…" else keys.joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
            color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (open) "Fold front matter" else "Show front matter")
    }
    if (open) {
        OutlinedTextField(
            value = yaml,
            onValueChange = onChange,
            readOnly = !editable,
            placeholder = { Text("image: /assets/images/cover.jpg\nexcerpt: A line for the home page", fontFamily = FontFamily.Monospace) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            isError = problem != null,
            supportingText = { Text(note ?: problem ?: "YAML, as at the top of the post. Title, date, categories and tags have their own places.") },
            minLines = 3,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("frontMatter"),
        )
    }
}

/** Asks for a just-added photo's alt text: what a screen reader says, and what shows if it won't load. */
@Composable
internal fun DescribePhoto(sitePath: String, onDone: (String) -> Unit) {
    var alt by remember(sitePath) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onDone("") },
        title = { Text("Describe the photo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("For people using screen readers, and for when it doesn't load.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = alt, onValueChange = { alt = it }, placeholder = { Text("A loaf of sourdough on a board") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onDone(alt) }),
                    modifier = Modifier.fillMaxWidth().testTag("altText"),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDone(alt) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = { onDone("") }) { Text("Skip") } },
    )
}
