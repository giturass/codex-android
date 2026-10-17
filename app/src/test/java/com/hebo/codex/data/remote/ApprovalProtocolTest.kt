package com.hebo.codex.data.remote

import com.hebo.codex.domain.ApprovalDecision
import com.hebo.codex.domain.ApprovalOption
import com.hebo.codex.domain.ApprovalType
import com.hebo.codex.domain.NetworkApprovalContext
import com.hebo.codex.domain.NetworkApprovalProtocol
import com.hebo.codex.domain.NetworkPolicyRuleAction
import com.hebo.codex.domain.PendingServerRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalProtocolTest {
    @Test
    fun serverDecisionsKeepTheirOrderWithoutAddingOtherApprovalChoices() {
        val approval = approval(
            """{
              "availableDecisions":["cancel","accept"],
              "proposedExecpolicyAmendment":["git","status"],
              "proposedNetworkPolicyAmendments":[{"host":"example.com","action":"allow"}]
            }""",
        )

        assertEquals(
            listOf(
                ApprovalOption.Decision(ApprovalDecision.CANCEL),
                ApprovalOption.Decision(ApprovalDecision.ACCEPT),
            ),
            approval.availableDecisions,
        )
    }

    @Test
    fun structuredDecisionsUseTheirOwnAmendmentsInsteadOfSeparateProposals() {
        val approval = approval(
            """{
              "availableDecisions":[
                {"applyNetworkPolicyAmendment":{"network_policy_amendment":{"host":"blocked.example","action":"deny"}}},
                {"acceptWithExecpolicyAmendment":{"execpolicy_amendment":["git","diff"]}}
              ],
              "proposedExecpolicyAmendment":["git","status"]
            }""",
        )
        val network = (approval.availableDecisions[0] as ApprovalOption.NetworkPolicy).amendment
        val command = (approval.availableDecisions[1] as ApprovalOption.ExecPolicy).amendment

        assertEquals("blocked.example", network.host)
        assertEquals(NetworkPolicyRuleAction.DENY, network.action)
        assertEquals(
            Json.parseToJsonElement("""{"host":"blocked.example","action":"deny"}"""),
            Json.parseToJsonElement(network.responseJson),
        )
        assertEquals(listOf("git", "diff"), command.command)
        assertEquals(Json.parseToJsonElement("""["git","diff"]"""), Json.parseToJsonElement(command.responseJson))
    }

    @Test
    fun explicitEmptyDecisionsDoNotExposeDefaultChoices() {
        assertTrue(approval("""{"availableDecisions":[]}""").availableDecisions.isEmpty())
    }

    @Test
    fun omittedOrNullDecisionListsUseTheProtocolStandardChoices() {
        listOf("{}", """{"availableDecisions":null}""").forEach { fields ->
            val approval = approval(fields)

            assertEquals(ApprovalType.COMMAND_EXECUTION, approval.type)
            assertEquals(
                ApprovalDecision.entries.map { ApprovalOption.Decision(it) },
                approval.availableDecisions,
            )
        }
    }

    @Test
    fun defaultChoicesIncludeTheOfferedPolicyAmendments() {
        val approval = approval(
            """{
              "proposedExecpolicyAmendment":["git","status"],
              "proposedNetworkPolicyAmendments":[{"host":"example.com","action":"allow"}]
            }""",
        )

        assertEquals(6, approval.availableDecisions.size)
        assertEquals(
            listOf("git", "status"),
            (approval.availableDecisions[2] as ApprovalOption.ExecPolicy).amendment.command,
        )
        assertEquals(
            "example.com",
            (approval.availableDecisions[3] as ApprovalOption.NetworkPolicy).amendment.host,
        )
    }

    @Test
    fun malformedOrUnknownDecisionListsAreRejected() {
        listOf(
            """{"availableDecisions":"accept"}""",
            """{"availableDecisions":["allow"]}""",
            """{"availableDecisions":[{}]}""",
            """{"availableDecisions":[{"acceptWithExecpolicyAmendment":{}}]}""",
        ).forEach { fields ->
            assertThrows(ProtocolException::class.java) { approval(fields) }
        }
    }

    @Test
    fun terminalInputApprovalUsesTheExistingItemWithoutLosingItsRequestIdentity() {
        val first = approval("""{"kind":"writeStdin","approvalId":"stdin-1"}""", "server:1")
        val second = approval("""{"kind":"writeStdin","approvalId":"stdin-2"}""", "server:2")

        assertEquals(ApprovalType.TERMINAL_INPUT, first.type)
        assertEquals(first.itemId, second.itemId)
        assertEquals("server:1", first.requestKey)
        assertEquals("server:2", second.requestKey)
    }

    @Test
    fun networkApprovalKeepsTheDestinationAndTransportProtocol() {
        val approval = approval(
            """{
              "command":"network access helper",
              "networkApprovalContext":{"host":"example.com:8443","protocol":"https"}
            }""",
        )

        assertEquals(ApprovalType.NETWORK_ACCESS, approval.type)
        assertEquals(
            NetworkApprovalContext("example.com:8443", NetworkApprovalProtocol.HTTPS),
            approval.networkContext,
        )
    }

    @Test
    fun fileChangesUseTheFourFileApprovalDecisions() {
        val approval = ProtocolMapper.pendingServerRequest(
            method = "item/fileChange/requestApproval",
            params = Json.parseToJsonElement(
                """{"threadId":"thread","turnId":"turn","itemId":"patch","startedAtMs":1}""",
            ).jsonObject,
            requestKey = "server:1",
        ) as PendingServerRequest.Approval

        assertEquals(ApprovalType.FILE_CHANGE, approval.type)
        assertEquals(ApprovalDecision.entries.map { ApprovalOption.Decision(it) }, approval.availableDecisions)
    }

    private fun approval(fields: String, requestKey: String = "server:1"): PendingServerRequest.Approval {
        val required = Json.parseToJsonElement(
            """{"threadId":"thread","turnId":"turn","itemId":"command","startedAtMs":1}""",
        ).jsonObject
        return ProtocolMapper.pendingServerRequest(
            method = "item/commandExecution/requestApproval",
            params = kotlinx.serialization.json.JsonObject(required + Json.parseToJsonElement(fields).jsonObject),
            requestKey = requestKey,
        ) as PendingServerRequest.Approval
    }
}
