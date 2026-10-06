package com.app.jekyllposter.ui.editor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.jekyllposter.ui.editor.EditorViewModel.TermKind
import com.app.jekyllposter.ui.theme.TermPill
import com.app.jekyllposter.ui.theme.termColor
import com.app.jekyllposter.ui.theme.termInk

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TermRow(label: String, terms: List<String>, editable: Boolean, onAdd: (() -> Unit)?, onRemove: (String) -> Unit) {
    if (!editable && terms.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            terms.forEach { term ->
                InputChip(
                    selected = false,
                    enabled = editable,
                    colors = InputChipDefaults.inputChipColors(
                        containerColor = termColor(term), disabledContainerColor = termColor(term),
                        labelColor = termInk(), disabledLabelColor = termInk(), trailingIconColor = termInk(),
                    ),
                    onClick = { onRemove(term) },
                    label = { Text(term) },
                    trailingIcon = if (editable) ({ Icon(Icons.Default.Close, "Remove $term") }) else null,
                )
            }
            if (editable && onAdd != null) {
                AssistChip(
                    onClick = onAdd,
                    label = { Text(if (terms.isEmpty()) "Add" else "More") },
                    leadingIcon = { Icon(Icons.Default.Add, null) },
                    modifier = Modifier.semantics { contentDescription = "Add ${label.lowercase().removeSuffix("s").replace("categorie", "category")}" },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TermPicker(kind: TermKind, viewModel: EditorViewModel, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val noun = if (kind == TermKind.Category) "category" else "tag"
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Recomputed on each keystroke and each change to the post's picks.
    val state by viewModel.state.collectAsStateWithLifecycle()
    // What was typed, as it would be added: a leading # (habit from elsewhere) isn't part of a tag.
    val term = query.trim().trimStart('#').trim()
    val suggestions = remember(term, state) { viewModel.suggestions(kind, term) }
    val exact = suggestions.any { it.name.equals(term, ignoreCase = true) }
    val picked = viewModel.text?.let { if (kind == TermKind.Category) it.categories else it.tags }.orEmpty()
    val already = picked.any { it.equals(term, ignoreCase = true) }
    val add = { name: String -> viewModel.add(kind, name); query = "" }
    // The blog's spelling when it has the term already, so "Rain" doesn't sit beside "rain". Reads
    // the field when called, not when composed: the sheet keeps the first dismiss callback it's given.
    val addTyped = {
        val typed = query.trim().trimStart('#').trim()
        if (typed.isNotEmpty()) add(viewModel.suggestions(kind, typed).firstOrNull { it.name.equals(typed, ignoreCase = true) }?.name ?: typed)
    }
    // However the sheet is closed, what's typed and not yet added goes in rather than being dropped.
    val done = { addTyped(); onDismiss() }
    ModalBottomSheet(onDismissRequest = done, sheetState = sheet, containerColor = MaterialTheme.colorScheme.background) {
        // The post's own picks, so one just added is seen landing here rather than vanishing
        // from the suggestions below. Capped, so many of them can't push the field off a short screen.
        if (picked.isNotEmpty()) {
            Box(Modifier.heightIn(max = 112.dp).verticalScroll(rememberScrollState()).testTag("picked")) {
                TermRow("On this post", picked, editable = true, onAdd = null, onRemove = { viewModel.remove(kind, it) })
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(if (kind == TermKind.Category) "Find or add a category" else "Find or add a tag") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addTyped() }),
                modifier = Modifier.fillMaxWidth().testTag("termQuery"),
            )
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            if (already) {
                item {
                    Text(
                        "“$term” is already on this post.",
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (term.isNotEmpty() && !exact) {
                item {
                    Row(Modifier.fillMaxWidth().clickable { add(term) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, null, Modifier.padding(end = 12.dp))
                        Text("Add “$term” as a new $noun")
                    }
                }
            }
            if (suggestions.isEmpty() && term.isEmpty()) {
                item {
                    Text(
                        "Your blog has no ${noun}s yet that this post doesn't already have. Type one to add it.",
                        Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(suggestions, key = { it.name }) { term ->
                Row(Modifier.fillMaxWidth().clickable { add(term.name) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { TermPill(term.name) }
                    Text(if (term.count == 1) "1 post" else "${term.count} posts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TextButton(onClick = done, modifier = Modifier.align(Alignment.End).padding(8.dp)) { Text("Done") }
    }
}
