@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.hub

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hebo.codex.R
import com.hebo.codex.domain.AccountOverview
import com.hebo.codex.domain.AccountRateLimit
import com.hebo.codex.domain.AccountRateLimits
import com.hebo.codex.domain.AccountUsage
import com.hebo.codex.domain.LoginChallenge
import com.hebo.codex.domain.RateLimitWindow
import com.hebo.codex.domain.ServerConfiguration
import com.hebo.codex.ui.components.relativeTime
import com.hebo.codex.ui.displayLabelRes
import com.hebo.codex.ui.formatCount
import com.hebo.codex.ui.formatDateTime
import com.hebo.codex.ui.formatDurationSeconds
import com.hebo.codex.ui.formatPercent

@Composable
fun AccountContent(
    account: AccountOverview,
    loginChallenge: LoginChallenge?,
    pendingActions: Set<String>,
    onApiKeyLogin: () -> Unit,
    onChatGptLogin: () -> Unit,
    onDeviceLogin: () -> Unit,
    onCancelLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onResetLimit: () -> Unit,
    onNudge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loginPending = ServerHubViewModel.ACCOUNT_LOGIN_ACTION_KEY in pendingActions ||
        ServerHubViewModel.DEVICE_LOGIN_ACTION_KEY in pendingActions
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("account-summary") {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AccountCircle, contentDescription = null, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (account.signedIn) account.email ?: "已登录" else "未登录",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                listOfNotNull(
                                    account.type?.let { stringResource(it.displayLabelRes) },
                                    account.planType?.let { stringResource(it.displayLabelRes) },
                                ).joinToString(" · ").ifBlank {
                                    if (account.signedIn) "Codex 账号" else "登录后可查看计划和额度"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusBadge(
                            if (account.signedIn) "已登录" else "未登录",
                            if (account.signedIn) StatusTone.POSITIVE else StatusTone.NEUTRAL,
                        )
                    }
                    if (account.signedIn) {
                        OutlinedButton(
                            onClick = onLogout,
                            enabled = ServerHubViewModel.ACCOUNT_LOGOUT_ACTION_KEY !in pendingActions,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("退出登录")
                        }
                    } else {
                        Button(onClick = onChatGptLogin, enabled = !loginPending, modifier = Modifier.fillMaxWidth()) {
                            Text("使用 ChatGPT 登录")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onDeviceLogin, enabled = !loginPending, modifier = Modifier.weight(1f)) {
                                Text("设备码")
                            }
                            OutlinedButton(onClick = onApiKeyLogin, enabled = !loginPending, modifier = Modifier.weight(1f)) {
                                Text("API Key")
                            }
                        }
                    }
                }
            }
        }
        loginChallenge?.let { challenge ->
            item("login-challenge") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("完成登录", style = MaterialTheme.typography.titleMedium)
                        challenge.userCode?.let { Text("设备码  $it", style = MaterialTheme.typography.titleLarge) }
                        challenge.url?.let { url ->
                            Button(onClick = { onOpenUrl(url) }, modifier = Modifier.fillMaxWidth()) {
                                Text("打开登录页面")
                            }
                        }
                        if (challenge.loginId != null) {
                            TextButton(
                                onClick = onCancelLogin,
                                enabled = ServerHubViewModel.CANCEL_LOGIN_ACTION_KEY !in pendingActions,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("取消登录")
                            }
                        }
                    }
                }
            }
        }
        account.workspaceMessages.forEach { message ->
            item("message:${message.id}") {
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(message.type.displayLabelRes), style = MaterialTheme.typography.labelMedium)
                        Text(message.body)
                        message.createdAt?.let {
                            Text(
                                relativeTime(it),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        account.rateLimits?.let { rateLimits ->
            item("rate-limits") { AccountRateLimitsCard(rateLimits) }
        }
        account.usage?.let { usage ->
            item("usage") { AccountUsageCard(usage) }
        }
        if (account.signedIn) {
            item("credit-actions") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("额度操作", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onResetLimit,
                            enabled = ServerHubViewModel.RATE_LIMIT_ACTION_KEY !in pendingActions,
                        ) {
                            Text("使用重置额度")
                        }
                        OutlinedButton(
                            onClick = onNudge,
                            enabled = ServerHubViewModel.CREDITS_NUDGE_ACTION_KEY !in pendingActions,
                        ) {
                            Text("请求补充额度")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRateLimitsCard(rateLimits: AccountRateLimits) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("额度", style = MaterialTheme.typography.titleMedium)
            rateLimits.limits.forEachIndexed { index, limit ->
                if (index > 0) HorizontalDivider()
                AccountRateLimitView(limit)
            }
            rateLimits.availableResetCredits?.let {
                MetricValue("可用重置次数", formatCount(it))
            }
            ExpandableJsonSection("额度技术详情", rateLimits.technicalDetails)
        }
    }
}

@Composable
private fun AccountRateLimitView(limit: AccountRateLimit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(limit.name ?: limit.id ?: "Codex", style = MaterialTheme.typography.labelLarge)
        limit.primary?.let { RateLimitWindowView("主要额度", it) }
        limit.secondary?.let { RateLimitWindowView("补充额度", it) }
        limit.credits?.let { credits ->
            val value = when {
                credits.unlimited -> "不限量"
                credits.hasCredits -> credits.balance ?: "可用"
                else -> "无可用额度"
            }
            MetricValue("额外额度", value)
        }
    }
}

@Composable
private fun RateLimitWindowView(label: String, window: RateLimitWindow) {
    val duration = window.windowDurationMinutes?.let { formatDurationSeconds(it * 60) }
    val reset = window.resetsAt?.let(::formatDateTime)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                listOfNotNull(label, duration).joinToString(" · "),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            Text("已使用 ${formatPercent(window.usedPercent)}", style = MaterialTheme.typography.labelMedium)
        }
        LinearProgressIndicator(
            progress = { (window.usedPercent / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        reset?.let {
            Text("$it 重置", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccountUsageCard(usage: AccountUsage) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("用量概览", style = MaterialTheme.typography.titleMedium)
            usage.lifetimeTokens?.let { MetricValue("累计 Token", formatCount(it)) }
            usage.peakDailyTokens?.let { MetricValue("单日最高 Token", formatCount(it)) }
            usage.longestRunningTurnSeconds?.let { MetricValue("最长任务", formatDurationSeconds(it)) }
            usage.currentStreakDays?.let { MetricValue("当前连续使用", "$it 天") }
            usage.longestStreakDays?.let { MetricValue("最长连续使用", "$it 天") }
            ExpandableJsonSection("用量技术详情", usage.technicalDetails)
        }
    }
}

@Composable
private fun MetricValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ConfigurationContent(
    configuration: ServerConfiguration,
    projectScoped: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("config-warning") {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Code, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (projectScoped) "项目配置视图" else "开发者选项", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (projectScoped) {
                                "这里展示项目作用域最终生效的配置、管理要求和来源层"
                            } else {
                                "这里展示服务端的生效配置、管理要求和来源层，写入操作会直接修改服务端配置"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        item("effective-config") { ExpandableJsonSection("生效配置", configuration.configJson) }
        configuration.requirementsJson?.let { json ->
            item("requirements") { ExpandableJsonSection("管理要求", json) }
        }
        configuration.layersJson?.let { json ->
            item("layers") { ExpandableJsonSection("配置来源层", json) }
        }
    }
}

@Composable
fun ExpandableJsonSection(title: String, json: String) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            ListItem(
                headlineContent = { Text(title) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (expanded) R.string.common_hide_technical_details
                            else R.string.common_show_technical_details,
                        ),
                    )
                },
                leadingContent = { Icon(Icons.Rounded.Code, contentDescription = null) },
                modifier = Modifier.clickable { expanded = !expanded },
            )
            if (expanded) {
                HorizontalDivider()
                Box(Modifier.padding(12.dp)) { JsonBlock(json) }
            }
        }
    }
}
