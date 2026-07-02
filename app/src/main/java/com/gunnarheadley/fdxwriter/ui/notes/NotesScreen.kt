package com.gunnarheadley.fdxwriter.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnarheadley.fdxwriter.data.fdx.FdxColor
import com.gunnarheadley.fdxwriter.data.fdx.NoteAnnotation
import com.gunnarheadley.fdxwriter.data.fdx.ScreenplayParagraph
import com.gunnarheadley.fdxwriter.data.fdx.ScriptOffsetMapper
import com.gunnarheadley.fdxwriter.ui.ScriptViewModel
import com.gunnarheadley.fdxwriter.ui.common.ColorPicker

/** Review/add/edit/remove Final Draft script notes, anchored to the script by character offset. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(viewModel: ScriptViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val doc = state.document ?: return
    val notes = doc.model.notes.sortedBy { it.start }
    val paragraphs = doc.model.paragraphs
    val mapper = remember(doc) { doc.offsetMapper() }

    var editing by remember { mutableStateOf<NoteAnnotation?>(null) }
    var pendingEditId by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    // When a freshly added note appears in the model, open its editor.
    LaunchedEffect(pendingEditId, notes) {
        val id = pendingEditId ?: return@LaunchedEffect
        notes.firstOrNull { it.id == id }?.let {
            editing = it
            pendingEditId = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notes") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = { viewModel.save() }, enabled = state.isDirty) {
                        Text(if (state.isDirty) "Save*" else "Saved")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { pendingEditId = viewModel.addNote() }) {
                Text("+", fontSize = 28.sp)
            }
        },
    ) { padding ->
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No notes yet. Place the cursor in a line, then tap + to add one.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        anchor = anchorSnippet(note, mapper, paragraphs),
                        onClick = { viewModel.revealNote(note); onBack() },
                    )
                }
            }
        }
    }

    editing?.let { note ->
        NoteEditDialog(
            note = note,
            onDismiss = { editing = null },
            onSave = { body, type, color ->
                viewModel.updateNote(note.id, body, type, color)
                editing = null
            },
            onDelete = {
                viewModel.deleteNote(note.id)
                editing = null
            },
        )
    }
}

private fun anchorSnippet(
    note: NoteAnnotation,
    mapper: ScriptOffsetMapper,
    paragraphs: List<ScreenplayParagraph>,
): String {
    val index = mapper.offsetToPosition(note.start).paragraphIndex
    val text = paragraphs.getOrNull(index)?.plainText?.trim().orEmpty()
    return when {
        text.isBlank() -> "blank line"
        text.length > 50 -> text.take(50) + "\u2026"
        else -> text
    }
}

@Composable
private fun NoteCard(note: NoteAnnotation, anchor: String, onClick: () -> Unit) {
    val dotColor = FdxColor.toArgb(note.color)?.let { Color(it) } ?: Color.White
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(1.dp, Color(0x33000000), CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(
                    note.bodyText.ifBlank { "(empty note)" },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "\u201C$anchor\u201D",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val meta = listOfNotNull(
                    note.type.ifBlank { null },
                    note.writerName.ifBlank { null },
                ).joinToString(" \u00B7 ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun NoteEditDialog(
    note: NoteAnnotation,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onDelete: () -> Unit,
) {
    var body by remember { mutableStateOf(note.bodyText) }
    var type by remember { mutableStateOf(note.type) }
    var color by remember { mutableStateOf(note.color) }

    // A note with no text and no type carries no information; discard it instead of keeping it.
    val cancel = { if (note.bodyText.isBlank() && note.type.isBlank()) onDelete() else onDismiss() }

    AlertDialog(
        onDismissRequest = cancel,
        title = { Text("Edit Note") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Type (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Color", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                ColorPicker(selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (body.isBlank() && type.isBlank()) onDelete() else onSave(body, type, color)
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = cancel) { Text("Cancel") }
            }
        },
    )
}
