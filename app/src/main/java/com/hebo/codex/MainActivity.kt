package com.hebo.codex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hebo.codex.ui.CodexApp
import com.hebo.codex.ui.theme.CodexTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as CodexApplication).container.repository
        setContent {
            CodexTheme {
                CodexApp(repository)
            }
        }
    }
}

