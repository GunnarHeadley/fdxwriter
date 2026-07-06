package com.gunnarheadley.fdxwriter.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnarheadley.fdxwriter.ui.beatboard.BeatBoardScreen
import com.gunnarheadley.fdxwriter.ui.editor.EditorScreen
import com.gunnarheadley.fdxwriter.ui.notes.NotesScreen
import com.gunnarheadley.fdxwriter.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

private enum class OpenScreen { Editor, BeatBoard, Notes, Settings }

/** Top-level UI: owns the file pickers and shows the editor when a script is open, else home. */
@Composable
fun AppScreen(viewModel: ScriptViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(OpenScreen.Editor) }
    // Hoisted so the editor's scroll position survives navigating to Beat Board / Notes / Settings.
    val editorListState = rememberLazyListState()

    // Resume each script where the user last left off, and keep the saved position current as they scroll.
    val restoreScroll by viewModel.restoreScroll.collectAsStateWithLifecycle()
    LaunchedEffect(restoreScroll) {
        val pos = restoreScroll ?: return@LaunchedEffect
        val maxIndex = (state.paragraphCount - 1).coerceAtLeast(0)
        editorListState.scrollToItem(pos.index.coerceIn(0, maxIndex), pos.offset.coerceAtLeast(0))
        viewModel.consumeRestoreScroll()
    }
    LaunchedEffect(state.uri) {
        if (state.uri == null) return@LaunchedEffect
        snapshotFlow { editorListState.firstVisibleItemIndex to editorListState.firstVisibleItemScrollOffset }
            .drop(1) // ignore the list's pre-restore position
            .debounce(500)
            .collect { (index, offset) -> viewModel.saveScrollPosition(index, offset) }
    }

    val openLauncher = rememberLauncherForActivityResult(OpenFdxDocument()) { uri ->
        uri?.let { viewModel.open(it) }
    }
    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(FDX_CREATE_MIME),
    ) { uri -> uri?.let { viewModel.saveAs(it) } }

    val newLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(FDX_CREATE_MIME),
    ) { uri -> uri?.let { viewModel.createNew(it) } }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri -> uri?.let { viewModel.exportPdf(it) } }

    val onOpen: () -> Unit = { openLauncher.launch(FDX_OPEN_MIME_TYPES) }
    val onNew: () -> Unit = { newLauncher.launch("Untitled.fdx") }
    val onSaveAs: () -> Unit = { saveAsLauncher.launch(state.fileName ?: "script.fdx") }
    val onExportPdf: () -> Unit = {
        exportPdfLauncher.launch((state.fileName?.substringBeforeLast(".") ?: "script") + ".pdf")
    }

    LaunchedEffect(state.isOpen) { if (!state.isOpen) screen = OpenScreen.Editor }

    if (state.isOpen) {
        when (screen) {
            OpenScreen.Editor -> EditorScreen(
                viewModel = viewModel,
                listState = editorListState,
                onOpen = onOpen,
                onSaveAs = onSaveAs,
                onExportPdf = onExportPdf,
                onOpenBeatBoard = { screen = OpenScreen.BeatBoard },
                onOpenNotes = { screen = OpenScreen.Notes },
                onOpenSettings = { screen = OpenScreen.Settings },
            )
            OpenScreen.BeatBoard -> BeatBoardScreen(
                viewModel = viewModel,
                onBack = { screen = OpenScreen.Editor },
            )
            OpenScreen.Notes -> NotesScreen(
                viewModel = viewModel,
                onBack = { screen = OpenScreen.Editor },
            )
            OpenScreen.Settings -> SettingsScreen(
                viewModel = viewModel,
                onBack = { screen = OpenScreen.Editor },
            )
        }
    } else {
        HomeScreen(viewModel = viewModel, onOpen = onOpen, onNew = onNew)
    }
}
