package com.hebo.codex.ui

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewTaskRouteTest {
    @Test
    fun projectContextSurvivesSerialization() {
        val route = NewTaskRoute(
            draftId = "draft-1",
            serverId = "server-1",
            cwd = "/home/user/Idea Projects/codex-android",
        )

        val restored = Json.decodeFromString<NewTaskRoute>(Json.encodeToString(route))

        assertEquals(route, restored)
    }

    @Test
    fun destinationMatchesTypedRoutePattern() {
        val routeName = NewTaskRoute::class.qualifiedName

        assertTrue(isNewTaskDestination(routeName))
        assertTrue(isNewTaskDestination("$routeName/{draftId}?serverId={serverId}&cwd={cwd}"))
        assertFalse(isNewTaskDestination("com.hebo.codex.ui.ChatRoute/{serverId}/{threadId}"))
    }
}
