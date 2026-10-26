package com.app.jekyllposter.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.jekyllposter.core.webmention.WebmentionIo.Kind
import com.app.jekyllposter.core.webmention.WebmentionIo.Received
import com.app.jekyllposter.ui.editor.EditorViewModel.Mentions

/** The post's webmentions on webmention.io: a line counting them, opening to who and what. */
@Composable
internal fun MentionsSection(mentions: Mentions) {
    var open by rememberSaveable { mutableStateOf(false) }
    val received = (mentions as? Mentions.Loaded)?.received.orEmpty()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(enabled = received.isNotEmpty(), onClickLabel = if (open) "Fold mentions" else "Show mentions") { open = !open }
            .semantics { if (received.isNotEmpty()) stateDescription = if (open) "Open" else "Folded" }
            .padding(horizontal = 16.dp, vertical = 10.dp).testTag("mentions"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Mentions", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
        Text(
            when (mentions) {
                Mentions.Loading -> "Asking webmention.io…"
                Mentions.Failed -> "Couldn't read webmention.io just now"
                is Mentions.Loaded -> mentionCounts(received) ?: "None on webmention.io yet"
            },
            style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (received.isNotEmpty()) Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
    }
    if (open) {
        val uri = LocalUriHandler.current
        Column {
            received.forEach { m ->
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .then(m.url?.let { url -> Modifier.clickable(onClickLabel = "Open on their site") { uri.openUri(url) } } ?: Modifier)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(mentionLine(m), style = MaterialTheme.typography.bodyMedium)
                    m.text?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

/** "2 replies · 5 likes", most said first; null with none. */
internal fun mentionCounts(received: List<Received>): String? = received.groupingBy { it.kind }.eachCount().entries
    .sortedBy { it.key.ordinal }
    .joinToString(" · ") { (kind, n) ->
        val (one, many) = when (kind) {
            Kind.Reply -> "reply" to "replies"
            Kind.Like -> "like" to "likes"
            Kind.Repost -> "repost" to "reposts"
            Kind.Bookmark -> "bookmark" to "bookmarks"
            Kind.Rsvp -> "RSVP" to "RSVPs"
            Kind.Mention -> "mention" to "mentions"
        }
        "$n ${if (n == 1) one else many}"
    }.ifEmpty { null }

/** "Ada replied", or "Someone liked it" for an author with no name. */
internal fun mentionLine(m: Received): String {
    // Isolated, so a right-to-left name doesn't carry the words after it along with it.
    val who = m.author?.let { "\u2068$it\u2069" } ?: "Someone"
    return when (m.kind) {
        Kind.Reply -> "$who replied"
        Kind.Like -> "$who liked it"
        Kind.Repost -> "$who reposted it"
        Kind.Bookmark -> "$who bookmarked it"
        Kind.Rsvp -> "$who replied to the invitation"
        Kind.Mention -> "$who linked to it"
    }
}
