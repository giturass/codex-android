package com.hebo.codex.domain

import com.hebo.codex.util.runSuspendCatching
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class InputResourceLoadResult(
    val entries: List<FeatureEntry>,
    val failure: Throwable?,
)

suspend fun loadInputResources(
    repository: CodexRepository,
    serverId: String,
    cwd: String?,
    threadId: String? = null,
    forceRefresh: Boolean = false,
): InputResourceLoadResult = coroutineScope {
    val catalogs = listOf(FeatureArea.SKILLS, FeatureArea.APPS, FeatureArea.PLUGINS)
        .associateWith { area ->
            async {
                runSuspendCatching {
                    repository.featureCatalog(
                        serverId = serverId,
                        area = area,
                        cwd = cwd,
                        threadId = threadId,
                        forceRefresh = forceRefresh,
                    )
                }
            }
        }
        .mapValues { (_, request) -> request.await() }
    InputResourceLoadResult(
        entries = availableInputResources(
            skills = catalogs.getValue(FeatureArea.SKILLS).getOrNull()?.entries.orEmpty(),
            apps = catalogs.getValue(FeatureArea.APPS).getOrNull()?.entries.orEmpty(),
            plugins = catalogs.getValue(FeatureArea.PLUGINS).getOrNull()?.entries.orEmpty(),
        ),
        failure = catalogs.values.firstNotNullOfOrNull { it.exceptionOrNull() },
    )
}

internal fun availableInputResources(
    skills: List<FeatureEntry>,
    apps: List<FeatureEntry>,
    plugins: List<FeatureEntry>,
): List<FeatureEntry> =
    skills.filter { entry -> entry.enabled != false } +
        apps.filter { entry -> entry.enabled != false && entry.metadata["accessible"] == "true" } +
        plugins.filter { entry -> entry.installed == true && entry.enabled != false }
