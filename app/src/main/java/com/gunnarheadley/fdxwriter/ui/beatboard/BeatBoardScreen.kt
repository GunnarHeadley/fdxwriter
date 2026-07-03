package com.gunnarheadley.fdxwriter.ui.beatboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnarheadley.fdxwriter.data.fdx.Beat
import com.gunnarheadley.fdxwriter.data.fdx.FdxColor
import com.gunnarheadley.fdxwriter.ui.ScriptViewModel
import com.gunnarheadley.fdxwriter.ui.common.ColorPicker
import kotlin.math.roundToInt

private const val MIN_ZOOM = 0.3f
private const val MAX_ZOOM = 6f
private const val DEFAULT_ZOOM = 2f
private const val MIN_BEAT_SIZE = 120f

/** Pan/zoom beat board: draggable cards backed by the FDX ListItems + Beat DisplayBoard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeatBoardScreen(viewModel: ScriptViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val doc = state.document ?: return
    val beats = doc.model.beats

    var editing by remember { mutableStateOf<Beat?>(null) }
    var pendingEditId by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    // When a freshly added beat appears in the model, open its editor.
    LaunchedEffect(pendingEditId, beats) {
        val id = pendingEditId ?: return@LaunchedEffect
        beats.firstOrNull { it.id == id }?.let {
            editing = it
            pendingEditId = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beat Board") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = { viewModel.save() }, enabled = state.isDirty) {
                        Text(if (state.isDirty) "Save*" else "Saved")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { pendingEditId = viewModel.addBeat() }) {
                Text("+", fontSize = 28.sp)
            }
        },
    ) { padding ->
        BeatCanvas(
            beats = beats,
            modifier = Modifier.padding(padding),
            onMove = viewModel::moveBeat,
            onResize = viewModel::resizeBeat,
            onEdit = { editing = it },
        )
    }

    editing?.let { beat ->
        BeatEditDialog(
            beat = beat,
            onDismiss = { editing = null },
            onSave = { title, lines, color ->
                viewModel.updateBeat(beat.id, title, lines, color)
                editing = null
            },
            onDelete = {
                viewModel.deleteBeat(beat.id)
                editing = null
            },
        )
    }
}

@Composable
private fun BeatCanvas(
    beats: List<Beat>,
    modifier: Modifier = Modifier,
    onMove: (String, Int, Int) -> Unit,
    onResize: (String, Int, Int) -> Unit,
    onEdit: (Beat) -> Unit,
) {
    var scale by remember { mutableStateOf(0f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var selectedId by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                // Tapping empty canvas clears the selection.
                detectTapGestures { selectedId = null }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoom, _ ->
                    scale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    pan += panChange
                }
            },
    ) {
        val vpW = constraints.maxWidth.toFloat()
        val vpH = constraints.maxHeight.toFloat()

        // Pick an initial zoom/centre once so cards are a usable size; the user pans/zooms after.
        LaunchedEffect(beats.isNotEmpty(), vpW, vpH) {
            if (scale != 0f) return@LaunchedEffect
            scale = DEFAULT_ZOOM
            pan = if (beats.isEmpty()) {
                Offset(vpW / 2f, vpH / 2f)
            } else {
                // Anchor the top-left of the card cluster near the viewport's top-left.
                val minL = beats.minOf { it.left }.toFloat()
                val minT = beats.minOf { it.top }.toFloat()
                Offset(48f - minL * DEFAULT_ZOOM, 48f - minT * DEFAULT_ZOOM)
            }
        }

        if (scale > 0f) {
            for (beat in beats) {
                key(beat.id) {
                    BeatCardView(
                        beat = beat,
                        scale = scale,
                        pan = pan,
                        selected = beat.id == selectedId,
                        onSelect = { selectedId = beat.id },
                        onMove = onMove,
                        onResize = onResize,
                        onEdit = onEdit,
                    )
                }
            }
        }

        if (beats.isEmpty()) {
            Text(
                "No beats yet. Tap + to add one.",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BeatCardView(
    beat: Beat,
    scale: Float,
    pan: Offset,
    selected: Boolean,
    onSelect: () -> Unit,
    onMove: (String, Int, Int) -> Unit,
    onResize: (String, Int, Int) -> Unit,
    onEdit: (Beat) -> Unit,
) {
    val density = LocalDensity.current
    var pos by remember(beat.id, beat.left, beat.top) {
        mutableStateOf(Offset(beat.left.toFloat(), beat.top.toFloat()))
    }
    var cardSize by remember(beat.id, beat.width, beat.height) {
        mutableStateOf(Size(beat.width.toFloat(), beat.height.toFloat()))
    }
    val cardColor = FdxColor.toArgb(beat.color)?.let { Color(it) } ?: Color.White
    val titleSp = (cardSize.height * scale * 0.18f).coerceIn(9f, 18f).sp
    val bodySp = (cardSize.height * scale * 0.13f).coerceIn(8f, 14f).sp

    // Read pos/scale/pan during composition so each drag delta re-places the card live. Reading
    // them only inside offset { } (layout phase) left the card visually stationary until the drag
    // ended and the model commit forced a recomposition.
    val offsetX = (pos.x * scale + pan.x).roundToInt()
    val offsetY = (pos.y * scale + pan.y).roundToInt()

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX, offsetY) }
            .size(
                width = with(density) { (cardSize.width * scale).toDp() },
                height = with(density) { (cardSize.height * scale).toDp() },
            )
            .clip(RoundedCornerShape(6.dp))
            .background(cardColor)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color(0x33000000),
                shape = RoundedCornerShape(6.dp),
            )
            .pointerInput(beat.id, selected) {
                // Tap an unselected card to select it; tap the selected card to edit it.
                detectTapGestures { if (selected) onEdit(beat) else onSelect() }
            }
            .then(
                // Only a selected card consumes drags to move; otherwise the drag falls through so
                // the canvas pans instead of the card moving accidentally.
                if (selected) {
                    Modifier.pointerInput(beat.id, scale, beat.left, beat.top) {
                        detectDragGestures(
                            onDragEnd = { onMove(beat.id, pos.x.roundToInt(), pos.y.roundToInt()) },
                        ) { change, drag ->
                            change.consume()
                            pos += drag / scale
                        }
                    }
                } else {
                    Modifier
                },
            )
            .padding(6.dp),
    ) {
        Column {
            if (beat.title.isNotEmpty()) {
                Text(
                    beat.title,
                    color = Color(0xFF1A1A1A),
                    fontWeight = FontWeight.Bold,
                    fontSize = titleSp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val body = beat.bodyLines.joinToString("\n").trim()
            if (body.isNotEmpty()) {
                Text(
                    body,
                    color = Color(0xFF333333),
                    fontSize = bodySp,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (selected) {
            // Drag this corner grip to resize the card; the top-left stays anchored.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomEnd = 6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
                    .pointerInput(beat.id, scale, beat.width, beat.height) {
                        detectDragGestures(
                            onDragEnd = {
                                onResize(beat.id, cardSize.width.roundToInt(), cardSize.height.roundToInt())
                            },
                        ) { change, drag ->
                            change.consume()
                            cardSize = Size(
                                (cardSize.width + drag.x / scale).coerceAtLeast(MIN_BEAT_SIZE),
                                (cardSize.height + drag.y / scale).coerceAtLeast(MIN_BEAT_SIZE),
                            )
                        }
                    },
            )
        }
    }
}

@Composable
private fun BeatEditDialog(
    beat: Beat,
    onDismiss: () -> Unit,
    onSave: (String, List<String>, String) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember { mutableStateOf(beat.title) }
    var body by remember { mutableStateOf(beat.bodyLines.joinToString("\n")) }
    var color by remember { mutableStateOf(beat.color) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Beat") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text("Color", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                ColorPicker(selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, body.split("\n"), color) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
