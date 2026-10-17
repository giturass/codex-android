package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.ApprovalOption
import com.hebo.codex.domain.ApprovalType
import com.hebo.codex.domain.NetworkApprovalContext
import com.hebo.codex.domain.NetworkApprovalProtocol
import com.hebo.codex.domain.NetworkPolicyAmendment
import com.hebo.codex.domain.NetworkPolicyRuleAction
import com.hebo.codex.domain.PendingServerRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApprovalPresentationTest {
    @Test
    fun networkApprovalShowsTheTargetAndProtocolWithoutAShellCommandPreview() {
        val approval = approval(ApprovalType.NETWORK_ACCESS).copy(
            command = "internal network approval helper",
            networkContext = NetworkApprovalContext("example.com:443", NetworkApprovalProtocol.HTTPS),
        )

        assertEquals("网络访问等待确认", approval.title)
        assertEquals("example.com:443 · HTTPS", approval.summary)
        assertNull(approval.commandPreview)
    }

    @Test
    fun terminalInputIsDistinguishedFromStartingACommand() {
        val approval = approval(ApprovalType.TERMINAL_INPUT).copy(command = "yes\n")

        assertEquals("终端输入等待确认", approval.title)
        assertEquals("yes\n", approval.commandPreview)
    }

    @Test
    fun deniedNetworkRulesArePresentedAsDenyingAccess() {
        val option = ApprovalOption.NetworkPolicy(
            NetworkPolicyAmendment("blocked.example", NetworkPolicyRuleAction.DENY, "{}"),
        )

        assertEquals("保存网络规则并拒绝访问", option.buttonLabel)
    }

    private fun approval(type: ApprovalType) = PendingServerRequest.Approval(
        requestKey = "server:1",
        itemId = "item",
        turnId = "turn",
        type = type,
        reason = null,
        command = null,
        cwd = null,
        environmentId = null,
        networkContext = null,
        commandActions = emptyList(),
        grantRoot = null,
        availableDecisions = emptyList(),
    )
}
