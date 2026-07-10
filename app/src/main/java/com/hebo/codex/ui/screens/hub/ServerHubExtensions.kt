@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.FeatureAction
import com.hebo.codex.domain.FeatureCatalog
import com.hebo.codex.domain.FeatureEntry
import com.hebo.codex.domain.FeatureKind

private enum class PluginTab(val label: String) {
    BROWSE("浏览"),
    INSTALLED("已安装"),
    SOURCES("来源"),
    SHARED("共享"),
}

@Composable
fun PluginsContent(
    catalog: FeatureCatalog,
    projectScoped: Boolean,
    sharedState: HubSectionUiState?,
    pendingActions: Set<String>,
    onLoadShared: () -> Unit,
    onRefreshShared: () -> Unit,
    onExecute: (FeatureAction, String, HubSection?, ActionResultPresentation) -> Unit,
    onDialog: (HubDialog) -> Unit,
    onDetails: (FeatureDetails) -> Unit,
    onConfirmation: (ConfirmationRequest) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(PluginTab.BROWSE) }
    var query by rememberSaveable { mutableStateOf("") }
    val tabs = remember(projectScoped) {
        if (projectScoped) {
            listOf(PluginTab.BROWSE, PluginTab.INSTALLED)
        } else {
            PluginTab.entries
        }
    }
    LaunchedEffect(selectedTab) {
        if (selectedTab == PluginTab.SHARED) onLoadShared()
    }

    Column(modifier) {
        PrimaryTabRow(selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)) {
            tabs.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.label) },
                )
            }
        }
        when (selectedTab) {
            PluginTab.BROWSE,
            PluginTab.INSTALLED,
            -> {
                val plugins = catalog.entries
                    .filter { it.kind == FeatureKind.PLUGIN }
                    .filter { selectedTab != PluginTab.INSTALLED || it.installed == true }
                    .filter { query.isBlank() || it.searchText().contains(query, ignoreCase = true) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    item("plugin-search") {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            CatalogSearchField(query, { query = it }, "搜索 Plugins")
                        }
                    }
                    warnings(catalog.warnings)
                    if (plugins.isEmpty()) {
                        item("plugin-empty") {
                            EmptyState(
                                when {
                                    query.isNotBlank() -> "没有匹配的 Plugins"
                                    selectedTab == PluginTab.INSTALLED -> "尚未安装 Plugins"
                                    else -> "尚未发现 Plugins"
                                },
                            )
                        }
                    }
                    items(plugins, key = FeatureEntry::id) { entry ->
                        PluginRow(
                            entry = entry,
                            pendingActions = pendingActions,
                            onExecute = onExecute,
                            onDialog = onDialog,
                            onDetails = onDetails,
                            onConfirmation = onConfirmation,
                        )
                        HorizontalDivider(Modifier.padding(start = 56.dp))
                    }
                }
            }

            PluginTab.SOURCES -> {
                val sources = catalog.entries.filter { it.kind == FeatureKind.MARKETPLACE }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    warnings(catalog.warnings)
                    if (sources.isEmpty()) item("source-empty") { EmptyState("尚未添加插件来源") }
                    items(sources, key = FeatureEntry::id) { entry ->
                        MarketplaceRow(
                            entry = entry,
                            pendingActions = pendingActions,
                            onExecute = onExecute,
                            onDetails = onDetails,
                            onConfirmation = onConfirmation,
                        )
                        HorizontalDivider(Modifier.padding(start = 56.dp))
                    }
                }
            }

            PluginTab.SHARED -> when {
                sharedState?.data is HubData.Catalog -> SharedPluginsContent(
                    catalog = sharedState.data.value,
                    pendingActions = pendingActions,
                    onExecute = onExecute,
                    onDialog = onDialog,
                    onDetails = onDetails,
                    onConfirmation = onConfirmation,
                    onOpenUrl = onOpenUrl,
                    modifier = Modifier.fillMaxSize(),
                )

                sharedState?.errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("无法加载共享插件")
                        Text(
                            sharedState.errorMessage,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                        TextButton(onClick = onRefreshShared) { Text("重试") }
                    }
                }

                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun PluginRow(
    entry: FeatureEntry,
    pendingActions: Set<String>,
    onExecute: (FeatureAction, String, HubSection?, ActionResultPresentation) -> Unit,
    onDialog: (HubDialog) -> Unit,
    onDetails: (FeatureDetails) -> Unit,
    onConfirmation: (ConfirmationRequest) -> Unit,
) {
    val installKey = "plugin:install:${entry.id}"
    val uninstallKey = "plugin:uninstall:${entry.id}"
    val pending = installKey in pendingActions || uninstallKey in pendingActions
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.title, modifier = Modifier.weight(1f))
                if (entry.installed == true) StatusBadge("已安装", StatusTone.POSITIVE)
            }
        },
        supportingContent = {
            Column {
                if (entry.description.isNotBlank()) {
                    Text(entry.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                entry.parentId?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
        },
        leadingContent = { Icon(Icons.Rounded.Extension, contentDescription = null) },
        trailingContent = {
            when {
                pending -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                entry.installed == true -> PluginActionsMenu(
                    entry = entry,
                    onRead = {
                        val marketplacePath = entry.metadata["marketplacePath"]?.ifBlank { null }
                        val remoteMarketplace = if (marketplacePath == null) entry.metadata["marketplaceName"] else null
                        onExecute(
                            FeatureAction.ReadPlugin(entry.title, marketplacePath, remoteMarketplace),
                            "plugin:read:${entry.id}",
                            null,
                            ActionResultPresentation.DETAILS,
                        )
                    },
                    onReadSkill = entry.metadata["remotePluginId"]
                        ?.takeIf(String::isNotBlank)
                        ?.let { { onDialog(HubDialog.PluginSkill(entry)) } },
                    onUninstall = {
                        onConfirmation(
                            ConfirmationRequest(
                                title = "卸载 ${entry.title}",
                                message = "卸载后，新任务将无法使用此插件包含的 Skills 和工具",
                                confirmLabel = "卸载",
                                onConfirm = {
                                    onExecute(
                                        FeatureAction.UninstallPlugin(entry.id),
                                        uninstallKey,
                                        HubSection.PLUGINS,
                                        ActionResultPresentation.MESSAGE,
                                    )
                                },
                            ),
                        )
                    },
                )

                else -> IconButton(
                    onClick = {
                        val marketplacePath = entry.metadata["marketplacePath"]?.ifBlank { null }
                        val remoteMarketplace = if (marketplacePath == null) entry.metadata["marketplaceName"] else null
                        onExecute(
                            FeatureAction.InstallPlugin(entry.title, marketplacePath, remoteMarketplace),
                            installKey,
                            HubSection.PLUGINS,
                            ActionResultPresentation.MESSAGE,
                        )
                    },
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "安装 ${entry.title}")
                }
            }
        },
        modifier = Modifier.clickable { onDetails(FeatureDetails(entry)) },
    )
}

@Composable
private fun PluginActionsMenu(
    entry: FeatureEntry,
    onRead: () -> Unit,
    onReadSkill: (() -> Unit)?,
    onUninstall: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "${entry.title} 操作")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("读取插件定义") },
                onClick = {
                    expanded = false
                    onRead()
                },
            )
            onReadSkill?.let { action ->
                DropdownMenuItem(
                    text = { Text("读取 Skill") },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("卸载") },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onUninstall()
                },
            )
        }
    }
}

@Composable
private fun MarketplaceRow(
    entry: FeatureEntry,
    pendingActions: Set<String>,
    onExecute: (FeatureAction, String, HubSection?, ActionResultPresentation) -> Unit,
    onDetails: (FeatureDetails) -> Unit,
    onConfirmation: (ConfirmationRequest) -> Unit,
) {
    val upgradeKey = "marketplace:upgrade:${entry.id}"
    val removeKey = "marketplace:remove:${entry.id}"
    val pending = upgradeKey in pendingActions || removeKey in pendingActions
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(entry.title) },
        supportingContent = {
            Text(entry.description.ifBlank { "插件目录来源" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { Icon(Icons.Rounded.Source, contentDescription = null) },
        trailingContent = {
            if (pending) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "${entry.title} 来源操作")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("检查并升级") },
                            onClick = {
                                expanded = false
                                onExecute(
                                    FeatureAction.UpgradeMarketplace(entry.id),
                                    upgradeKey,
                                    HubSection.PLUGINS,
                                    ActionResultPresentation.MESSAGE,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("移除来源") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = {
                                expanded = false
                                onConfirmation(
                                    ConfirmationRequest(
                                        title = "移除 ${entry.title}",
                                        message = "该来源中的插件将不再出现在插件目录中",
                                        confirmLabel = "移除",
                                        onConfirm = {
                                            onExecute(
                                                FeatureAction.RemoveMarketplace(entry.id),
                                                removeKey,
                                                HubSection.PLUGINS,
                                                ActionResultPresentation.MESSAGE,
                                            )
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable { onDetails(FeatureDetails(entry)) },
    )
}

@Composable
fun SharedPluginsContent(
    catalog: FeatureCatalog,
    pendingActions: Set<String>,
    onExecute: (FeatureAction, String, HubSection?, ActionResultPresentation) -> Unit,
    onDialog: (HubDialog) -> Unit,
    onDetails: (FeatureDetails) -> Unit,
    onConfirmation: (ConfirmationRequest) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        warnings(catalog.warnings)
        if (catalog.entries.isEmpty()) item("shared-empty") { EmptyState("尚未共享插件") }
        items(catalog.entries, key = FeatureEntry::id) { entry ->
            val checkoutKey = "shared:checkout:${entry.id}"
            val deleteKey = "shared:delete:${entry.id}"
            val pending = checkoutKey in pendingActions || deleteKey in pendingActions
            var expanded by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(entry.title) },
                supportingContent = {
                    Column {
                        Text(entry.description.ifBlank { "共享插件" })
                        entry.path?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                },
                leadingContent = { Icon(Icons.Rounded.Link, contentDescription = null) },
                trailingContent = {
                    if (pending) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Box {
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "${entry.title} 共享操作")
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                entry.metadata["shareUrl"]?.takeIf(String::isNotBlank)?.let { url ->
                                    DropdownMenuItem(
                                        text = { Text("打开分享") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Launch, contentDescription = null) },
                                        onClick = {
                                            expanded = false
                                            onOpenUrl(url)
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("共享设置") },
                                    onClick = {
                                        expanded = false
                                        onDialog(HubDialog.SharedTargets(entry))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("检出到本机") },
                                    onClick = {
                                        expanded = false
                                        onExecute(
                                            FeatureAction.CheckoutSharedPlugin(entry.id),
                                            checkoutKey,
                                            HubSection.SHARED_PLUGINS,
                                            ActionResultPresentation.MESSAGE,
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("删除共享插件") },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                    onClick = {
                                        expanded = false
                                        onConfirmation(
                                            ConfirmationRequest(
                                                title = "删除 ${entry.title}",
                                                message = "共享链接和已配置的共享目标将被永久移除",
                                                confirmLabel = "删除",
                                                onConfirm = {
                                                    onExecute(
                                                        FeatureAction.DeleteSharedPlugin(entry.id),
                                                        deleteKey,
                                                        HubSection.SHARED_PLUGINS,
                                                        ActionResultPresentation.MESSAGE,
                                                    )
                                                },
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.clickable { onDetails(FeatureDetails(entry)) },
            )
            HorizontalDivider(Modifier.padding(start = 56.dp))
        }
    }
}

@Composable
fun McpContent(
    catalog: FeatureCatalog,
    pendingActions: Set<String>,
    onExecute: (FeatureAction, String, HubSection?, ActionResultPresentation) -> Unit,
    onDialog: (HubDialog) -> Unit,
    onDetails: (FeatureDetails) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedServers by remember { mutableStateOf(emptySet<String>()) }
    var showDeveloperActions by rememberSaveable { mutableStateOf(false) }
    val servers = catalog.entries.filter { it.kind == FeatureKind.MCP_SERVER }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("mcp-developer-actions") {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                ListItem(
                    headlineContent = { Text("开发者操作") },
                    supportingContent = { Text("允许手动读取资源和调用工具") },
                    trailingContent = {
                        Switch(
                            checked = showDeveloperActions,
                            onCheckedChange = { showDeveloperActions = it },
                        )
                    },
                )
            }
        }
        warnings(catalog.warnings)
        if (servers.isEmpty()) item("mcp-empty") { EmptyState("尚未配置 MCP 服务器") }
        items(servers, key = FeatureEntry::id) { server ->
            val children = catalog.entries.filter { it.parentId == server.id }
            val expanded = server.id in expandedServers
            McpServerBlock(
                server = server,
                children = children,
                expanded = expanded,
                pendingActions = pendingActions,
                showDeveloperActions = showDeveloperActions,
                onToggle = {
                    expandedServers = if (expanded) expandedServers - server.id else expandedServers + server.id
                },
                onExecute = onExecute,
                onDialog = onDialog,
                onDetails = onDetails,
            )
        }
    }
}

@Composable
private fun McpServerBlock(
    server: FeatureEntry,
    children: List<FeatureEntry>,
    expanded: Boolean,
    pendingActions: Set<String>,
    showDeveloperActions: Boolean,
    onToggle: () -> Unit,
    onExecute: (FeatureAction, String, HubSection?, ActionResultPresentation) -> Unit,
    onDialog: (HubDialog) -> Unit,
    onDetails: (FeatureDetails) -> Unit,
) {
    val authStatus = server.metadata["authStatus"].orEmpty()
    val loginKey = "mcp:login:${server.id}"
    val toolCount = children.count { it.kind == FeatureKind.MCP_TOOL }
    val resourceCount = children.count { it.kind == FeatureKind.MCP_RESOURCE }
    Surface(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            ListItem(
                headlineContent = { Text(server.title) },
                supportingContent = {
                    Text("${authStatusLabel(authStatus)} · $toolCount 个工具 · $resourceCount 个资源")
                },
                leadingContent = { Icon(Icons.Rounded.Hub, contentDescription = null) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (loginKey in pendingActions) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else if (authStatus == "notLoggedIn") {
                            TextButton(
                                onClick = {
                                    onExecute(
                                        FeatureAction.LoginMcpServer(server.id, null),
                                        loginKey,
                                        HubSection.MCP,
                                        ActionResultPresentation.MESSAGE,
                                    )
                                },
                            ) {
                                Text("登录")
                            }
                        } else {
                            StatusBadge(authStatusLabel(authStatus), authStatusTone(authStatus))
                        }
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (expanded) "收起 ${server.title}" else "展开 ${server.title}",
                        )
                    }
                },
                modifier = Modifier.clickable(onClick = onToggle),
            )
            if (expanded) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { onDetails(FeatureDetails(server)) }) { Text("服务器详情") }
                }
                if (children.isEmpty()) {
                    Text(
                        "服务器没有公开工具或资源",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                children.forEach { entry ->
                    HorizontalDivider(Modifier.padding(start = 48.dp))
                    McpChildRow(
                        entry = entry,
                        pendingActions = pendingActions,
                        showDeveloperActions = showDeveloperActions,
                        onExecute = onExecute,
                        onDialog = onDialog,
                        onDetails = onDetails,
                    )
                }
            }
        }
    }
}

@Composable
private fun McpChildRow(
    entry: FeatureEntry,
    pendingActions: Set<String>,
    showDeveloperActions: Boolean,
    onExecute: (FeatureAction, String, HubSection?, ActionResultPresentation) -> Unit,
    onDialog: (HubDialog) -> Unit,
    onDetails: (FeatureDetails) -> Unit,
) {
    val resourceKey = "mcp:resource:${entry.id}"
    ListItem(
        headlineContent = { Text(entry.title) },
        supportingContent = {
            if (entry.description.isNotBlank()) {
                Text(entry.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = {
            Icon(
                if (entry.kind == FeatureKind.MCP_TOOL) Icons.Rounded.Build else Icons.Rounded.Description,
                contentDescription = null,
            )
        },
        trailingContent = {
            if (showDeveloperActions) when {
                resourceKey in pendingActions -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                entry.kind == FeatureKind.MCP_TOOL -> TextButton(onClick = { onDialog(HubDialog.McpTool(entry)) }) {
                    Text("调用")
                }

                entry.kind == FeatureKind.MCP_RESOURCE -> TextButton(
                    onClick = {
                        onExecute(
                            FeatureAction.ReadMcpResource(
                                server = entry.metadata.getValue("server"),
                                uri = entry.metadata.getValue("uri"),
                                threadId = null,
                            ),
                            resourceKey,
                            null,
                            ActionResultPresentation.DETAILS,
                        )
                    },
                ) {
                    Text("读取")
                }
            }
        },
        modifier = Modifier.padding(start = 24.dp).clickable { onDetails(FeatureDetails(entry)) },
    )
}

private fun authStatusLabel(status: String): String = when (status) {
    "notLoggedIn" -> "待登录"
    "authenticated", "loggedIn" -> "已认证"
    "notSupported" -> "无需认证"
    "disabled" -> "已停用"
    "" -> "状态未知"
    else -> status
}

private fun authStatusTone(status: String): StatusTone = when (status) {
    "authenticated", "loggedIn", "notSupported" -> StatusTone.POSITIVE
    "notLoggedIn" -> StatusTone.NEGATIVE
    else -> StatusTone.NEUTRAL
}
