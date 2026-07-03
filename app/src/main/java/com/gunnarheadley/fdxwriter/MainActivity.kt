package com.gunnarheadley.fdxwriter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gunnarheadley.fdxwriter.data.repo.ThemeMode
import com.gunnarheadley.fdxwriter.ui.AppScreen
import com.gunnarheadley.fdxwriter.ui.ScriptViewModel
import com.gunnarheadley.fdxwriter.ui.theme.FdxWriterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ScriptViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            FdxWriterTheme(darkTheme = darkTheme) {
                AppScreen(viewModel)
            }
        }
    }
}
