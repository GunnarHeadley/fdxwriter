package com.gunnarheadley.fdxwriter.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnarheadley.fdxwriter.data.repo.ThemeMode
import com.gunnarheadley.fdxwriter.ui.ScriptViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ScriptViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    // Local state keeps text/slider input responsive; changes are persisted to DataStore as they occur.
    var author by remember { mutableStateOf(settings.noteAuthor) }
    var interval by remember { mutableStateOf(settings.autoSaveIntervalSeconds.toFloat()) }
    var fontSize by remember { mutableStateOf(settings.editorFontSize.toFloat()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = author,
                onValueChange = {
                    author = it
                    viewModel.setNoteAuthor(it)
                },
                label = { Text("Note author") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Color character names",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = settings.characterColorsEnabled,
                    onCheckedChange = { viewModel.setCharacterColorsEnabled(it) },
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Show approximate page breaks",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = settings.showPageBreaks,
                    onCheckedChange = { viewModel.setShowPageBreaks(it) },
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Auto-save",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = settings.autoSaveEnabled,
                    onCheckedChange = { viewModel.setAutoSaveEnabled(it) },
                )
            }

            if (settings.autoSaveEnabled) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Save every ${interval.roundToInt()} seconds",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = interval,
                    onValueChange = { interval = it },
                    onValueChangeFinished = { viewModel.setAutoSaveIntervalSeconds(interval.roundToInt()) },
                    valueRange = 10f..600f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))
            Text("Editor text size: ${fontSize.roundToInt()}", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = fontSize,
                onValueChange = { fontSize = it },
                onValueChangeFinished = { viewModel.setEditorFontSize(fontSize.roundToInt()) },
                valueRange = 12f..28f,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
            Text("Theme", style = MaterialTheme.typography.bodyLarge)
            ThemeMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                    )
                    Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }
    }
}
