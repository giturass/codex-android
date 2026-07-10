package com.hebo.codex.ui.screens.hub

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Webhook
import androidx.compose.ui.graphics.vector.ImageVector

data class HubSectionGroup(
    val title: String,
    val sections: List<HubSection>,
)

val HubSection.title: String
    get() = when (this) {
        HubSection.MODELS -> "可用模型"
        HubSection.PERMISSIONS -> "权限策略"
        HubSection.SKILLS -> "Skills"
        HubSection.HOOKS -> "Hooks"
        HubSection.APPS -> "Apps"
        HubSection.PLUGINS -> "Plugins"
        HubSection.MCP -> "MCP"
        HubSection.SHARED_PLUGINS -> "共享插件"
        HubSection.ACCOUNT -> "Codex 账号"
        HubSection.CONFIG -> "配置与诊断"
    }

val HubSection.description: String
    get() = when (this) {
        HubSection.MODELS -> "查看服务器提供的模型和推理能力"
        HubSection.PERMISSIONS -> "查看当前作用域允许的运行配置"
        HubSection.SKILLS -> "管理可复用工作流和来源目录"
        HubSection.HOOKS -> "查看生命周期钩子及其可信状态"
        HubSection.APPS -> "管理应用可用性和外部授权"
        HubSection.PLUGINS -> "浏览、安装和共享插件"
        HubSection.MCP -> "查看第三方服务器、工具和资源"
        HubSection.SHARED_PLUGINS -> "管理已共享的插件"
        HubSection.ACCOUNT -> "登录状态、计划、用量和额度"
        HubSection.CONFIG -> "查看配置层并执行高级配置操作"
    }

val HubSection.icon: ImageVector
    get() = when (this) {
        HubSection.MODELS -> Icons.Rounded.SmartToy
        HubSection.PERMISSIONS -> Icons.Rounded.Policy
        HubSection.SKILLS -> Icons.Rounded.AutoAwesome
        HubSection.HOOKS -> Icons.Rounded.Webhook
        HubSection.APPS -> Icons.Rounded.Apps
        HubSection.PLUGINS, HubSection.SHARED_PLUGINS -> Icons.Rounded.Extension
        HubSection.MCP -> Icons.Rounded.Hub
        HubSection.ACCOUNT -> Icons.Rounded.AccountCircle
        HubSection.CONFIG -> Icons.Rounded.Code
    }

fun hubSectionGroups(projectScoped: Boolean): List<HubSectionGroup> = if (projectScoped) {
    listOf(
        HubSectionGroup(
            title = "项目能力",
            sections = listOf(
                HubSection.PERMISSIONS,
                HubSection.SKILLS,
                HubSection.PLUGINS,
                HubSection.HOOKS,
                HubSection.CONFIG,
            ),
        ),
    )
} else {
    listOf(
        HubSectionGroup(
            title = "连接与账户",
            sections = listOf(HubSection.ACCOUNT, HubSection.MODELS),
        ),
        HubSectionGroup(
            title = "功能与扩展",
            sections = listOf(HubSection.SKILLS, HubSection.PLUGINS, HubSection.APPS, HubSection.MCP),
        ),
        HubSectionGroup(
            title = "策略与高级",
            sections = listOf(HubSection.PERMISSIONS, HubSection.HOOKS, HubSection.CONFIG),
        ),
    )
}
