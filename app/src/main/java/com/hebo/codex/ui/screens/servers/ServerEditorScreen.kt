@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hebo.codex.R
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.ui.components.UserFacingErrorDialog

@Composable
fun ServerEditorScreen(
    viewModel: ServerEditorViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showToken by rememberSaveable { mutableStateOf(false) }
    var technicalError by remember { mutableStateOf<UserFacingError?>(null) }
    val detailsLabel = stringResource(R.string.common_details)

    LaunchedEffect(viewModel) {
        viewModel.savedEvents.collect { onBack() }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            val result = snackbarHostState.showSnackbar(
                message = error.summary,
                actionLabel = error.technicalDetails?.let { detailsLabel },
            )
            if (result == SnackbarResult.ActionPerformed) technicalError = error
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "编辑服务器" else "添加服务器") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isSaving) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::updateName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("名称") },
                    placeholder = { Text("Office Server") },
                    singleLine = true,
                    enabled = state.isReady && !state.isSaving,
                )
                OutlinedTextField(
                    value = state.endpoint,
                    onValueChange = viewModel::updateEndpoint,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("WebSocket URL") },
                    placeholder = { Text("ws://10.150.99.201:8390") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    enabled = state.isReady && !state.isSaving,
                )
                OutlinedTextField(
                    value = stringResource(R.string.server_auth_capability_token),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("认证方式") },
                    readOnly = true,
                    enabled = state.isReady && !state.isSaving,
                )
                OutlinedTextField(
                    value = state.token,
                    onValueChange = viewModel::updateToken,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.server_auth_capability_token)) },
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    enabled = state.isReady && !state.isSaving,
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = stringResource(
                                    if (showToken) R.string.server_auth_hide_capability_token
                                    else R.string.server_auth_show_capability_token,
                                ),
                            )
                        }
                    },
                )
                Text(
                    stringResource(R.string.server_auth_capability_token_storage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.endpoint.startsWith("ws://")) {
                    Text(
                        "ws:// 不提供传输加密，仅应通过可信局域网或 SSH 端口转发使用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canSave,
                ) {
                    if (state.isSaving) {
                        Row {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("正在测试连接")
                        }
                    } else {
                        Text(if (state.isEditing) "测试并更新" else "测试并保存")
                    }
                }
            }
        }
    }
    technicalError?.let { error ->
        UserFacingErrorDialog(error = error, onDismiss = { technicalError = null })
    }
}
