package com.hebo.codex.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// A modal drawer presents the shared notice above its scrim instead of inside the covered page.
internal val LocalPageNoticesVisible = compositionLocalOf { true }

@Composable
internal fun NoticeScaffold(
    noticeState: NoticeHostState,
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    topBar: @Composable () -> Unit,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = contentWindowInsets,
        topBar = topBar,
        floatingActionButton = floatingActionButton,
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            content(padding)
            // Scaffold supplies the measured top bar height, including its system insets.
            // The notice overlays the content and never contributes to the page's padding.
            FloatingNoticeHost(
                state = noticeState,
                visible = LocalPageNoticesVisible.current,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(padding)
                    .widthIn(max = NoticeMaxWidth),
            )
        }
    }
}
