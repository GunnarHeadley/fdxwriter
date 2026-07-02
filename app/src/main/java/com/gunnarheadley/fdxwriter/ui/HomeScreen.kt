package com.gunnarheadley.fdxwriter.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val QUIPS = listOf(
    "Fade in\u2026 eventually.",
    "Act two is where scripts go to die. Good luck.",
    "Every great screenplay starts with a blank page and mild panic.",
    "Save early, save often \u2014 your muse is unreliable.",
    "No notes. (There are always notes.)",
    "Show, don't tell. Also, remember to save.",
    "Your protagonist wants something. So does your deadline.",
    "INT. PHONE \u2014 CONTINUOUS",
    "The first draft is just you telling yourself the story.",
    "Cut to: you actually finishing this scene.",
    "It's not procrastination, it's \u2018breaking the story.\u2019",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: ScriptViewModel, onOpen: () -> Unit, onNew: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("FDX Writer") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            if (state.isLoading) {
                LoadingState()
            } else {
                EmptyState(
                    recentFiles = recentFiles,
                    onNew = onNew,
                    onOpen = onOpen,
                    onOpenRecent = { viewModel.open(Uri.parse(it)) },
                    onRemoveRecent = viewModel::removeRecent,
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Working\u2026")
    }
}

@Composable
private fun EmptyState(
    recentFiles: List<com.gunnarheadley.fdxwriter.data.repo.RecentFile>,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onOpenRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
) {
    val quip = remember { QUIPS.random() }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            quip,
            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNew) { Text("New script") }
            OutlinedButton(onClick = onOpen) { Text("Open .fdx file") }
        }

        if (recentFiles.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Recent", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(recentFiles, key = { it.uri }) { recent ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenRecent(recent.uri) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            recent.name,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = { onRemoveRecent(recent.uri) }) { Text("Remove") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
