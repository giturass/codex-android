@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.hebo.codex.domain.typeLabel
import com.hebo.codex.ui.components.NoticeHostState
import com.hebo.codex.ui.components.NoticeScaffold
import com.hebo.codex.ui.components.SettingsSectionCard

@Composable
fun ServerEditorScreen(
    viewModel: ServerEditorViewModel,
    noticeState: NoticeHostState,
    onBack: () -> Unit,
    onManageProxies: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showToken by rememberSaveable { mutableStateOf(false) }
    var proxyMenuExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.savedEvents.collect { onBack() }
    }
    LaunchedEffect(viewModel) {
        viewModel.testSucceededEvents.collect {
            noticeState.showMessage("服务器连接成功")
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            noticeState.showError(error)
            viewModel.clearError()
        }
    }

    NoticeScaffold(
        noticeState = noticeState,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "编辑服务器" else "添加服务器") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isBusy) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            )
        },
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SettingsSectionCard("连接信息", Icons.Rounded.Dns) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::updateName,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("名称") },
                        placeholder = { Text("Office Server") },
                        singleLine = true,
                        enabled = state.isReady && !state.isBusy,
                        shape = MaterialTheme.shapes.medium,
                    )
                    OutlinedTextField(
                        value = state.endpoint,
                        onValueChange = viewModel::updateEndpoint,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("WebSocket URL") },
                        placeholder = { Text("ws://10.150.99.201:8390") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true,
                        enabled = state.isReady && !state.isBusy,
                        shape = MaterialTheme.shapes.medium,
                    )
                }
                SettingsSectionCard("访问认证", Icons.Rounded.Key) {
                    OutlinedTextField(
                        value = stringResource(R.string.server_auth_capability_token),
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("认证方式") },
                        readOnly = true,
                        enabled = state.isReady && !state.isBusy,
                        shape = MaterialTheme.shapes.medium,
                    )
                    OutlinedTextField(
                        value = state.token,
                        onValueChange = viewModel::updateToken,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.server_auth_capability_token)) },
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = state.isReady && !state.isBusy,
                        shape = MaterialTheme.shapes.medium,
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
                }
                SettingsSectionCard("网络代理", Icons.Rounded.Lan) {
                    ExposedDropdownMenuBox(
                        expanded = proxyMenuExpanded,
                        onExpandedChange = { if (!state.isBusy) proxyMenuExpanded = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val selectedProxy = state.proxies.singleOrNull { it.id == state.proxyId }
                        OutlinedTextField(
                            value = selectedProxy?.name ?: "直连",
                            onValueChange = {},
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            label = { Text("代理") },
                            readOnly = true,
                            enabled = state.isReady && !state.isBusy,
                            shape = MaterialTheme.shapes.medium,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = proxyMenuExpanded)
                            },
                        )
                        ExposedDropdownMenu(
                            expanded = proxyMenuExpanded,
                            onDismissRequest = { proxyMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("直连") },
                                onClick = {
                                    viewModel.updateProxy(null)
                                    proxyMenuExpanded = false
                                },
                            )
                            state.proxies.forEach { proxy ->
                                DropdownMenuItem(
                                    text = { Text("${proxy.name} · ${proxy.configuration.typeLabel()}") },
                                    onClick = {
                                        viewModel.updateProxy(proxy.id)
                                        proxyMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = onManageProxies,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isBusy,
                    ) {
                        Text("管理代理")
                    }
                }
                if (state.endpoint.startsWith("ws://")) {
                    Text(
                        "ws:// 不提供端到端传输加密，仅应连接可信网络；HTTP/SOCKS 代理能够读取通信内容",
                        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                FilledTonalButton(
                    onClick = viewModel::testConnection,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().heightIn(min = 48.dp),
                    enabled = state.canTest,
                ) {
                    if (state.isTesting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("正在测试连接")
                        }
                    } else {
                        Text("测试连接")
                    }
                }
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().heightIn(min = 48.dp),
                    enabled = state.canSave,
                ) {
                    if (state.isSaving) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("正在保存")
                        }
                    } else {
                        Text(if (state.isEditing) "更新" else "保存")
                    }
                }
            }
        }
    }
}
