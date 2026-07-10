package com.hebo.codex.ui.screens.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerHubNavigationTest {
    @Test
    fun projectScopeOnlyContainsCwdAwareSections() {
        val sections = hubSectionGroups(projectScoped = true).flatMap(HubSectionGroup::sections)

        assertEquals(
            listOf(
                HubSection.PERMISSIONS,
                HubSection.SKILLS,
                HubSection.PLUGINS,
                HubSection.HOOKS,
                HubSection.CONFIG,
            ),
            sections,
        )
        assertFalse(HubSection.ACCOUNT in sections)
        assertFalse(HubSection.MODELS in sections)
        assertFalse(HubSection.APPS in sections)
        assertFalse(HubSection.MCP in sections)
    }

    @Test
    fun serverScopeGroupsEveryTopLevelSectionOnce() {
        val sections = hubSectionGroups(projectScoped = false).flatMap(HubSectionGroup::sections)

        assertEquals(sections.size, sections.distinct().size)
        assertTrue(HubSection.ACCOUNT in sections)
        assertTrue(HubSection.PLUGINS in sections)
        assertTrue(HubSection.MCP in sections)
        assertFalse(HubSection.SHARED_PLUGINS in sections)
    }
}
