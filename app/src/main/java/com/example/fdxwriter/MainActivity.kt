package com.example.fdxwriter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fdxwriter.ui.AppScreen
import com.example.fdxwriter.ui.ScriptViewModel
import com.example.fdxwriter.ui.theme.FdxWriterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FdxWriterTheme {
                val viewModel: ScriptViewModel = viewModel()
                AppScreen(viewModel)
            }
        }
    }
}
