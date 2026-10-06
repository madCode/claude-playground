package com.app.jekyllposter.ui.editor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.jekyllposter.core.jekyll.Edit
import com.app.jekyllposter.core.jekyll.MarkdownEdits
import com.app.jekyllposter.data.CachedPost

/**
 * Posts to complete a `[[link` with, above the keyboard; none says what happens to the link. At
 * most three rows tall, scrolling for more, so the line being typed isn't covered, and set off by
 * a rule for e-ink.
 */
@Composable
internal fun LinkSuggestions(posts: List<CachedPost>, query: String, anyPosts: Boolean, onPick: (CachedPost) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.semantics { contentDescription = "Posts to link"; liveRegion = LiveRegionMode.Polite }) {
            HorizontalDivider()
            if (posts.isEmpty()) {
                Text(
                    when {
                        !anyPosts -> "No posts on the blog to link yet."
                        else -> "No post titled like that: it stays as written, [[$query]]."
                    },
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Three rows tall, scrolling for more: taller, it would cover the line being typed.
            Column(Modifier.heightIn(max = 168.dp).verticalScroll(rememberScrollState())) {
                posts.forEach { post ->
                    Column(
                        Modifier.fillMaxWidth().clickable(onClickLabel = "Link to this post") { onPick(post) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(post.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        post.date?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

/**
 * Markdown at the cursor, above the keyboard; bold, italic, code and the line prefixes undo
 * themselves when pressed again. Formatting needs the body focused; a photo can go in any time.
 */
@Composable
internal fun FormatBar(formatting: Boolean, addingPhoto: Boolean, onFormat: ((Edit) -> Edit) -> Unit, onPickPhoto: () -> Unit, onTakePhoto: () -> Unit) {
    var photoMenu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
            IconButton(enabled = formatting, onClick = { onFormat { MarkdownEdits.wrap(it, "**") } }) { Icon(Icons.Default.FormatBold, "Bold") }
            IconButton(enabled = formatting, onClick = { onFormat { MarkdownEdits.wrap(it, "_") } }) { Icon(Icons.Default.FormatItalic, "Italic") }
            IconButton(enabled = formatting, onClick = { onFormat(MarkdownEdits::link) }) { Icon(Icons.Default.Link, "Link") }
            IconButton(enabled = formatting, onClick = { onFormat(MarkdownEdits::postLink) }) { Icon(Icons.Default.AddLink, "Link to a post") }
            IconButton(enabled = formatting, onClick = { onFormat { MarkdownEdits.linePrefix(it, "## ") } }) { Icon(Icons.Default.Title, "Heading") }
            IconButton(enabled = formatting, onClick = { onFormat { MarkdownEdits.linePrefix(it, "- ") } }) { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, "List") }
            IconButton(enabled = formatting, onClick = { onFormat { MarkdownEdits.linePrefix(it, "> ") } }) { Icon(Icons.Default.FormatQuote, "Quote") }
            IconButton(enabled = formatting, onClick = { onFormat { MarkdownEdits.wrap(it, "`") } }) { Icon(Icons.Default.Code, "Code") }
            if (addingPhoto) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp).semantics { contentDescription = "Adding the photo" }, strokeWidth = 2.dp)
            } else {
                Box {
                    IconButton(onClick = { photoMenu = true }, modifier = Modifier.semantics { onClick("Choose or take a photo") { photoMenu = true; true } }) {
                        Icon(Icons.Default.AddPhotoAlternate, "Add a photo")
                    }
                    DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Choose photos") },
                            leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) },
                            onClick = { photoMenu = false; onPickPhoto() },
                        )
                        DropdownMenuItem(
                            text = { Text("Take a photo") },
                            leadingIcon = { Icon(Icons.Default.PhotoCamera, null) },
                            onClick = { photoMenu = false; onTakePhoto() },
                        )
                    }
                }
            }
        }
    }
}
