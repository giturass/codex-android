package com.hebo.codex.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StableRpcMethodTest {
    @Test
    fun stableWhitelistHasNoDuplicateOrExcludedMethods() {
        val methods = StableRpcMethod.entries.map(StableRpcMethod::wireName)

        assertEquals(methods.size, methods.distinct().size)
        assertFalse(methods.any { it.startsWith("thread/realtime/") })
        assertFalse(methods.any { it.startsWith("process/") })
        assertFalse(methods.any { it.startsWith("fs/") })
        assertFalse(methods.any { it.startsWith("command/") })
        assertFalse(methods.any { it.startsWith("windows") })
        assertFalse(methods.any { it.startsWith("remoteControl/") })
        assertFalse(methods.any { it.startsWith("experimentalFeature/") })
        assertFalse(methods.any { it.startsWith("externalAgentConfig/") })
    }

    @Test
    fun stableWhitelistContainsUserFacingCapabilityGroups() {
        val methods = StableRpcMethod.entries.map(StableRpcMethod::wireName).toSet()
        val expected = setOf(
            "thread/fork",
            "thread/archive",
            "thread/delete",
            "thread/name/set",
            "thread/goal/set",
            "thread/unarchive",
            "thread/compact/start",
            "review/start",
            "model/list",
            "modelProvider/capabilities/read",
            "permissionProfile/list",
            "skills/list",
            "hooks/list",
            "app/list",
            "plugin/list",
            "plugin/installed",
            "plugin/share/updateTargets",
            "mcpServerStatus/list",
            "account/read",
            "feedback/upload",
            "config/read",
            "config/batchWrite",
        )

        assertTrue(methods.containsAll(expected))
    }
}
