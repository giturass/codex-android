@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hebo.codex.R
import com.hebo.codex.domain.FeatureAction
import com.hebo.codex.domain.FeatureActionResult
import com.hebo.codex.domain.FeatureEntry
import com.hebo.codex.ui.featureScopeLabel
import com.hebo.codex.ui.featureTrustLabel
import com.hebo.codex.ui.hookEventLabel
import com.hebo.codex.ui.mcpAuthStatusLabel
import com.hebo.codex.ui.pluginDiscoverabilityLabel

data class FeatureDetails(val entry: FeatureEntry)

data class ConfirmationRequest(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
)

sealed interface HubDialog {
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
}

@Composable
fun HubInputDialog(
    value: HubDialog,
    onDismiss: () -> Unit,
    onSubmit: (first: String, second: String, enabled: Boolean) -> Unit,
) {
    var first by remember(value) { mutableStateOf(value.defaultFirst) }
    var second by remember(value) { mutableStateOf(value.defaultSecond) }
    var enabled by remember(value) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(value.dialogTitle) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                value.description?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (value) {
                    is HubDialog.SharedTargets -> ChoiceField(
                        title = "可见范围",
                        choices = listOf(
                            "UNLISTED" to pluginDiscoverabilityLabel("UNLISTED"),
                            "PRIVATE" to pluginDiscoverabilityLabel("PRIVATE"),
                        ),
                        selected = first,
                        onSelect = { first = it },
                    )

                    HubDialog.CreditsNudge -> ChoiceField(
                        title = "需要补充的额度",
                        choices = listOf(
                            "credits" to "额外额度",
                            "usage_limit" to "用量限制",
                        ),
                        selected = first,
                        onSelect = { first = it },
                    )

                    else -> Unit
                }
                if (value !is HubDialog.SharedTargets && value != HubDialog.CreditsNudge) {
                    value.firstLabel?.let { label ->
                        OutlinedTextField(
                            value = first,
                            onValueChange = { first = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(label) },
                            minLines = if (
                                value is HubDialog.McpTool ||
                                value == HubDialog.ConfigValue ||
                                value == HubDialog.ConfigBatch
                            ) 3 else 1,
                            visualTransformation = if (value == HubDialog.ApiKeyLogin) {
                                PasswordVisualTransformation()
                            } else {
                                VisualTransformation.None
                            },
                        )
                    }
                }
                value.secondLabel?.let { label ->
                    OutlinedTextField(
                        value = second,
                        onValueChange = { second = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(label) },
                        minLines = if (value is HubDialog.SharedTargets) 3 else 1,
                    )
                }
                if (value == HubDialog.Feedback || value == HubDialog.ConfigBatch || value == HubDialog.ConfigValue) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (value) {
                                HubDialog.Feedback -> "附带日志"
                                HubDialog.ConfigBatch -> "写入后立即重新加载用户配置"
                                else -> "保留未填写的现有配置"
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(first, second, enabled) },
                enabled = value.isValid(first, second),
            ) {
                Text(value.confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ChoiceField(
    title: String,
    choices: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        choices.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
fun FeatureDetailsDialog(details: FeatureDetails, onDismiss: () -> Unit) {
    val entry = details.entry
    var showTechnicalDetails by remember(entry.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                entry.description.takeIf(String::isNotBlank)?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                entry.parentId?.let { DetailValue("来源", it) }
                entry.path?.let { DetailValue("路径", it) }
                entry.metadata
                    .filterKeys { it in FEATURE_DETAIL_LABELS }
                    .filterValues(String::isNotBlank)
                    .forEach { (key, value) ->
                        DetailValue(FEATURE_DETAIL_LABELS.getValue(key), featureDetailValue(key, value))
                    }
                TextButton(onClick = { showTechnicalDetails = !showTechnicalDetails }) {
                    Text(
                        stringResource(
                            if (showTechnicalDetails) R.string.common_hide_technical_details
                            else R.string.common_show_technical_details,
                        ),
                    )
                }
                if (showTechnicalDetails) JsonBlock(entry.rawJson)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
fun RawResultDialog(result: FeatureActionResult, onDismiss: () -> Unit) {
    var showTechnicalDetails by remember(result.rawJson) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(result.message) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            ) {
                Text("操作已完成，服务器响应可在技术详情中查看")
                TextButton(onClick = { showTechnicalDetails = !showTechnicalDetails }) {
                    Text(
                        stringResource(
                            if (showTechnicalDetails) R.string.common_hide_technical_details
                            else R.string.common_show_technical_details,
                        ),
                    )
                }
                if (showTechnicalDetails) JsonBlock(result.rawJson)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
fun ConfirmationDialog(
    request: ConfirmationRequest,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(request.title) },
        text = { Text(request.message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(request.confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun JsonBlock(json: String) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface),
    ) {
        SelectionContainer {
            Text(
                text = json,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DetailValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        SelectionContainer {
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

val HubDialog.actionKey: String
    get() = when (this) {
        HubDialog.SkillRoots -> "skills:roots"
        HubDialog.AddMarketplace -> "plugins:add-marketplace"
        HubDialog.SharePlugin -> "plugins:share"
        HubDialog.ReloadMcp -> "mcp:reload"
        HubDialog.ConfigValue -> "config:value"
        HubDialog.ConfigBatch -> "config:batch"
        HubDialog.Feedback -> "feedback:upload"
        HubDialog.ApiKeyLogin -> ServerHubViewModel.ACCOUNT_LOGIN_ACTION_KEY
        HubDialog.RateLimitReset -> ServerHubViewModel.RATE_LIMIT_ACTION_KEY
        HubDialog.CreditsNudge -> ServerHubViewModel.CREDITS_NUDGE_ACTION_KEY
        is HubDialog.McpTool -> "mcp:tool:${entry.id}"
        is HubDialog.PluginSkill -> "plugin:skill:${entry.id}"
        is HubDialog.SharedTargets -> "shared:targets:${entry.id}"
    }

val HubDialog.refreshSection: HubSection?
    get() = when (this) {
        HubDialog.SkillRoots -> HubSection.SKILLS
        HubDialog.AddMarketplace -> HubSection.PLUGINS
        HubDialog.SharePlugin, is HubDialog.SharedTargets -> HubSection.SHARED_PLUGINS
        HubDialog.ReloadMcp -> HubSection.MCP
        HubDialog.ConfigValue, HubDialog.ConfigBatch -> HubSection.CONFIG
        HubDialog.RateLimitReset, HubDialog.CreditsNudge, HubDialog.ApiKeyLogin -> HubSection.ACCOUNT
        HubDialog.Feedback, is HubDialog.McpTool, is HubDialog.PluginSkill -> null
    }

val HubDialog.resultPresentation: ActionResultPresentation
    get() = when (this) {
        is HubDialog.McpTool, is HubDialog.PluginSkill -> ActionResultPresentation.DETAILS
        else -> ActionResultPresentation.MESSAGE
    }

private val HubDialog.dialogTitle: String
    get() = when (this) {
        HubDialog.SkillRoots -> "Skill 来源目录"
        HubDialog.AddMarketplace -> "添加插件来源"
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
    }

private val HubDialog.description: String?
    get() = when (this) {
        HubDialog.SkillRoots -> "每行填写一个服务端绝对路径"
        HubDialog.AddMarketplace -> "添加 Git 仓库作为插件目录来源"
        HubDialog.SharePlugin -> "将服务端本地插件发布为共享插件"
        HubDialog.ReloadMcp -> "重新读取服务端 MCP 配置并刷新已加载会话"
        HubDialog.ConfigValue, HubDialog.ConfigBatch -> "此操作会直接修改服务端配置，请确认键和值的作用域"
        HubDialog.Feedback -> "反馈由当前服务器上传"
        HubDialog.ApiKeyLogin -> "API Key 将发送至当前服务器完成登录"
        HubDialog.RateLimitReset -> "确认后将使用一次可用的额度重置，服务器会自动选择可用项"
        HubDialog.CreditsNudge -> "选择需要补充的额度类型，系统会通知工作区所有者"
        is HubDialog.McpTool -> "参数必须是合法 JSON，并关联一个会话 ID"
        is HubDialog.PluginSkill -> "读取远程插件中指定的 Skill"
        is HubDialog.SharedTargets -> "选择插件的可见范围，并填写需要共享到的目标"
    }

private val HubDialog.confirmLabel: String
    get() = when (this) {
        HubDialog.ReloadMcp -> "重新加载"
        HubDialog.ApiKeyLogin -> "登录"
        HubDialog.Feedback -> "提交"
        is HubDialog.McpTool -> "调用"
        else -> "确认"
    }

private val HubDialog.firstLabel: String?
    get() = when (this) {
        HubDialog.SkillRoots -> "绝对路径"
        HubDialog.AddMarketplace -> "Git URL 或 owner/repo"
        HubDialog.SharePlugin -> "插件绝对路径"
        HubDialog.ConfigValue -> "配置键路径"
        HubDialog.ConfigBatch -> "配置编辑 JSON 数组"
        HubDialog.Feedback -> "分类"
        HubDialog.ApiKeyLogin -> "API Key"
        HubDialog.RateLimitReset -> null
        HubDialog.CreditsNudge -> null
        is HubDialog.McpTool -> "参数 JSON"
        is HubDialog.PluginSkill -> "Skill 名称"
        is HubDialog.SharedTargets -> null
        HubDialog.ReloadMcp -> null
    }

private val HubDialog.secondLabel: String?
    get() = when (this) {
        HubDialog.AddMarketplace -> "第一行分支或 Tag，后续行稀疏检出路径，均可留空"
        HubDialog.SharePlugin -> "分享选项 JSON 对象"
        HubDialog.ConfigValue -> "JSON 值"
        HubDialog.Feedback -> "原因"
        is HubDialog.McpTool -> "会话 ID"
        is HubDialog.SharedTargets -> "共享目标（JSON 格式）"
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

private fun HubDialog.isValid(first: String, second: String): Boolean {
    val firstValid = when {
        this is HubDialog.SharedTargets -> first == "UNLISTED" || first == "PRIVATE"
        this == HubDialog.CreditsNudge -> first == "credits" || first == "usage_limit"
        firstLabel == null || this == HubDialog.RateLimitReset -> true
        else -> first.isNotBlank()
    }
    val secondRequired = this == HubDialog.SharePlugin ||
        this == HubDialog.ConfigValue ||
        this is HubDialog.McpTool ||
        this is HubDialog.SharedTargets
    return firstValid && (!secondRequired || second.isNotBlank())
}

fun HubDialog.toAction(first: String, second: String, enabled: Boolean): FeatureAction? = when (this) {
    HubDialog.SkillRoots -> FeatureAction.SetSkillExtraRoots(
        first.lineSequence().map(String::trim).filter(String::isNotEmpty).toList(),
    )

    HubDialog.AddMarketplace -> second.lineSequence().map(String::trim).filter(String::isNotEmpty).toList().let {
        FeatureAction.AddMarketplace(first.trim(), it.firstOrNull(), it.drop(1))
    }

    HubDialog.SharePlugin -> FeatureAction.SaveSharedPlugin(first.trim(), second)
    HubDialog.ReloadMcp -> FeatureAction.ReloadMcpServers
    HubDialog.ConfigValue -> FeatureAction.WriteConfigValue(
        keyPath = first.trim(),
        valueJson = second,
        mergeStrategy = if (enabled) "upsert" else "replace",
    )

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

    HubDialog.ApiKeyLogin, HubDialog.RateLimitReset, HubDialog.CreditsNudge -> null
}

private val FEATURE_DETAIL_LABELS = mapOf(
    "scope" to "作用域",
    "event" to "触发事件",
    "trust" to "可信状态",
    "authStatus" to "认证状态",
    "accessible" to "可访问",
    "marketplaceName" to "插件来源",
    "discoverability" to "可发现性",
)

private fun featureDetailValue(key: String, value: String): String = when (key) {
    "scope" -> featureScopeLabel(value)
    "event" -> hookEventLabel(value)
    "trust" -> featureTrustLabel(value)
    "authStatus" -> mcpAuthStatusLabel(value)

    "accessible" -> when (value) {
        "true" -> "可访问"
        "false" -> "不可访问"
        else -> "状态未知"
    }

    "discoverability" -> pluginDiscoverabilityLabel(value)

    else -> value
}
