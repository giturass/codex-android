@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Webhook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.hebo.codex.domain.ModelOption
import com.hebo.codex.ui.featureScopeLabel
import com.hebo.codex.ui.featureTrustLabel
import com.hebo.codex.ui.hookEventLabel

@Composable
fun HubDataContent(
    section: HubSection,
    data: HubData,
    state: ServerHubUiState,
    viewModel: ServerHubViewModel,
    onDialog: (HubDialog) -> Unit,
    onDetails: (FeatureDetails) -> Unit,
    onConfirmation: (ConfirmationRequest) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (section) {
        HubSection.MODELS -> (data as? HubData.Models)?.let {
            ModelsContent(
                models = it.value,
                capabilitiesLoading = ServerHubViewModel.MODEL_CAPABILITIES_ACTION_KEY in state.pendingActions,
                onCapabilities = viewModel::loadModelCapabilities,
                modifier = modifier,
            )
        }

        HubSection.ACCOUNT -> (data as? HubData.Account)?.let {
            AccountContent(
                account = it.value,
                loginChallenge = state.loginChallenge,
                pendingActions = state.pendingActions,
                onApiKeyLogin = { onDialog(HubDialog.ApiKeyLogin) },
                onChatGptLogin = { viewModel.loginWithChatGpt(false) },
                onDeviceLogin = { viewModel.loginWithChatGpt(true) },
                onCancelLogin = viewModel::cancelLogin,
                onLogout = {
                    onConfirmation(
                        ConfirmationRequest(
                            title = "退出 Codex 账号",
                            message = "确定要在当前服务器上退出登录吗",
                            confirmLabel = "退出登录",
                            onConfirm = viewModel::logout,
                        ),
                    )
                },
                onOpenUrl = onOpenUrl,
                onResetLimit = { onDialog(HubDialog.RateLimitReset) },
                onNudge = { onDialog(HubDialog.CreditsNudge) },
                modifier = modifier,
            )
        }

        HubSection.CONFIG -> (data as? HubData.Configuration)?.let {
            ConfigurationContent(
                configuration = it.value,
                projectScoped = state.cwd != null,
                modifier = modifier,
            )
        }

        HubSection.PLUGINS -> (data as? HubData.Catalog)?.let {
            PluginsContent(
                catalog = it.value,
                projectScoped = state.cwd != null,
                sharedState = state.sections[HubSection.SHARED_PLUGINS],
                pendingActions = state.pendingActions,
                onLoadShared = { viewModel.load(HubSection.SHARED_PLUGINS) },
                onRefreshShared = { viewModel.refresh(HubSection.SHARED_PLUGINS) },
                onExecute = viewModel::execute,
                onDialog = onDialog,
                onDetails = onDetails,
                onConfirmation = onConfirmation,
                onOpenUrl = onOpenUrl,
                modifier = modifier,
            )
        }

        HubSection.MCP -> (data as? HubData.Catalog)?.let {
            McpContent(
                catalog = it.value,
                pendingActions = state.pendingActions,
                onExecute = viewModel::execute,
                onDialog = onDialog,
                onDetails = onDetails,
                modifier = modifier,
            )
        }

        HubSection.PERMISSIONS,
        HubSection.SKILLS,
        HubSection.HOOKS,
        HubSection.APPS,
        HubSection.SHARED_PLUGINS,
        -> (data as? HubData.Catalog)?.let { catalogData ->
            when (section) {
                HubSection.PERMISSIONS -> PermissionContent(
                    catalog = catalogData.value,
                    onDetails = onDetails,
                    modifier = modifier,
                )

                HubSection.SKILLS -> SkillsContent(
                    catalog = catalogData.value,
                    pendingActions = state.pendingActions,
                    onExecute = viewModel::execute,
                    onDetails = onDetails,
                    modifier = modifier,
                )

                HubSection.HOOKS -> HooksContent(
                    catalog = catalogData.value,
                    onDetails = onDetails,
                    modifier = modifier,
                )

                HubSection.APPS -> AppsContent(
                    catalog = catalogData.value,
                    pendingActions = state.pendingActions,
                    onExecute = viewModel::execute,
                    onOpenUrl = onOpenUrl,
                    onDetails = onDetails,
                    modifier = modifier,
                )

                HubSection.SHARED_PLUGINS -> SharedPluginsContent(
                    catalog = catalogData.value,
                    pendingActions = state.pendingActions,
                    onExecute = viewModel::execute,
                    onDialog = onDialog,
                    onDetails = onDetails,
                    onConfirmation = onConfirmation,
                    onOpenUrl = onOpenUrl,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun ModelsContent(
    models: List<ModelOption>,
    capabilitiesLoading: Boolean,
    onCapabilities: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item("models-intro") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("模型能力", style = MaterialTheme.typography.titleMedium)
                Text(
                    "模型选择应在创建任务时完成，此处只展示服务器提供的能力",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onCapabilities, enabled = !capabilitiesLoading) {
                    if (capabilitiesLoading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("查看提供方技术详情")
                }
            }
        }
        if (models.isEmpty()) {
            item("models-empty") { EmptyState("服务器没有返回可用模型") }
        } else {
            items(models, key = ModelOption::id) { model ->
                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(model.displayName, modifier = Modifier.weight(1f))
                            if (model.isDefault) StatusBadge("默认", StatusTone.POSITIVE)
                            if (model.hidden) {
                                Spacer(Modifier.width(6.dp))
                                StatusBadge("隐藏", StatusTone.NEUTRAL)
                            }
                        }
                    },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(model.model, style = MaterialTheme.typography.labelMedium)
                            Text(model.description, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(
                                "推理 ${model.reasoningEfforts.joinToString { it.label }}" +
                                    if (model.serviceTiers.isEmpty()) "" else
                                        " · 服务层级 ${model.serviceTiers.joinToString { it.label }}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    leadingContent = { Icon(Icons.Rounded.SmartToy, contentDescription = null) },
                )
                HorizontalDivider(Modifier.padding(start = 56.dp))
            }
        }
    }
}

@Composable
private fun PermissionContent(
    catalog: FeatureCatalog,
    onDetails: (FeatureDetails) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        warnings(catalog.warnings)
        if (catalog.entries.isEmpty()) {
            item("permissions-empty") { EmptyState("没有可用的权限策略") }
        }
        items(catalog.entries, key = FeatureEntry::id) { entry ->
            val allowed = entry.enabled
            ListItem(
                headlineContent = { Text(entry.title) },
                supportingContent = {
                    if (entry.description.isNotBlank()) Text(entry.description)
                },
                leadingContent = { Icon(Icons.Rounded.Policy, contentDescription = null) },
                trailingContent = {
                    StatusBadge(
                        label = when (allowed) {
                            true -> "允许"
                            false -> "受限"
                            null -> "未知"
                        },
                        tone = when (allowed) {
                            true -> StatusTone.POSITIVE
                            false -> StatusTone.NEGATIVE
                            null -> StatusTone.NEUTRAL
                        },
                    )
                },
                modifier = Modifier.clickable { onDetails(FeatureDetails(entry)) },
            )
            HorizontalDivider(Modifier.padding(start = 56.dp))
        }
    }
}

@Composable
private fun SkillsContent(
    catalog: FeatureCatalog,
    pendingActions: Set<String>,
    onExecute: (FeatureAction, String, HubSection?, ActionResultPresentation) -> Unit,
    onDetails: (FeatureDetails) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedScope by rememberSaveable { mutableStateOf<String?>(null) }
    val scopes = remember(catalog.entries) {
        catalog.entries.mapNotNull { it.metadata["scope"]?.ifBlank { null } }.distinct().sorted()
    }
    val filtered = remember(catalog.entries, query, selectedScope) {
        catalog.entries.filter { entry ->
            (selectedScope == null || entry.metadata["scope"] == selectedScope) &&
                (query.isBlank() || entry.searchText().contains(query, ignoreCase = true))
        }
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        item("skills-search") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CatalogSearchField(query = query, onQueryChange = { query = it }, label = "搜索 Skills")
                if (scopes.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        FilterChip(
                            selected = selectedScope == null,
                            onClick = { selectedScope = null },
                            label = { Text("全部") },
                        )
                        scopes.forEach { scope ->
                            Spacer(Modifier.width(8.dp))
                            FilterChip(
                                selected = selectedScope == scope,
                                onClick = { selectedScope = scope },
                            label = { Text(featureScopeLabel(scope)) },
                            )
                        }
                    }
                }
            }
        }
        warnings(catalog.warnings)
        if (filtered.isEmpty()) {
            item("skills-empty") {
                EmptyState(if (query.isBlank()) "当前作用域没有 Skills" else "没有匹配的 Skills")
            }
        }
        items(filtered, key = FeatureEntry::id) { entry ->
            val actionKey = "skill:${entry.id}"
            val pending = actionKey in pendingActions
            ListItem(
                headlineContent = { Text(entry.title) },
                supportingContent = {
                    Column {
                        Text(entry.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        entry.metadata["scope"]?.takeIf(String::isNotBlank)?.let {
                            Text(featureScopeLabel(it), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                leadingContent = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                trailingContent = {
                    if (pending) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else if (entry.enabled != null) {
                        Switch(
                            checked = entry.enabled,
                            onCheckedChange = { enabled ->
                                onExecute(
                                    FeatureAction.SetSkillEnabled(entry.title, entry.path, enabled),
                                    actionKey,
                                    HubSection.SKILLS,
                                    ActionResultPresentation.MESSAGE,
                                )
                            },
                        )
                    }
                },
                modifier = Modifier.clickable { onDetails(FeatureDetails(entry)) },
            )
            HorizontalDivider(Modifier.padding(start = 56.dp))
        }
    }
}

@Composable
private fun HooksContent(
    catalog: FeatureCatalog,
    onDetails: (FeatureDetails) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        warnings(catalog.warnings)
        if (catalog.entries.isEmpty()) {
            item("hooks-empty") { EmptyState("当前作用域没有 Hooks") }
        }
        items(catalog.entries, key = FeatureEntry::id) { entry ->
            val trust = entry.metadata["trust"].orEmpty()
            ListItem(
                headlineContent = { Text(entry.title) },
                supportingContent = {
                    Column {
                        if (entry.description.isNotBlank()) {
                            Text(entry.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            listOfNotNull(
                                entry.metadata["event"]?.ifBlank { null }?.let(::hookEventLabel),
                                trust.ifBlank { null }?.let(::featureTrustLabel),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                leadingContent = { Icon(Icons.Rounded.Webhook, contentDescription = null) },
                trailingContent = {
                    StatusBadge(
                        if (entry.enabled == false) "停用" else "启用",
                        if (entry.enabled == false) StatusTone.NEUTRAL else StatusTone.POSITIVE,
                    )
                },
                modifier = Modifier.clickable { onDetails(FeatureDetails(entry)) },
            )
            HorizontalDivider(Modifier.padding(start = 56.dp))
        }
    }
}

@Composable
private fun AppsContent(
    catalog: FeatureCatalog,
    pendingActions: Set<String>,
    onExecute: (FeatureAction, String, HubSection?, ActionResultPresentation) -> Unit,
    onOpenUrl: (String) -> Unit,
    onDetails: (FeatureDetails) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(catalog.entries, query) {
        catalog.entries.filter { query.isBlank() || it.searchText().contains(query, ignoreCase = true) }
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        item("apps-search") {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                CatalogSearchField(query = query, onQueryChange = { query = it }, label = "搜索 Apps")
            }
        }
        warnings(catalog.warnings)
        if (filtered.isEmpty()) {
            item("apps-empty") { EmptyState(if (query.isBlank()) "当前没有可用 Apps" else "没有匹配的 Apps") }
        }
        items(filtered, key = FeatureEntry::id) { entry ->
            val accessible = entry.metadata["accessible"] == "true"
            val actionKey = "app:${entry.id}"
            val pending = actionKey in pendingActions
            ListItem(
                headlineContent = { Text(entry.title) },
                supportingContent = {
                    Column {
                        Text(entry.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (accessible) "已授权访问" else "需要安装或授权",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                leadingContent = { Icon(Icons.Rounded.Apps, contentDescription = null) },
                trailingContent = {
                    when {
                        pending -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        !accessible && entry.metadata["installUrl"].orEmpty().isNotBlank() -> {
                            IconButton(onClick = { onOpenUrl(entry.metadata.getValue("installUrl")) }) {
                                Icon(Icons.AutoMirrored.Rounded.Launch, contentDescription = "安装或授权 ${entry.title}")
                            }
                        }

                        entry.enabled != null -> Switch(
                            checked = entry.enabled,
                            onCheckedChange = { enabled ->
                                onExecute(
                                    FeatureAction.WriteConfigValue(
                                        "apps.${entry.id.toConfigKeySegment()}.enabled",
                                        enabled.toString(),
                                    ),
                                    actionKey,
                                    HubSection.APPS,
                                    ActionResultPresentation.MESSAGE,
                                )
                            },
                        )
                    }
                },
                modifier = Modifier.clickable { onDetails(FeatureDetails(entry)) },
            )
            HorizontalDivider(Modifier.padding(start = 56.dp))
        }
    }
}

@Composable
fun CatalogSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(label) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "清除搜索")
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun WarningsPanel(warnings: List<String>) {
    if (warnings.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("${warnings.size} 个加载提醒", modifier = Modifier.weight(1f))
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "查看")
                }
            }
            if (expanded) {
                warnings.forEach { warning ->
                    Text(
                        text = warning,
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

fun androidx.compose.foundation.lazy.LazyListScope.warnings(warnings: List<String>) {
    if (warnings.isNotEmpty()) item("warnings") { WarningsPanel(warnings) }
}

enum class StatusTone {
    POSITIVE,
    NEGATIVE,
    NEUTRAL,
}

@Composable
fun StatusBadge(label: String, tone: StatusTone) {
    val colors = when (tone) {
        StatusTone.POSITIVE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        StatusTone.NEGATIVE -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = CircleShape, color = colors.first, contentColor = colors.second) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

fun FeatureEntry.searchText(): String = listOf(title, description, path, parentId)
    .filterNotNull()
    .joinToString(" ")

private fun String.toConfigKeySegment(): String = if (all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
    this
} else {
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
