package com.gunnarheadley.fdxwriter.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnarheadley.fdxwriter.data.fdx.ElementType
import com.gunnarheadley.fdxwriter.data.fdx.FdxColor
import com.gunnarheadley.fdxwriter.data.fdx.NoteAnnotation
import com.gunnarheadley.fdxwriter.data.fdx.ScreenplayParagraph
import com.gunnarheadley.fdxwriter.data.fdx.ScriptStats
import com.gunnarheadley.fdxwriter.data.fdx.StyledRun
import com.gunnarheadley.fdxwriter.ui.ScriptViewModel
import com.gunnarheadley.fdxwriter.ui.SearchUiState
import com.gunnarheadley.fdxwriter.ui.notes.NoteEditDialog
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Accessors a focused paragraph field exposes so the format bar can read/modify its text. */
class FocusedField(
    val key: String,
    val getValue: () -> TextFieldValue,
    val setValue: (TextFieldValue) -> Unit,
)

/** SmartType suggestions offered for scene headings and transitions. */
private val SCENE_OPENERS = listOf("INT. ", "EXT. ", "INT./EXT. ", "EST. ")
private val TRANSITIONS = listOf(
    "CUT TO:", "DISSOLVE TO:", "SMASH CUT TO:", "MATCH CUT TO:",
    "FADE IN:", "FADE OUT.", "FADE TO BLACK.",
)

/** A note overlapping a paragraph, in that paragraph's local character coordinates. */
data class ParagraphNote(val start: Int, val end: Int, val color: Color, val note: NoteAnnotation)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: ScriptViewModel,
    listState: LazyListState,
    onOpen: () -> Unit,
    onSaveAs: () -> Unit,
    onExportPdf: () -> Unit,
    onOpenBeatBoard: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val document = state.document ?: return
    val paragraphs = document.model.paragraphs

    // The focused paragraph drives the bottom "details" bar. We keep the last focus so the bar
    // still targets that paragraph while the user taps its controls.
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var focusedField by remember { mutableStateOf<FocusedField?>(null) }
    var pendingFocusKey by remember { mutableStateOf<String?>(null) }
    var pendingCaret by remember { mutableStateOf<Int?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showOutline by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val focusedType = focusedKey?.let { key -> paragraphs.firstOrNull { it.key == key }?.type }

    val reveal by viewModel.revealRequest.collectAsStateWithLifecycle()
    val searchState by viewModel.search.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val notes = document.model.notes
    val characterNames = remember(document) {
        paragraphs.asSequence()
            .filter { it.type == ElementType.CHARACTER }
            .map { it.plainText.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.uppercase() }
            .toList()
    }
    val sceneHeadingTexts = remember(document) {
        paragraphs.asSequence()
            .filter { it.type == ElementType.SCENE_HEADING }
            .map { it.plainText.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.uppercase() }
            .toList()
    }
    val sceneList = remember(document) {
        paragraphs.mapIndexedNotNull { i, p ->
            if (p.type == ElementType.SCENE_HEADING) {
                i to p.plainText.trim().ifEmpty { "(untitled scene)" }
            } else {
                null
            }
        }
    }
    val sceneHeadingCandidates = remember(sceneHeadingTexts) { SCENE_OPENERS + sceneHeadingTexts }
    var editingNote by remember { mutableStateOf<NoteAnnotation?>(null) }
    var pendingNoteEditId by remember { mutableStateOf<String?>(null) }

    // Character ranges (per paragraph) covered by notes, painted as a light highlight.
    val highlightsByKey = remember(document) {
        val mapper = document.offsetMapper()
        val map = HashMap<String, MutableList<ParagraphNote>>()
        paragraphs.forEachIndexed { i, para ->
            val paraStart = mapper.paragraphStart(i)
            val paraLen = para.plainText.length
            val paraEnd = paraStart + paraLen
            for (note in notes) {
                val overlaps = (note.start < paraEnd && note.end > paraStart) ||
                    (note.start == note.end && note.start in paraStart..paraEnd)
                if (!overlaps) continue
                var ls = (maxOf(note.start, paraStart) - paraStart).coerceIn(0, paraLen)
                var le = (minOf(note.end, paraEnd) - paraStart).coerceIn(0, paraLen)
                if (ls == le) { if (le < paraLen) le += 1 else if (ls > 0) ls -= 1 }
                if (ls >= le) continue
                val color = FdxColor.toArgb(note.color)?.let { Color(it).copy(alpha = 0.30f) }
                    ?: Color(0x33FFEB3B)
                map.getOrPut(para.key) { mutableListOf() }.add(ParagraphNote(ls, le, color, note))
            }
        }
        map
    }

    // Per-paragraph search-match highlights (the active match is brighter than the rest).
    val searchHighlightsByKey = remember(searchState) {
        val map = HashMap<String, MutableList<RichText.HighlightSpan>>()
        searchState.matches.forEachIndexed { idx, m ->
            val color = if (idx == searchState.current) Color(0xFFFFC107) else Color(0x55FFEB3B)
            map.getOrPut(m.key) { mutableListOf() }.add(RichText.HighlightSpan(m.start, m.end, color))
        }
        map
    }

    // Scroll to a note's paragraph when one is chosen from the Notes list.
    LaunchedEffect(reveal) {
        val target = reveal ?: return@LaunchedEffect
        if (paragraphs.isNotEmpty()) {
            listState.animateScrollToItem(target.paragraphIndex.coerceIn(0, paragraphs.lastIndex))
        }
        viewModel.consumeReveal()
    }

    // Scroll to the active search match.
    LaunchedEffect(searchState.current, searchState.matches) {
        val m = searchState.matches.getOrNull(searchState.current) ?: return@LaunchedEffect
        if (paragraphs.isNotEmpty()) {
            listState.animateScrollToItem(m.index.coerceIn(0, paragraphs.lastIndex))
        }
    }

    // Open the note editor once a newly added note appears in the model.
    LaunchedEffect(pendingNoteEditId, notes) {
        val id = pendingNoteEditId ?: return@LaunchedEffect
        notes.firstOrNull { it.id == id }?.let {
            editingNote = it
            pendingNoteEditId = null
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    var showSaved by remember { mutableStateOf(false) }
    LaunchedEffect(state.savedAt) {
        if (state.savedAt != null) {
            showSaved = true
            delay(1500)
            showSaved = false
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(state.fileName ?: "Script", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        TextButton(onClick = { viewModel.openSearch() }) { Text("Find") }
                        TextButton(onClick = { viewModel.save() }, enabled = state.isDirty) {
                            Text(if (state.isDirty) "Save*" else "Saved")
                        }
                        Box {
                            TextButton(onClick = { menuOpen = true }) { Text("...") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Save As") }, onClick = { menuOpen = false; onSaveAs() })
                                DropdownMenuItem(text = { Text("Export PDF") }, onClick = { menuOpen = false; onExportPdf() })
                                DropdownMenuItem(text = { Text("Open") }, onClick = { menuOpen = false; onOpen() })
                                DropdownMenuItem(text = { Text("Close") }, onClick = { menuOpen = false; viewModel.close() })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Beat Board") }, onClick = { menuOpen = false; onOpenBeatBoard() })
                                DropdownMenuItem(text = { Text("Notes") }, onClick = { menuOpen = false; onOpenNotes() })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; onOpenSettings() })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Outline") }, onClick = { menuOpen = false; showOutline = true })
                                DropdownMenuItem(text = { Text("Statistics") }, onClick = { menuOpen = false; showStats = true })
                            }
                        }
                    },
                )
                if (searchState.active) {
                    SearchBar(
                        state = searchState,
                        onQuery = viewModel::setSearchQuery,
                        onNext = viewModel::nextMatch,
                        onPrev = viewModel::prevMatch,
                        onReplaceOne = viewModel::replaceCurrent,
                        onReplaceAll = viewModel::replaceAll,
                        onClose = viewModel::closeSearch,
                    )
                }
            }
        },
        bottomBar = {
            Column {
                val suggestionCandidates = when (focusedType) {
                    ElementType.CHARACTER -> characterNames
                    ElementType.SCENE_HEADING -> sceneHeadingCandidates
                    ElementType.TRANSITION -> TRANSITIONS
                    else -> emptyList()
                }
                SuggestionBar(
                    field = if (suggestionCandidates.isNotEmpty()) focusedField else null,
                    candidates = suggestionCandidates,
                )
                FormatBar(
                    focused = focusedField,
                    currentType = focusedType,
                    canUndo = state.canUndo,
                    canRedo = state.canRedo,
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    onTypeChange = { type -> focusedKey?.let { viewModel.setParagraphType(it, type) } },
                    onDelete = {
                        focusedKey?.let { viewModel.deleteParagraph(it) }
                        focusedKey = null
                        focusedField = null
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                items(paragraphs, key = { it.key }) { para ->
                    ParagraphRow(
                        paragraph = para,
                        notes = highlightsByKey[para.key].orEmpty(),
                        searchHighlights = searchHighlightsByKey[para.key].orEmpty(),
                        characterColor = if (settings.characterColorsEnabled &&
                            para.type == ElementType.CHARACTER && para.plainText.isNotBlank()
                        ) RichText.characterColor(para.plainText) else null,
                        fontSize = settings.editorFontSize,
                        requestFocus = para.key == pendingFocusKey,
                        caretTarget = if (para.key == pendingFocusKey) pendingCaret else null,
                        onFocusConsumed = {
                            if (pendingFocusKey == para.key) {
                                pendingFocusKey = null
                                pendingCaret = null
                            }
                        },
                        onRunsChanged = { runs -> viewModel.updateParagraphRuns(para.key, runs) },
                        onSplit = { before, after -> pendingFocusKey = viewModel.splitParagraph(para.key, before, after) },
                        onMergeUp = {
                            viewModel.mergeWithPrevious(para.key)?.let {
                                pendingCaret = it.caret
                                pendingFocusKey = it.key
                            }
                        },
                        onFocusGained = { field ->
                            focusedKey = field.key
                            focusedField = field
                            viewModel.setFocusedParagraph(field.key)
                        },
                        onSelectionChanged = { s, e -> viewModel.setFocusedSelection(para.key, s, e) },
                        onNoteTapped = { note -> editingNote = note },
                        onAddNote = { pendingNoteEditId = viewModel.addNoteForSelection() },
                        onRemoveNote = { note -> viewModel.deleteNote(note.id) },
                    )
                }
            }
            FastScrollbar(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(32.dp),
            )
            AnimatedVisibility(
                visible = showSaved,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = RoundedCornerShape(50),
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp,
                ) {
                    Text(
                        "Saved",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }

    editingNote?.let { note ->
        NoteEditDialog(
            note = note,
            onDismiss = { editingNote = null },
            onSave = { body, type, color ->
                viewModel.updateNote(note.id, body, type, color)
                editingNote = null
            },
            onDelete = {
                viewModel.deleteNote(note.id)
                editingNote = null
            },
        )
    }

    if (showOutline) {
        ModalBottomSheet(onDismissRequest = { showOutline = false }) {
            if (sceneList.isEmpty()) {
                Text("No scenes yet.", modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    items(sceneList) { (index, text) ->
                        Text(
                            text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { listState.animateScrollToItem(index) }
                                    showOutline = false
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }

    if (showStats) {
        val stats = remember(document) { ScriptStats.compute(paragraphs) }
        ModalBottomSheet(onDismissRequest = { showStats = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text("Statistics", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("Scenes: ${stats.sceneCount}")
                Text("Words: ${stats.wordCount}")
                Text("Estimated pages: ~${stats.estimatedPages}")
                if (stats.dialogueByCharacter.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Dialogue blocks by character", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    stats.dialogueByCharacter.forEach { (name, count) ->
                        Text("$name \u2014 $count")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ParagraphRow(
    paragraph: ScreenplayParagraph,
    notes: List<ParagraphNote>,
    searchHighlights: List<RichText.HighlightSpan>,
    characterColor: Color?,
    fontSize: Int,
    requestFocus: Boolean,
    caretTarget: Int?,
    onFocusConsumed: () -> Unit,
    onRunsChanged: (List<StyledRun>) -> Unit,
    onSplit: (List<StyledRun>, List<StyledRun>) -> Unit,
    onMergeUp: () -> Unit,
    onFocusGained: (FocusedField) -> Unit,
    onSelectionChanged: (Int, Int) -> Unit,
    onNoteTapped: (NoteAnnotation) -> Unit,
    onAddNote: () -> Unit,
    onRemoveNote: (NoteAnnotation) -> Unit,
) {
    val tfvState = remember(paragraph.key) { mutableStateOf(RichText.toTextFieldValue(paragraph.runs)) }
    val focusRequester = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            // When merging lines, place the caret at the join point on the merged text.
            caretTarget?.let { target ->
                val merged = RichText.toTextFieldValue(paragraph.runs)
                tfvState.value = merged.copy(selection = TextRange(target.coerceIn(0, merged.text.length)))
            }
            onFocusConsumed()
        }
    }

    // Reflect external model changes (undo/redo, merges) that didn't originate from this field.
    LaunchedEffect(paragraph.runs) {
        val incoming = RichText.toTextFieldValue(paragraph.runs)
        if (incoming.text != tfvState.value.text) {
            val caret = tfvState.value.selection.start.coerceIn(0, incoming.text.length)
            tfvState.value = incoming.copy(selection = TextRange(caret))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topGapFor(paragraph.type), bottom = 1.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        BasicTextField(
            value = tfvState.value,
            onValueChange = { newValue ->
                val old = tfvState.value
                val newline = newValue.text.indexOf('\n')
                if (newline >= 0) {
                    val anno = newValue.annotatedString
                    val beforeAnno = anno.subSequence(0, newline)
                    val afterAnno = anno.subSequence(newline + 1, anno.length)
                    tfvState.value = TextFieldValue(beforeAnno, selection = TextRange(beforeAnno.length))
                    onSplit(RichText.annotatedToRuns(beforeAnno), RichText.annotatedToRuns(afterAnno))
                } else {
                    tfvState.value = newValue
                    // A pure cursor/selection move (unchanged text & styling) must not mark dirty.
                    if (newValue.annotatedString != old.annotatedString) {
                        onRunsChanged(RichText.annotatedToRuns(newValue.annotatedString))
                    }
                    onSelectionChanged(newValue.selection.min, newValue.selection.max)
                    // Tapping into a highlighted range (no text change) reveals its note.
                    if (newValue.text.length == old.text.length && newValue.selection.collapsed) {
                        val caret = newValue.selection.start
                        val wasInside = notes.any { old.selection.start in it.start until it.end }
                        val hit = notes.firstOrNull { caret in it.start until it.end }
                        if (hit != null && !wasInside) onNoteTapped(hit.note)
                    }
                }
            },
            textStyle = textStyleFor(paragraph.type, colors.onSurface, colors.primary, fontSize)
                .let { if (characterColor != null) it.copy(color = characterColor) else it },
            visualTransformation = RichText.highlightTransformation(
                uppercase = paragraph.type.displaysUppercase,
                highlights = notes.map { RichText.HighlightSpan(it.start, it.end, it.color) } + searchHighlights,
            ),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier
                .fillMaxWidth(widthFractionFor(paragraph.type))
                .heightIn(min = 20.dp)
                .onPreviewKeyEvent { ev ->
                    val sel = tfvState.value.selection
                    if (ev.type == KeyEventType.KeyDown && ev.key == Key.Backspace &&
                        sel.collapsed && sel.start == 0
                    ) {
                        onMergeUp()
                        true
                    } else {
                        false
                    }
                }
                .appendTextContextMenuComponents {
                    val sel = tfvState.value.selection
                    val existing = notes.firstOrNull { pn ->
                        val lo = sel.min
                        val hi = sel.max
                        if (lo == hi) lo in pn.start..pn.end else lo < pn.end && pn.start < hi
                    }
                    item(
                        "fdx_toggle_note",
                        if (existing != null) "Remove note" else "Add note",
                        0,
                    ) {
                        if (existing != null) onRemoveNote(existing.note) else onAddNote()
                        close()
                    }
                }
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        onFocusGained(
                            FocusedField(
                                key = paragraph.key,
                                getValue = { tfvState.value },
                                setValue = { v ->
                                    tfvState.value = v
                                    onRunsChanged(RichText.annotatedToRuns(v.annotatedString))
                                },
                            ),
                        )
                        val sel = tfvState.value.selection
                        onSelectionChanged(sel.min, sel.max)
                    }
                },
        )
    }
}

/** A draggable scrollbar on the right edge: press or drag anywhere along it to jump/scroll. */
@Composable
private fun FastScrollbar(listState: LazyListState, modifier: Modifier = Modifier) {
    val total = listState.layoutInfo.totalItemsCount
    val visible = listState.layoutInfo.visibleItemsInfo.size
    if (total == 0 || visible >= total) return
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier.pointerInput(total) {
            fun jumpTo(y: Float) {
                val trackPx = size.height.toFloat()
                if (trackPx <= 0f) return
                val f = (y / trackPx).coerceIn(0f, 1f)
                val target = (f * (total - 1)).roundToInt().coerceIn(0, total - 1)
                scope.launch { listState.scrollToItem(target) }
            }
            awaitPointerEventScope {
                while (true) {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    jumpTo(down.position.y)
                    var pressed = true
                    while (pressed) {
                        val change = awaitPointerEvent().changes.first()
                        change.consume()
                        jumpTo(change.position.y)
                        pressed = change.pressed
                    }
                }
            }
        },
    ) {
        val density = LocalDensity.current
        val trackPx = with(density) { maxHeight.toPx() }
        val minThumbPx = with(density) { 48.dp.toPx() }
        val thumbFraction = (visible.toFloat() / total).coerceIn(0.08f, 1f)
        val thumbPx = (trackPx * thumbFraction).coerceIn(minThumbPx.coerceAtMost(trackPx), trackPx)
        val travel = (trackPx - thumbPx).coerceAtLeast(1f)
        val progress = (listState.firstVisibleItemIndex.toFloat() / (total - visible)).coerceIn(0f, 1f)
        val thumbTop = travel * progress
        // A faint full-height track makes the scrollbar easy to see and aim for.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 3.dp)
                .width(12.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(colors.onSurface.copy(alpha = 0.12f)),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbTop.roundToInt()) }
                .padding(end = 3.dp)
                .width(12.dp)
                .height(with(density) { thumbPx.toDp() })
                .clip(RoundedCornerShape(6.dp))
                .background(colors.primary.copy(alpha = 0.75f)),
        )
    }
}

/** Thin bottom bar: undo/redo, the focused paragraph's element type, and B / I / U. */
@Composable
private fun FormatBar(
    focused: FocusedField?,
    currentType: ElementType?,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTypeChange: (ElementType) -> Unit,
    onDelete: () -> Unit,
) {
    val value = focused?.getValue?.invoke()
    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ElementTypeSelector(
                    currentType = currentType,
                    enabled = currentType != null,
                    onTypeChange = onTypeChange,
                    onDelete = onDelete,
                )
                Spacer(Modifier.weight(1f))
                BarGlyphButton("\u21BA", enabled = canUndo, onClick = onUndo)
                BarGlyphButton("\u21BB", enabled = canRedo, onClick = onRedo)
                FormatToggle("B", StyledRun.BOLD, focused, value, weight = FontWeight.Bold)
                FormatToggle("I", StyledRun.ITALIC, focused, value, style = FontStyle.Italic)
                FormatToggle("U", StyledRun.UNDERLINE, focused, value, decoration = TextDecoration.Underline)
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

/** Compact glyph button (undo / redo) sized to match the format toggles. */
@Composable
private fun BarGlyphButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            color = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f),
        )
    }
}

@Composable
private fun ElementTypeSelector(
    currentType: ElementType?,
    enabled: Boolean,
    onTypeChange: (ElementType) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) {
            Text(currentType?.displayName ?: "No selection")
            Text(" \u25BE")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (type in ElementType.screenplayTypes) {
                DropdownMenuItem(
                    text = { Text(type.displayName) },
                    onClick = { expanded = false; onTypeChange(type) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Delete paragraph") }, onClick = { expanded = false; onDelete() })
        }
    }
}

/** A horizontal strip of SmartType suggestions shown above the bar for the focused line. */
@Composable
private fun SuggestionBar(field: FocusedField?, candidates: List<String>) {
    if (field == null || candidates.isEmpty()) return
    val current = field.getValue().text.trim()
    val matches = candidates.filter {
        it.startsWith(current, ignoreCase = true) && !it.equals(current, ignoreCase = true)
    }.take(6)
    if (matches.isEmpty()) return
    Surface(tonalElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (name in matches) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.clickable {
                        field.setValue(TextFieldValue(name, selection = TextRange(name.length)))
                    },
                ) {
                    Text(
                        name,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatToggle(
    label: String,
    token: String,
    focused: FocusedField?,
    value: TextFieldValue?,
    weight: FontWeight? = null,
    style: FontStyle? = null,
    decoration: TextDecoration? = null,
) {
    val enabled = focused != null
    val active = value != null && RichText.isTokenActive(value, token)
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) colors.secondaryContainer else Color.Transparent)
            .clickable(enabled = enabled) {
                focused?.let { it.setValue(RichText.toggleToken(it.getValue(), token)) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontWeight = weight,
            fontStyle = style,
            textDecoration = decoration,
            color = when {
                !enabled -> LocalContentColor.current.copy(alpha = 0.38f)
                active -> colors.onSecondaryContainer
                else -> LocalContentColor.current
            },
        )
    }
}

@Composable
private fun SearchBar(
    state: SearchUiState,
    onQuery: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onReplaceOne: (String) -> Unit,
    onReplaceAll: (String) -> Unit,
    onClose: () -> Unit,
) {
    var replaceText by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    placeholder = { Text("Find") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                Text(
                    if (state.matches.isEmpty()) "0/0" else "${state.current + 1}/${state.matches.size}",
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
                TextButton(onClick = onPrev, enabled = state.matches.isNotEmpty()) { Text("\u2039") }
                TextButton(onClick = onNext, enabled = state.matches.isNotEmpty()) { Text("\u203A") }
                TextButton(onClick = onClose) { Text("\u2715") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = replaceText,
                    onValueChange = { replaceText = it },
                    placeholder = { Text("Replace with") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onReplaceOne(replaceText) }, enabled = state.current >= 0) { Text("Replace") }
                TextButton(onClick = { onReplaceAll(replaceText) }, enabled = state.matches.isNotEmpty()) { Text("All") }
            }
        }
    }
}

private fun textStyleFor(type: ElementType, onSurface: Color, primary: Color, fontSize: Int): TextStyle {
    val base = TextStyle(
        fontFamily = FontFamily.Monospace,
        color = onSurface,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.25f).sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )
    return when (type) {
        ElementType.SCENE_HEADING -> base.copy(fontWeight = FontWeight.Bold, color = primary)
        ElementType.TRANSITION -> base.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        ElementType.CHARACTER -> base.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        ElementType.PARENTHETICAL -> base.copy(fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
        ElementType.DIALOGUE -> base.copy(textAlign = TextAlign.Center)
        else -> base
    }
}

private fun topGapFor(type: ElementType): Dp = when (type) {
    ElementType.SCENE_HEADING -> 24.dp
    ElementType.ACTION -> 14.dp
    ElementType.CHARACTER -> 14.dp
    ElementType.TRANSITION -> 14.dp
    ElementType.DIALOGUE -> 0.dp
    ElementType.PARENTHETICAL -> 0.dp
    else -> 2.dp
}

/** Fraction of the available width a paragraph occupies, narrowing dialogue like a screenplay. */
private fun widthFractionFor(type: ElementType): Float = when (type) {
    ElementType.DIALOGUE -> 0.62f
    ElementType.PARENTHETICAL -> 0.5f
    else -> 1f
}
