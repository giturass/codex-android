@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.hub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hebo.codex.domain.FeatureCatalog
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.ui.components.ConnectionIndicator
import com.hebo.codex.ui.components.ExpandableErrorDetails
import com.hebo.codex.ui.components.NoticeHostState
import com.hebo.codex.ui.components.NoticeScaffold
import com.hebo.codex.ui.components.ConnectionStatusButton
import com.hebo.codex.ui.components.label

private val TwoPaneThreshold = 760.dp

@Composable
fun ServerHubScreen(
    viewModel: ServerHubViewModel,
    noticeState: NoticeHostState,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var dialog by remember { mutableStateOf<HubDialog?>(null) }
    var details by remember { mutableStateOf<FeatureDetails?>(null) }
    var confirmation by remember { mutableStateOf<ConfirmationRequest?>(null) }

    LaunchedEffect(state.errorMessage) {
        val error = state.errorMessage ?: return@LaunchedEffect
        viewModel.clearError()
        noticeState.showError(error)
    }
    LaunchedEffect(state.actionResult) {
        val result = state.actionResult ?: return@LaunchedEffect
        result.url?.let { url -> runCatching { uriHandler.openUri(url) } }
        viewModel.clearActionResult()
        noticeState.showMessage(result.message)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= TwoPaneThreshold
        val selectedSection = state.selectedSection
        val projectScoped = state.cwd != null
        val rootTitle = if (projectScoped) "项目设置" else "服务器设置"
        val showDetailBack = !twoPane && selectedSection != null

        BackHandler(enabled = showDetailBack) { viewModel.selectSection(null) }

        NoticeScaffold(
            noticeState = noticeState,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (showDetailBack) selectedSection.title else rootTitle)
                            if (showDetailBack) {
                                Text(
                                    text = state.scopeSummary(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = if (showDetailBack) {
                                { viewModel.selectSection(null) }
                            } else {
                                onBack
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        ConnectionStatusButton(state.connectionStatus, onReconnect = viewModel::reconnect)
                        selectedSection?.let { section ->
                            SectionPrimaryAction(
                                section = section,
                                projectScoped = projectScoped,
                                onDialog = { dialog = it },
                            )
                            IconButton(onClick = { viewModel.refresh(section) }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "刷新${section.title}")
                            }
                        }
                        HubOverflowMenu(
                            selectedSection = selectedSection,
                            projectScoped = projectScoped,
                            onDialog = { dialog = it },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                )
            },
        ) { padding ->
            if (twoPane) {
                Row(Modifier.fillMaxSize().padding(padding)) {
                    SettingsIndex(
                        state = state,
                        onSelect = viewModel::selectSection,
                        onReconnect = viewModel::reconnect,
                        modifier = Modifier.fillMaxHeight().width(340.dp),
                    )
                    VerticalDivider()
                    if (selectedSection == null) {
                        DetailPlaceholder(
                            projectScoped = projectScoped,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    } else {
                        HubDetailPane(
                            section = selectedSection,
                            state = state,
                            viewModel = viewModel,
                            onDialog = { dialog = it },
                            onDetails = { details = it },
                            onConfirmation = { confirmation = it },
                            onOpenUrl = uriHandler::openUri,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            } else if (selectedSection == null) {
                SettingsIndex(
                    state = state,
                    onSelect = viewModel::selectSection,
                    onReconnect = viewModel::reconnect,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            } else {
                HubDetailPane(
                    section = selectedSection,
                    state = state,
                    viewModel = viewModel,
                    onDialog = { dialog = it },
                    onDetails = { details = it },
                    onConfirmation = { confirmation = it },
                    onOpenUrl = uriHandler::openUri,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
        }
    }

    state.detailResult?.let { result ->
        RawResultDialog(result = result, onDismiss = viewModel::clearDetailResult)
    }

    details?.let { value ->
        FeatureDetailsDialog(details = value, onDismiss = { details = null })
    }

    confirmation?.let { request ->
        ConfirmationDialog(
            request = request,
            onDismiss = { confirmation = null },
            onConfirm = {
                confirmation = null
                request.onConfirm()
            },
        )
    }

    dialog?.let { value ->
        HubInputDialog(
            value = value,
            onDismiss = { dialog = null },
            onSubmit = { first, second, enabled ->
                when (value) {
                    HubDialog.ApiKeyLogin -> viewModel.loginWithApiKey(first)
                    HubDialog.RateLimitReset -> viewModel.consumeRateLimitReset(first.ifBlank { null })
                    HubDialog.CreditsNudge -> viewModel.sendCreditsNudge(first)
                    else -> value.toAction(first, second, enabled)?.let { action ->
                        viewModel.execute(
                            action = action,
                            actionKey = value.actionKey,
                            refreshSection = value.refreshSection,
                            presentation = value.resultPresentation,
                        )
                    }
                }
                dialog = null
            },
        )
    }
}

@Composable
private fun SettingsIndex(
    state: ServerHubUiState,
    onSelect: (HubSection) -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = hubSectionGroups(projectScoped = state.cwd != null)
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item("scope-header") {
            ScopeHeader(state = state, onReconnect = onReconnect)
        }
        groups.forEach { group ->
            item("group:${group.title}") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = group.title,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column {
                            group.sections.forEachIndexed { index, section ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    )
                                }
                                val selected = state.selectedSection == section
                                ListItem(
                                    selected = selected,
                                    onClick = { onSelect(section) },
                                    supportingContent = {
                                        Text(
                                            sectionSummary(section, state.sections[section]),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    leadingContent = {
                                        HubSectionIcon(section.icon, selected = selected)
                                    },
                                    trailingContent = {
                                        Icon(
                                            Icons.Rounded.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                    colors = androidx.compose.material3.ListItemDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    ),
                                ) {
                                    Text(section.title, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
        item("bottom-space") { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun ScopeHeader(state: ServerHubUiState, onReconnect: () -> Unit) {
    val reconnecting = ServerHubViewModel.RECONNECT_ACTION_KEY in state.pendingActions
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HubSectionIcon(if (state.cwd != null) Icons.Rounded.Folder else Icons.Rounded.Dns)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.scopeTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.scopeDescription(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConnectionIndicator(state.connectionStatus)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${state.server?.name ?: "服务器"} · ${state.connectionStatus.label()}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onReconnect, enabled = !reconnecting) {
                    if (reconnecting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("重新连接")
                    }
                }
            }
        }
    }
}

@Composable
internal fun HubSectionIcon(icon: ImageVector, selected: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun HubDetailPane(
    section: HubSection,
    state: ServerHubUiState,
    viewModel: ServerHubViewModel,
    onDialog: (HubDialog) -> Unit,
    onDetails: (FeatureDetails) -> Unit,
    onConfirmation: (ConfirmationRequest) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sectionState = state.sections[section] ?: HubSectionUiState(isLoading = true)
    val data = sectionState.data
    when {
        data == null && sectionState.errorMessage != null -> {
            SectionErrorState(
                error = sectionState.errorMessage,
                onRetry = { viewModel.refresh(section) },
                modifier = modifier,
            )
        }

        data == null -> {
            Box(modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        else -> {
            Column(modifier) {
                if (sectionState.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                sectionState.errorMessage?.let { error ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ExpandableErrorDetails(error, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.refresh(section) }) { Text("重试") }
                        }
                    }
                }
                HubDataContent(
                    section = section,
                    data = data,
                    state = state,
                    viewModel = viewModel,
                    onDialog = onDialog,
                    onDetails = onDetails,
                    onConfirmation = onConfirmation,
                    onOpenUrl = onOpenUrl,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SectionErrorState(
    error: UserFacingError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp).widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("无法加载内容", style = MaterialTheme.typography.titleMedium)
            ExpandableErrorDetails(error)
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun DetailPlaceholder(projectScoped: Boolean, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (projectScoped) "选择一项项目设置" else "选择一项服务器设置",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "详细内容将在此处显示",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionPrimaryAction(
    section: HubSection,
    projectScoped: Boolean,
    onDialog: (HubDialog) -> Unit,
) {
    val action = when (section) {
        HubSection.SKILLS -> if (projectScoped) null else "目录" to HubDialog.SkillRoots
        HubSection.PLUGINS -> if (projectScoped) null else "来源" to HubDialog.AddMarketplace
        HubSection.MCP -> "重载" to HubDialog.ReloadMcp
        HubSection.CONFIG -> if (projectScoped) null else "写入" to HubDialog.ConfigValue
        else -> null
    }
    action?.let { (label, dialog) ->
        TextButton(onClick = { onDialog(dialog) }) { Text(label) }
    }
}

@Composable
private fun HubOverflowMenu(
    selectedSection: HubSection?,
    projectScoped: Boolean,
    onDialog: (HubDialog) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "更多设置操作")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (selectedSection == HubSection.PLUGINS && !projectScoped) {
                DropdownMenuItem(
                    text = { Text("分享本地插件") },
                    onClick = {
                        expanded = false
                        onDialog(HubDialog.SharePlugin)
                    },
                )
            }
            if (selectedSection == HubSection.CONFIG && !projectScoped) {
                DropdownMenuItem(
                    text = { Text("批量写入配置") },
                    onClick = {
                        expanded = false
                        onDialog(HubDialog.ConfigBatch)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("提交反馈") },
                onClick = {
                    expanded = false
                    onDialog(HubDialog.Feedback)
                },
            )
        }
    }
}

private fun sectionSummary(section: HubSection, state: HubSectionUiState?): String {
    if (state?.errorMessage != null && state.data == null) return "加载失败，点按重试"
    if (state?.isLoading == true && state.data == null) return "正在加载"
    return when (val data = state?.data) {
        is HubData.Models -> "${data.value.size} 个模型"
        is HubData.Catalog -> data.value.summary(section)
        is HubData.Account -> when {
            data.value.signedIn && data.value.email != null -> data.value.email
            data.value.signedIn -> "已登录"
            else -> "未登录"
        }

        is HubData.Configuration -> "已读取生效配置和配置层"
        null -> section.description
    }
}

private fun FeatureCatalog.summary(section: HubSection): String {
    if (entries.isEmpty()) return when (section) {
        HubSection.MCP -> "尚未配置 MCP 服务器"
        HubSection.SKILLS -> "当前作用域没有 Skills"
        HubSection.PLUGINS -> "尚未发现插件"
        HubSection.APPS -> "当前没有可用 Apps"
        HubSection.HOOKS -> "当前作用域没有 Hooks"
        HubSection.PERMISSIONS -> "没有可用的权限策略"
        HubSection.SHARED_PLUGINS -> "尚未共享插件"
        else -> "暂无内容"
    }
    val warning = if (warnings.isEmpty()) "" else " · ${warnings.size} 个提醒"
    return "${entries.size} 项$warning"
}

private fun ServerHubUiState.scopeTitle(): String = cwd
    ?.trimEnd('/')
    ?.substringAfterLast('/')
    ?.ifBlank { cwd }
    ?: server?.name
    ?: "服务器"

private fun ServerHubUiState.scopeDescription(): String = cwd ?: server?.webSocketUrl.orEmpty()

private fun ServerHubUiState.scopeSummary(): String = if (cwd != null) {
    "${scopeTitle()} · ${server?.name ?: "服务器"}"
} else {
    "${server?.name ?: "服务器"} · ${connectionStatus.label()}"
}
