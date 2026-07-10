@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hebo.codex.ui.components.TaskPromptBar
import com.hebo.codex.ui.components.ThreadRow

@Composable
fun ProjectScreen(
    viewModel: ProjectViewModel,
    onBack: () -> Unit,
    onOpenConversation: (threadId: String) -> Unit,
    onOpenServerHub: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ProjectEvent.OpenConversation -> onOpenConversation(event.threadId)
            }
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.projectName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenServerHub) {
                        Icon(Icons.Rounded.Settings, contentDescription = "服务器能力")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            TaskPromptBar(
                label = "在此项目中开始新任务",
                isLoading = state.isCreatingConversation,
                onClick = viewModel::startConversation,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("path") {
                    Text(
                        state.cwd,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                item("title") { Text("最近会话", style = MaterialTheme.typography.titleMedium) }
                if (state.threads.isNotEmpty()) {
                    items(state.threads, key = { it.id }) { thread ->
                        ThreadRow(thread = thread, onClick = { onOpenConversation(thread.id) })
                    }
                } else if (state.isLoading) {
                    item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                } else {
                    item("empty") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            Text("该项目还没有会话", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (state.nextCursor != null) {
                    item("load-more") {
                        OutlinedButton(
                            onClick = viewModel::loadMore,
                            enabled = !state.isLoadingMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.isLoadingMore) CircularProgressIndicator(strokeWidth = 2.dp)
                            else Text("加载更多")
                        }
                    }
                }
                item("end") { Spacer(Modifier.height(1.dp)) }
            }
        }
    }
}
