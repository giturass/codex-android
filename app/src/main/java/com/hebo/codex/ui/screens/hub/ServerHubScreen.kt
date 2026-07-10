@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.hub

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hebo.codex.domain.FeatureAction
import com.hebo.codex.domain.FeatureEntry
import com.hebo.codex.domain.FeatureKind
import com.hebo.codex.domain.ModelOption

@Composable
fun ServerHubScreen(
    viewModel: ServerHubViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    var dialog by remember { mutableStateOf<HubDialog?>(null) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.actionResult?.url) {
        state.actionResult?.url?.let(uriHandler::openUri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("服务器能力") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    SectionAddAction(state.section) { dialog = it }
                    IconButton(onClick = { viewModel.refresh(forceRefresh = true) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SectionSelector(state.section, viewModel::selectSection)
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (state.section) {
                HubSection.MODELS -> ModelsContent(state.models, viewModel::loadModelCapabilities)
                HubSection.ACCOUNT -> AccountContent(
                    state = state,
                    onApiKeyLogin = { dialog = HubDialog.ApiKeyLogin },
                    onChatGptLogin = { viewModel.loginWithChatGpt(false) },
                    onDeviceLogin = { viewModel.loginWithChatGpt(true) },
                    onCancelLogin = viewModel::cancelLogin,
                    onLogout = viewModel::logout,
                    onOpenUrl = uriHandler::openUri,
                    onResetLimit = { dialog = HubDialog.RateLimitReset },
                    onNudge = { dialog = HubDialog.CreditsNudge },
                )

                HubSection.CONFIG -> ConfigurationContent(
                    config = state.configuration?.configJson,
                    requirements = state.configuration?.requirementsJson,
                    layers = state.configuration?.layersJson,
                    onBatch = { dialog = HubDialog.ConfigBatch },
                    onFeedback = { dialog = HubDialog.Feedback },
                )

                else -> CatalogContent(
                    entries = state.catalog.entries,
                    warnings = state.catalog.warnings,
                    onRaw = { dialog = HubDialog.Raw(it.title, it.rawJson) },
                    onAction = { action -> viewModel.execute(action) },
                    onEntryDialog = { dialog = it },
                    onOpenUrl = uriHandler::openUri,
                )
            }
        }
    }

    state.actionResult?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            title = { Text(result.message) },
            text = { JsonBlock(result.rawJson) },
            confirmButton = { TextButton(onClick = viewModel::clearMessage) { Text("完成") } },
        )
    }

    dialog?.let { value ->
        HubInputDialog(
            value = value,
            onDismiss = { dialog = null },
            onSubmit = { first, second, enabled ->
                val action = value.toAction(first, second, enabled)
                when (value) {
                    HubDialog.ApiKeyLogin -> viewModel.loginWithApiKey(first)
                    HubDialog.RateLimitReset -> viewModel.consumeRateLimitReset(first.ifBlank { null })
                    HubDialog.CreditsNudge -> viewModel.sendCreditsNudge(first)
                    is HubDialog.Raw -> Unit
                    else -> action?.let(viewModel::execute)
                }
                dialog = null
            },
        )
    }
}

@Composable
private fun SectionSelector(selected: HubSection, onSelect: (HubSection) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HubSection.entries.forEach { section ->
            FilterChip(
                selected = section == selected,
                onClick = { onSelect(section) },
                label = { Text(section.label) },
            )
        }
    }
}

@Composable
private fun SectionAddAction(section: HubSection, onDialog: (HubDialog) -> Unit) {
    val dialog = when (section) {
        HubSection.SKILLS -> HubDialog.SkillRoots
        HubSection.PLUGINS -> HubDialog.AddMarketplace
        HubSection.SHARED_PLUGINS -> HubDialog.SharePlugin
        HubSection.MCP -> HubDialog.ReloadMcp
        HubSection.CONFIG -> HubDialog.ConfigValue
        else -> null
    }
    if (dialog != null) {
        IconButton(onClick = { onDialog(dialog) }) {
            Icon(
                if (section == HubSection.CONFIG) Icons.Rounded.Settings else Icons.Rounded.Add,
                contentDescription = "新增或配置",
            )
        }
    }
}

@Composable
private fun ModelsContent(models: List<ModelOption>, onCapabilities: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedButton(onClick = onCapabilities, modifier = Modifier.fillMaxWidth()) {
                Text("查看提供方能力")
            }
        }
        items(models, key = ModelOption::id) { model ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.displayName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        if (model.isDefault) Text("默认", color = MaterialTheme.colorScheme.primary)
                        if (model.hidden) Text("隐藏", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(model.model, style = MaterialTheme.typography.labelMedium)
                    Text(model.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("推理：${model.reasoningEfforts.joinToString { it.value }}")
                    if (model.serviceTiers.isNotEmpty()) {
                        Text("服务层级：${model.serviceTiers.joinToString { it.label }}")
                    }
                    Text("输入：${model.inputModalities.joinToString()}${if (model.supportsPersonality) " · 支持人格" else ""}")
                }
            }
        }
    }
}

@Composable
private fun CatalogContent(
    entries: List<FeatureEntry>,
    warnings: List<String>,
    onRaw: (FeatureEntry) -> Unit,
    onAction: (FeatureAction) -> Unit,
    onEntryDialog: (HubDialog) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        warnings.forEachIndexed { index, warning ->
            item("warning:$index") {
                Text(warning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (entries.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                    Text("暂无内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(entries, key = { "${it.kind}:${it.id}" }) { entry ->
            FeatureCard(entry, onRaw, onAction, onEntryDialog, onOpenUrl)
        }
    }
}

@Composable
private fun FeatureCard(
    entry: FeatureEntry,
    onRaw: (FeatureEntry) -> Unit,
    onAction: (FeatureAction) -> Unit,
    onEntryDialog: (HubDialog) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium)
                    if (entry.parentId != null) Text(entry.parentId, style = MaterialTheme.typography.labelSmall)
                }
                if ((entry.kind == FeatureKind.SKILL || entry.kind == FeatureKind.APP) && entry.enabled != null) {
                    Switch(
                        checked = entry.enabled,
                        onCheckedChange = {
                            val action = if (entry.kind == FeatureKind.SKILL) {
                                FeatureAction.SetSkillEnabled(entry.title, entry.path, it)
                            } else {
                                FeatureAction.WriteConfigValue(
                                    "apps.${entry.id.toConfigKeySegment()}.enabled",
                                    it.toString(),
                                )
                            }
                            onAction(action)
                        },
                    )
                }
            }
            if (entry.description.isNotBlank()) {
                Text(entry.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            entry.path?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { onRaw(entry) }) { Text("详情") }
                when (entry.kind) {
                    FeatureKind.APP -> entry.metadata["installUrl"]?.takeIf(String::isNotBlank)?.let { url ->
                        TextButton(onClick = { onOpenUrl(url) }) {
                            Icon(Icons.Rounded.OpenInBrowser, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("安装或授权")
                        }
                    }

                    FeatureKind.MARKETPLACE -> {
                        TextButton(onClick = { onAction(FeatureAction.UpgradeMarketplace(entry.id)) }) {
                            Text("升级")
                        }
                        TextButton(onClick = { onAction(FeatureAction.RemoveMarketplace(entry.id)) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = null)
                            Text("移除")
                        }
                    }

                    FeatureKind.PLUGIN -> {
                        val marketplacePath = entry.metadata["marketplacePath"]?.ifBlank { null }
                        val remoteMarketplace = if (marketplacePath == null) entry.metadata["marketplaceName"] else null
                        TextButton(
                            onClick = {
                                onAction(FeatureAction.ReadPlugin(entry.title, marketplacePath, remoteMarketplace))
                            },
                        ) { Text("读取") }
                        if (entry.installed == true) {
                            TextButton(onClick = { onAction(FeatureAction.UninstallPlugin(entry.id)) }) {
                                Text("卸载")
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    onAction(FeatureAction.InstallPlugin(entry.title, marketplacePath, remoteMarketplace))
                                },
                            ) { Text("安装") }
                        }
                        if (entry.metadata["remotePluginId"].orEmpty().isNotBlank()) {
                            TextButton(onClick = { onEntryDialog(HubDialog.PluginSkill(entry)) }) {
                                Text("读取 Skill")
                            }
                        }
                    }

                    FeatureKind.MCP_SERVER -> if (entry.metadata["authStatus"] == "notLoggedIn") {
                        TextButton(onClick = { onAction(FeatureAction.LoginMcpServer(entry.id, null)) }) {
                            Text("登录")
                        }
                    }

                    FeatureKind.MCP_RESOURCE -> TextButton(
                        onClick = {
                            onAction(
                                FeatureAction.ReadMcpResource(
                                    server = entry.metadata.getValue("server"),
                                    uri = entry.metadata.getValue("uri"),
                                    threadId = null,
                                ),
                            )
                        },
                    ) { Text("读取资源") }

                    FeatureKind.MCP_TOOL -> TextButton(onClick = { onEntryDialog(HubDialog.McpTool(entry)) }) {
                        Text("调用")
                    }

                    FeatureKind.SHARED_PLUGIN -> {
                        entry.metadata["shareUrl"]?.takeIf(String::isNotBlank)?.let { url ->
                            TextButton(onClick = { onOpenUrl(url) }) { Text("打开分享") }
                        }
                        TextButton(onClick = { onEntryDialog(HubDialog.SharedTargets(entry)) }) {
                            Text("共享设置")
                        }
                        TextButton(onClick = { onAction(FeatureAction.CheckoutSharedPlugin(entry.id)) }) {
                            Text("检出")
                        }
                        TextButton(onClick = { onAction(FeatureAction.DeleteSharedPlugin(entry.id)) }) {
                            Text("删除")
                        }
                    }

                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun AccountContent(
    state: ServerHubUiState,
    onApiKeyLogin: () -> Unit,
    onChatGptLogin: () -> Unit,
    onDeviceLogin: () -> Unit,
    onCancelLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onResetLimit: () -> Unit,
    onNudge: () -> Unit,
) {
    val account = state.account
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (account?.signedIn == true) "已登录" else "未登录", style = MaterialTheme.typography.titleLarge)
                    account?.type?.let { Text("类型：$it") }
                    account?.email?.let { Text(it) }
                    account?.planType?.let { Text("计划：$it") }
                    if (account?.signedIn == true) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(onClick = onLogout) { Text("退出登录") }
                            OutlinedButton(onClick = onResetLimit) { Text("使用重置额度") }
                            OutlinedButton(onClick = onNudge) { Text("请求补充额度") }
                        }
                    } else {
                        Button(onClick = onChatGptLogin) { Text("ChatGPT 登录") }
                        OutlinedButton(onClick = onDeviceLogin) { Text("设备码登录") }
                        OutlinedButton(onClick = onApiKeyLogin) { Text("API Key 登录") }
                    }
                }
            }
        }
        state.loginChallenge?.let { challenge ->
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("完成登录", style = MaterialTheme.typography.titleMedium)
                        challenge.userCode?.let { SelectionContainer { Text("设备码：$it") } }
                        challenge.url?.let { url -> Button(onClick = { onOpenUrl(url) }) { Text("打开登录页面") } }
                        if (challenge.loginId != null) TextButton(onClick = onCancelLogin) { Text("取消登录") }
                    }
                }
            }
        }
        account?.workspaceMessages.orEmpty().forEach { message ->
            item(message.id) {
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(message.type, style = MaterialTheme.typography.labelMedium)
                        Text(message.body)
                    }
                }
            }
        }
        account?.rateLimitsJson?.let { item { LabeledJson("额度", it) } }
        account?.usageJson?.let { item { LabeledJson("用量", it) } }
    }
}

@Composable
private fun ConfigurationContent(
    config: String?,
    requirements: String?,
    layers: String?,
    onBatch: () -> Unit,
    onFeedback: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onBatch) { Text("批量写入") }
                OutlinedButton(onClick = onFeedback) { Text("提交反馈") }
            }
        }
        config?.let { item { LabeledJson("生效配置", it) } }
        requirements?.let { item { LabeledJson("管理要求", it) } }
        layers?.let { item { LabeledJson("配置层", it) } }
    }
}

@Composable
private fun LabeledJson(label: String, json: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        JsonBlock(json)
    }
}

@Composable
private fun JsonBlock(json: String) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface),
    ) {
        SelectionContainer {
            Text(
                json,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private sealed interface HubDialog {
    data object SkillRoots : HubDialog
    data object AddMarketplace : HubDialog
    data object SharePlugin : HubDialog
    data object ReloadMcp : HubDialog
    data object ConfigValue : HubDialog
    data object ConfigBatch : HubDialog
    data object Feedback : HubDialog
    data object ApiKeyLogin : HubDialog
    data object RateLimitReset : HubDialog
    data object CreditsNudge : HubDialog
    data class McpTool(val entry: FeatureEntry) : HubDialog
    data class PluginSkill(val entry: FeatureEntry) : HubDialog
    data class SharedTargets(val entry: FeatureEntry) : HubDialog
    data class Raw(val title: String, val json: String) : HubDialog
}

@Composable
private fun HubInputDialog(
    value: HubDialog,
    onDismiss: () -> Unit,
    onSubmit: (first: String, second: String, enabled: Boolean) -> Unit,
) {
    if (value is HubDialog.Raw) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(value.title) },
            text = { JsonBlock(value.json) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        )
        return
    }
    var first by remember(value) { mutableStateOf(value.defaultFirst) }
    var second by remember(value) { mutableStateOf(value.defaultSecond) }
    var enabled by remember(value) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(value.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (value.firstLabel != null) {
                    OutlinedTextField(
                        value = first,
                        onValueChange = { first = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(value.firstLabel.orEmpty()) },
                        minLines = if (
                            value is HubDialog.McpTool || value == HubDialog.ConfigValue || value == HubDialog.ConfigBatch
                        ) 3 else 1,
                        visualTransformation = if (value == HubDialog.ApiKeyLogin) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                    )
                }
                if (value.secondLabel != null) {
                    OutlinedTextField(
                        value = second,
                        onValueChange = { second = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(value.secondLabel.orEmpty()) },
                    )
                }
                if (value == HubDialog.Feedback || value == HubDialog.ConfigBatch || value == HubDialog.ConfigValue) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (value) {
                                HubDialog.Feedback -> "附带日志"
                                HubDialog.ConfigBatch -> "写入后热重载用户配置"
                                else -> "使用 upsert 合并策略"
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
                if (value == HubDialog.ReloadMcp) Text("重新读取服务端 MCP 配置并刷新已加载会话")
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(first, second, enabled) },
                enabled = (value.firstLabel == null || first.isNotBlank()) &&
                    (value !is HubDialog.McpTool || second.isNotBlank()),
            ) {
                Text("确认")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private val HubSection.label: String
    get() = when (this) {
        HubSection.MODELS -> "模型"
        HubSection.PERMISSIONS -> "权限"
        HubSection.SKILLS -> "Skills"
        HubSection.HOOKS -> "Hooks"
        HubSection.APPS -> "Apps"
        HubSection.PLUGINS -> "插件"
        HubSection.MCP -> "MCP"
        HubSection.SHARED_PLUGINS -> "共享"
        HubSection.ACCOUNT -> "账号"
        HubSection.CONFIG -> "设置"
    }

private val HubDialog.title: String
    get() = when (this) {
        HubDialog.SkillRoots -> "Skill 额外目录"
        HubDialog.AddMarketplace -> "添加 Marketplace"
        HubDialog.SharePlugin -> "分享本地插件"
        HubDialog.ReloadMcp -> "重新加载 MCP"
        HubDialog.ConfigValue -> "写入配置"
        HubDialog.ConfigBatch -> "批量写入配置"
        HubDialog.Feedback -> "提交反馈"
        HubDialog.ApiKeyLogin -> "API Key 登录"
        HubDialog.RateLimitReset -> "使用额度重置"
        HubDialog.CreditsNudge -> "请求补充额度"
        is HubDialog.McpTool -> "调用 ${entry.title}"
        is HubDialog.PluginSkill -> "读取 ${entry.title} 的 Skill"
        is HubDialog.SharedTargets -> "${entry.title} 共享设置"
        is HubDialog.Raw -> title
    }

private val HubDialog.firstLabel: String?
    get() = when (this) {
        HubDialog.SkillRoots -> "绝对路径，多个路径用换行分隔"
        HubDialog.AddMarketplace -> "Git URL 或 owner/repo"
        HubDialog.SharePlugin -> "插件绝对路径"
        HubDialog.ConfigValue -> "配置键路径"
        HubDialog.ConfigBatch -> "配置编辑 JSON 数组"
        HubDialog.Feedback -> "分类"
        HubDialog.ApiKeyLogin -> "API Key"
        HubDialog.RateLimitReset -> "Credit ID，可留空"
        HubDialog.CreditsNudge -> "额度类型"
        is HubDialog.McpTool -> "参数 JSON"
        is HubDialog.PluginSkill -> "Skill 名称"
        is HubDialog.SharedTargets -> "可发现性：UNLISTED 或 PRIVATE"
        HubDialog.ReloadMcp, is HubDialog.Raw -> null
    }

private val HubDialog.secondLabel: String?
    get() = when (this) {
        HubDialog.AddMarketplace -> "第一行分支或 Tag，后续行 sparse path，均可留空"
        HubDialog.SharePlugin -> "分享选项 JSON 对象"
        HubDialog.ConfigValue -> "JSON 值"
        HubDialog.Feedback -> "原因"
        is HubDialog.McpTool -> "会话 ID"
        is HubDialog.SharedTargets -> "共享目标 JSON 数组"
        else -> null
    }

private val HubDialog.defaultFirst: String
    get() = when (this) {
        HubDialog.ConfigValue -> "model"
        HubDialog.ConfigBatch -> "[]"
        HubDialog.Feedback -> "bug"
        HubDialog.CreditsNudge -> "credits"
        is HubDialog.McpTool -> "{}"
        is HubDialog.SharedTargets -> entry.metadata["discoverability"]
            ?.takeIf { it == "UNLISTED" || it == "PRIVATE" }
            ?: "UNLISTED"
        else -> ""
    }

private val HubDialog.defaultSecond: String
    get() = when (this) {
        HubDialog.ConfigValue -> "null"
        HubDialog.SharePlugin -> "{}"
        is HubDialog.SharedTargets -> entry.metadata["shareTargets"].orEmpty().ifBlank { "[]" }
        else -> ""
    }

private fun HubDialog.toAction(first: String, second: String, enabled: Boolean): FeatureAction? = when (this) {
    HubDialog.SkillRoots -> FeatureAction.SetSkillExtraRoots(first.lineSequence().map(String::trim).filter(String::isNotEmpty).toList())
    HubDialog.AddMarketplace -> second.lineSequence().map(String::trim).filter(String::isNotEmpty).toList().let {
        FeatureAction.AddMarketplace(first.trim(), it.firstOrNull(), it.drop(1))
    }
    HubDialog.SharePlugin -> FeatureAction.SaveSharedPlugin(first.trim(), second)
    HubDialog.ReloadMcp -> FeatureAction.ReloadMcpServers
    HubDialog.ConfigValue -> FeatureAction.WriteConfigValue(first.trim(), second, if (enabled) "upsert" else "replace")
    HubDialog.ConfigBatch -> FeatureAction.WriteConfigBatch(first, enabled)
    HubDialog.Feedback -> FeatureAction.UploadFeedback(first.trim(), second.trim().ifBlank { null }, null, enabled)
    is HubDialog.McpTool -> FeatureAction.CallMcpTool(
        server = entry.metadata.getValue("server"),
        tool = entry.metadata.getValue("tool"),
        argumentsJson = first,
        threadId = second.trim(),
    )
    is HubDialog.PluginSkill -> FeatureAction.ReadPluginSkill(
        marketplaceName = entry.metadata.getValue("marketplaceName"),
        pluginId = entry.metadata.getValue("remotePluginId"),
        skillName = first.trim(),
    )
    is HubDialog.SharedTargets -> FeatureAction.UpdateSharedPluginTargets(
        remotePluginId = entry.id,
        discoverability = first.trim(),
        shareTargetsJson = second,
    )
    HubDialog.ApiKeyLogin, HubDialog.RateLimitReset, HubDialog.CreditsNudge, is HubDialog.Raw -> null
}

private fun String.toConfigKeySegment(): String = if (all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
    this
} else {
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
