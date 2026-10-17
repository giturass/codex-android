package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.ApprovalDecision
import com.hebo.codex.domain.ApprovalOption
import com.hebo.codex.domain.ApprovalType
import com.hebo.codex.domain.NetworkApprovalContext
import com.hebo.codex.domain.NetworkApprovalProtocol
import com.hebo.codex.domain.NetworkPolicyRuleAction
import com.hebo.codex.domain.PendingServerRequest

internal val PendingServerRequest.Approval.title: String
    get() = when (type) {
        ApprovalType.COMMAND_EXECUTION -> "命令等待确认"
        ApprovalType.TERMINAL_INPUT -> "终端输入等待确认"
        ApprovalType.NETWORK_ACCESS -> "网络访问等待确认"
        ApprovalType.FILE_CHANGE -> "文件修改等待确认"
    }

internal val PendingServerRequest.Approval.summary: String
    get() = networkContext?.description ?: reason ?: commandPreview ?: cwd ?: "查看详情后决定"

internal val PendingServerRequest.Approval.commandPreview: String?
    get() = command?.takeIf { type != ApprovalType.NETWORK_ACCESS && it.isNotBlank() }

internal val NetworkApprovalContext.description: String
    get() = "$host · ${protocol.label}"

private val NetworkApprovalProtocol.label: String
    get() = when (this) {
        NetworkApprovalProtocol.HTTP -> "HTTP"
        NetworkApprovalProtocol.HTTPS -> "HTTPS"
        NetworkApprovalProtocol.SOCKS5_TCP -> "SOCKS5 / TCP"
        NetworkApprovalProtocol.SOCKS5_UDP -> "SOCKS5 / UDP"
    }

internal val ApprovalOption.buttonLabel: String
    get() = when (this) {
        is ApprovalOption.Decision -> when (decision) {
            ApprovalDecision.ACCEPT -> "允许一次"
            ApprovalDecision.ACCEPT_FOR_SESSION -> "本会话允许"
            ApprovalDecision.DECLINE -> "拒绝"
            ApprovalDecision.CANCEL -> "拒绝并停止任务"
        }

        is ApprovalOption.ExecPolicy -> "保存命令规则并允许本次操作"
        is ApprovalOption.NetworkPolicy -> when (amendment.action) {
            NetworkPolicyRuleAction.ALLOW -> "保存网络规则并允许访问"
            NetworkPolicyRuleAction.DENY -> "保存网络规则并拒绝访问"
        }
    }
