@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.proxies

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hebo.codex.domain.HttpProxyAuthentication
import com.hebo.codex.domain.ProxyProfile
import com.hebo.codex.domain.ProxyTlsPolicy
import com.hebo.codex.domain.Socks5Authentication
import com.hebo.codex.domain.Socks5DnsResolution
import com.hebo.codex.domain.SshAuthentication
import com.hebo.codex.domain.addressSummary
import com.hebo.codex.domain.typeLabel
import com.hebo.codex.ui.components.NoticeHostState
import com.hebo.codex.ui.components.NoticeScaffold
import com.hebo.codex.ui.components.SettingsSectionCard

@Composable
fun ProxyListScreen(
    viewModel: ProxyListViewModel,
    noticeState: NoticeHostState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<ProxyProfile?>(null) }

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
                title = { Text("代理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("添加代理") },
            )
        },
    ) { padding ->
        if (state.proxies.isEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 32.dp, top = 32.dp, end = 32.dp, bottom = 104.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                item {
                    Column(
                        modifier = Modifier.widthIn(max = 320.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Icon(
                                Icons.Rounded.Lan,
                                contentDescription = null,
                                modifier = Modifier.padding(24.dp).size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("尚未添加代理", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "添加代理后，可在服务器设置中选择使用",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.proxies, key = ProxyProfile::id) { proxy ->
                    Card(
                        onClick = { onEdit(proxy.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Icon(
                                    Icons.Rounded.Lan,
                                    contentDescription = null,
                                    modifier = Modifier.padding(12.dp).size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(proxy.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${proxy.configuration.typeLabel()} · ${proxy.configuration.addressSummary()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    when {
                                        proxy.referencedServerCount > 0 -> "${proxy.referencedServerCount} 个服务器正在使用"
                                        proxy.lastTest?.succeeded == true -> "最近测试成功"
                                        proxy.lastTest != null -> "最近测试失败"
                                        else -> "尚未测试"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onEdit(proxy.id) }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "编辑")
                            }
                            IconButton(
                                onClick = { deleting = proxy },
                                enabled = proxy.referencedServerCount == 0 && state.removingProxyId == null,
                            ) {
                                if (state.removingProxyId == proxy.id) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.Delete, contentDescription = "删除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleting?.let { proxy ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除代理") },
            text = { Text("确定删除“${proxy.name}”吗？保存的代理凭据也会一并删除") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(proxy.id)
                    deleting = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
fun ProxyEditorScreen(
    viewModel: ProxyEditorViewModel,
    noticeState: NoticeHostState,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCredential by rememberSaveable { mutableStateOf(false) }
    val privateKeyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                viewModel.updatePrivateKeyUri(uri.toString())
            } catch (error: Exception) {
                viewModel.reportPrivateKeySelectionError(error)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.savedEvents.collect { onBack() }
    }
    LaunchedEffect(viewModel) {
        viewModel.testSucceededEvents.collect { noticeState.showMessage("代理连接成功") }
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
                title = { Text(if (state.isEditing) "编辑代理" else "添加代理") },
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
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
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
                SettingsSectionCard("代理信息", Icons.Rounded.Lan) {
                    TextField(state.name, viewModel::updateName, "名称", state.isBusy)
                    EnumDropdown(
                        label = "类型",
                        selected = state.type,
                        options = listOf(
                            ProxyType.HTTP_CONNECT to "HTTP CONNECT",
                            ProxyType.SOCKS5 to "SOCKS5",
                            ProxyType.SSH to "SSH",
                        ),
                        enabled = !state.isBusy,
                        onSelect = viewModel::updateType,
                    )
                }

                SettingsSectionCard("连接与认证", Icons.Rounded.Key) {
                    when (state.type) {
                        ProxyType.HTTP_CONNECT -> HttpFields(state, viewModel, showCredential) {
                            showCredential = !showCredential
                        }
                        ProxyType.SOCKS5 -> SocksFields(state, viewModel, showCredential) {
                            showCredential = !showCredential
                        }
                        ProxyType.SSH -> SshFields(
                            state = state,
                            viewModel = viewModel,
                            showCredential = showCredential,
                            onToggleCredential = { showCredential = !showCredential },
                            onSelectPrivateKey = { privateKeyLauncher.launch(arrayOf("*/*")) },
                        )
                    }
                }

                SettingsSectionCard("连接测试", Icons.Rounded.Speed) {
                    NumberField(state.timeoutSeconds, viewModel::updateTimeoutSeconds, "连接超时（秒）", state.isBusy)

                    if (state.isEditing) {
                        if (state.servers.isEmpty()) {
                            Text(
                                "添加服务器后可进行端到端代理测试",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            EnumDropdown(
                                label = "测试目标服务器",
                                selected = requireNotNull(state.testServerId),
                                options = state.servers.map { it.id to it.name },
                                enabled = !state.isBusy,
                                onSelect = viewModel::updateTestServer,
                            )
                        }
                        FilledTonalButton(
                            onClick = viewModel::test,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            enabled = state.canTest,
                        ) {
                            BusyButtonContent(state.isTesting, "正在测试", "测试代理")
                        }
                    } else {
                        Text(
                            "保存后可选择一个服务器做端到端测试",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().heightIn(min = 48.dp),
                    enabled = state.canSave,
                ) {
                    BusyButtonContent(state.isSaving, "正在保存", if (state.isEditing) "更新" else "保存")
                }
            }
        }
    }

    state.pendingHostKey?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissHostKey,
            title = { Text(if (pending.previous == null) "确认 SSH 主机密钥" else "SSH 主机密钥已变化") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pending.previous?.let { Text("原指纹\n${it.sha256Fingerprint}") }
                    Text("新指纹\n${pending.observed.sha256Fingerprint}")
                    Text("请通过可信渠道核对后再确认")
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmHostKey) { Text("确认并重试") } },
            dismissButton = { TextButton(onClick = viewModel::dismissHostKey) { Text("取消") } },
        )
    }
}

@Composable
private fun HttpFields(
    state: ProxyEditorUiState,
    viewModel: ProxyEditorViewModel,
    showCredential: Boolean,
    onToggleCredential: () -> Unit,
) {
    TextField(state.httpUrl, viewModel::updateHttpUrl, "代理 URL", state.isBusy, KeyboardType.Uri, "http://proxy.example:8080")
    EnumDropdown(
        "代理认证",
        state.httpAuthentication,
        listOf(
            HttpProxyAuthentication.NONE to "无",
            HttpProxyAuthentication.BASIC to "Basic",
            HttpProxyAuthentication.BEARER to "Bearer",
        ),
        !state.isBusy,
        viewModel::updateHttpAuthentication,
    )
    if (state.httpAuthentication == HttpProxyAuthentication.BASIC) {
        TextField(state.httpUsername, viewModel::updateHttpUsername, "用户名", state.isBusy)
    }
    if (state.httpAuthentication != HttpProxyAuthentication.NONE) {
        CredentialField(
            value = state.credential,
            onValueChange = viewModel::updateCredential,
            label = if (state.httpAuthentication == HttpProxyAuthentication.BEARER) "代理令牌" else "代理密码",
            configured = state.credentialConfigured,
            show = showCredential,
            enabled = !state.isBusy,
            onToggle = onToggleCredential,
        )
    }
    OutlinedTextField(
        value = state.httpHeaders,
        onValueChange = viewModel::updateHttpHeaders,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("自定义 CONNECT 请求头") },
        placeholder = { Text("X-Proxy-Header: value") },
        minLines = 2,
        enabled = !state.isBusy,
        shape = MaterialTheme.shapes.medium,
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("允许无效的代理 TLS 证书")
            Text(
                "仅影响 https:// 代理，不影响目标服务器证书校验",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.httpTlsPolicy == ProxyTlsPolicy.ALLOW_INVALID_CERTIFICATE) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Switch(
            checked = state.httpTlsPolicy == ProxyTlsPolicy.ALLOW_INVALID_CERTIFICATE,
            onCheckedChange = {
                viewModel.updateHttpTlsPolicy(
                    if (it) ProxyTlsPolicy.ALLOW_INVALID_CERTIFICATE else ProxyTlsPolicy.STRICT,
                )
            },
            enabled = !state.isBusy,
        )
    }
}

@Composable
private fun SocksFields(
    state: ProxyEditorUiState,
    viewModel: ProxyEditorViewModel,
    showCredential: Boolean,
    onToggleCredential: () -> Unit,
) {
    HostAndPortFields(state, viewModel, "SOCKS5")
    EnumDropdown(
        "代理认证",
        state.socksAuthentication,
        listOf(
            Socks5Authentication.NONE to "无",
            Socks5Authentication.USERNAME_PASSWORD to "用户名与密码",
        ),
        !state.isBusy,
        viewModel::updateSocksAuthentication,
    )
    if (state.socksAuthentication == Socks5Authentication.USERNAME_PASSWORD) {
        TextField(state.socksUsername, viewModel::updateSocksUsername, "用户名", state.isBusy)
        CredentialField(
            state.credential,
            viewModel::updateCredential,
            "密码",
            state.credentialConfigured,
            showCredential,
            !state.isBusy,
            onToggleCredential,
        )
    }
    EnumDropdown(
        "DNS 解析",
        state.socksDnsResolution,
        listOf(
            Socks5DnsResolution.PROXY to "由代理解析",
            Socks5DnsResolution.LOCAL to "由本机解析",
        ),
        !state.isBusy,
        viewModel::updateSocksDnsResolution,
    )
}

@Composable
private fun SshFields(
    state: ProxyEditorUiState,
    viewModel: ProxyEditorViewModel,
    showCredential: Boolean,
    onToggleCredential: () -> Unit,
    onSelectPrivateKey: () -> Unit,
) {
    HostAndPortFields(state, viewModel, "SSH")
    TextField(state.sshUsername, viewModel::updateSshUsername, "SSH 用户名", state.isBusy)
    EnumDropdown(
        "SSH 认证",
        state.sshAuthentication,
        listOf(
            SshAuthentication.PRIVATE_KEY to "私钥",
            SshAuthentication.PASSWORD to "密码",
        ),
        !state.isBusy,
        viewModel::updateSshAuthentication,
    )
    if (state.sshAuthentication == SshAuthentication.PRIVATE_KEY) {
        OutlinedButton(onClick = onSelectPrivateKey, modifier = Modifier.fillMaxWidth(), enabled = !state.isBusy) {
            Text(if (state.privateKeyUri == null) "选择私钥" else "重新选择私钥")
        }
        state.privateKeyUri?.let {
            Text(
                it.substringAfterLast('/'),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CredentialField(
            state.credential,
            viewModel::updateCredential,
            "私钥口令（可选）",
            state.credentialConfigured,
            showCredential,
            !state.isBusy,
            onToggleCredential,
        )
        if (state.credentialConfigured) {
            TextButton(onClick = viewModel::clearOptionalCredential, enabled = !state.isBusy) {
                Text("移除已保存的私钥口令")
            }
        }
    } else {
        CredentialField(
            state.credential,
            viewModel::updateCredential,
            "SSH 密码",
            state.credentialConfigured,
            showCredential,
            !state.isBusy,
            onToggleCredential,
        )
    }
    NumberField(state.keepAliveSeconds, viewModel::updateKeepAliveSeconds, "保活间隔（秒）", state.isBusy)
    NumberField(state.keepAliveMaxFailures, viewModel::updateKeepAliveMaxFailures, "保活失败次数", state.isBusy)
    state.sshHostKey?.let { hostKey ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("已确认的 SSH 主机密钥", style = MaterialTheme.typography.titleSmall)
                Text(hostKey.algorithm, style = MaterialTheme.typography.labelMedium)
                Text(hostKey.sha256Fingerprint, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::removeHostKey, enabled = !state.isBusy) {
                    Text("移除确认记录")
                }
            }
        }
    }
}

@Composable
private fun HostAndPortFields(state: ProxyEditorUiState, viewModel: ProxyEditorViewModel, prefix: String) {
    TextField(state.host, viewModel::updateHost, "$prefix 主机", state.isBusy)
    NumberField(state.port, viewModel::updatePort, "$prefix 端口", state.isBusy)
}

@Composable
private fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    busy: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        enabled = !busy,
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String, busy: Boolean) {
    TextField(value, onValueChange, label, busy, KeyboardType.Number)
}

@Composable
private fun CredentialField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    configured: Boolean,
    show: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = if (configured) ({ Text("留空则保留现有凭据") }) else null,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(
                    if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (show) "隐藏凭据" else "显示凭据",
                )
            }
        },
    )
}

@Composable
private fun <T> EnumDropdown(
    label: String,
    selected: T,
    options: List<Pair<T, String>>,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = options.singleOrNull { it.first == selected }?.second.orEmpty(),
            onValueChange = {},
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            label = { Text(label) },
            readOnly = true,
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun BusyButtonContent(busy: Boolean, busyText: String, idleText: String) {
    if (busy) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(busyText)
        }
    } else {
        Text(idleText)
    }
}
