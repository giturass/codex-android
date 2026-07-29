package com.hebo.codex.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class InputResourcesTest {
    @Test
    fun onlyUsableResourcesAreOfferedToComposer() {
        val enabledSkill = entry(FeatureKind.SKILL, "skill-enabled", enabled = true)
        val disabledSkill = entry(FeatureKind.SKILL, "skill-disabled", enabled = false)
        val accessibleApp = entry(
            FeatureKind.APP,
            "app-accessible",
            enabled = true,
            metadata = mapOf("accessible" to "true"),
        )
        val inaccessibleApp = entry(
            FeatureKind.APP,
            "app-inaccessible",
            enabled = true,
            metadata = mapOf("accessible" to "false"),
        )
        val installedPlugin = entry(FeatureKind.PLUGIN, "plugin-installed", enabled = true, installed = true)
        val availablePlugin = entry(FeatureKind.PLUGIN, "plugin-available", enabled = true, installed = false)

        assertEquals(
            listOf(enabledSkill, accessibleApp, installedPlugin),
            availableInputResources(
                skills = listOf(enabledSkill, disabledSkill),
                apps = listOf(accessibleApp, inaccessibleApp),
                plugins = listOf(installedPlugin, availablePlugin),
            ),
        )
    }

    private fun entry(
        kind: FeatureKind,
        id: String,
        enabled: Boolean?,
        installed: Boolean? = null,
        metadata: Map<String, String> = emptyMap(),
    ) = FeatureEntry(
        kind = kind,
        id = id,
        title = id,
        description = "",
        enabled = enabled,
        installed = installed,
        metadata = metadata,
        rawJson = "{}",
    )
}
