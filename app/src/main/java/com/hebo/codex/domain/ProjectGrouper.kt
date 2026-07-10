package com.hebo.codex.domain

fun groupRecentProjects(threads: List<ThreadSummary>): List<RecentProject> =
    threads
        .groupBy(ThreadSummary::cwd)
        .map { (path, projectThreads) ->
            RecentProject(
                path = path,
                name = projectName(path),
                lastActiveAt = projectThreads.maxOf(ThreadSummary::effectiveRecency),
                threads = projectThreads.sortedByDescending(ThreadSummary::effectiveRecency),
            )
        }
        .sortedByDescending(RecentProject::lastActiveAt)

fun projectName(path: String): String {
    val trimmed = path.trimEnd('/', '\\')
    if (trimmed.isEmpty()) return path
    val separatorIndex = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
    return trimmed.substring(separatorIndex + 1)
}

fun ThreadSummary.effectiveRecency(): Long = recencyAt ?: updatedAt

