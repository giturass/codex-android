package com.hebo.codex.ui.screens.chat

internal data class TurnDiffFile(
    val path: String,
    val previousPath: String?,
    val kind: TurnDiffKind,
    val additions: Int,
    val deletions: Int,
    val diff: String,
)

internal enum class TurnDiffKind { ADD, UPDATE, DELETE, RENAME }

internal data class ParsedTurnDiff(val files: List<TurnDiffFile>, val incomplete: Boolean)

internal fun parseTurnDiff(diff: String): ParsedTurnDiff {
    if (diff.isBlank()) return ParsedTurnDiff(emptyList(), incomplete = false)

    val boundaries = TURN_DIFF_BOUNDARY.findAll(diff).map { it.range.first }.toList()
    var incomplete = boundaries.isEmpty() || diff.substring(0, boundaries.first()).isNotBlank()
    val files = boundaries.mapIndexedNotNull { index, start ->
        val end = boundaries.getOrNull(index + 1) ?: diff.length
        parseTurnDiffFile(diff.substring(start, end)).also { if (it == null) incomplete = true }
    }
    return ParsedTurnDiff(files, incomplete)
}

private fun parseTurnDiffFile(diff: String): TurnDiffFile? {
    val lines = diff.split('\n')
    val firstHunk = lines.indexOfFirst { TURN_DIFF_HUNK.containsMatchIn(it) }
    val headers = if (firstHunk < 0) lines else lines.take(firstHunk)
    val oldHeader = headers.firstOrNull { it.startsWith("--- ") }?.removePrefix("--- ")
    val newHeader = headers.firstOrNull { it.startsWith("+++ ") }?.removePrefix("+++ ")
    val oldPath: String?
    val newPath: String?

    if (oldHeader != null && newHeader != null && firstHunk >= 0) {
        if ((oldHeader != "/dev/null" && !oldHeader.startsWith("a/")) ||
            (newHeader != "/dev/null" && !newHeader.startsWith("b/"))
        ) return null
        oldPath = oldHeader.takeUnless { it == "/dev/null" }?.removePrefix("a/")
        newPath = newHeader.takeUnless { it == "/dev/null" }?.removePrefix("b/")
    } else if (oldHeader == null && newHeader == null && firstHunk < 0) {
        // Empty additions/deletions have no hunks; the backreference preserves spaces in paths
        val path = EMPTY_FILE_DIFF_HEADER.matchEntire(lines.first())?.groupValues?.get(1) ?: return null
        val added = headers.any { it.startsWith("new file mode ") }
        val deleted = headers.any { it.startsWith("deleted file mode ") }
        if (added == deleted || headers.none { it.startsWith("index ") }) return null
        oldPath = path.takeUnless { added }
        newPath = path.takeUnless { deleted }
    } else {
        return null
    }

    val path = newPath ?: oldPath ?: return null
    if (path.isEmpty() || oldPath == "" || newPath == "" ||
        lines.first() != "diff --git a/${oldPath ?: newPath} b/${newPath ?: oldPath}"
    ) return null

    var additions = 0
    var deletions = 0
    if (firstHunk >= 0) {
        for (index in firstHunk until lines.size) {
            if (lines[index].startsWith('+')) additions += 1
            if (lines[index].startsWith('-')) deletions += 1
        }
    }
    val renamed = oldPath != null && newPath != null && oldPath != newPath
    return TurnDiffFile(
        path = path,
        previousPath = oldPath.takeIf { renamed },
        kind = when {
            oldPath == null -> TurnDiffKind.ADD
            newPath == null -> TurnDiffKind.DELETE
            renamed -> TurnDiffKind.RENAME
            else -> TurnDiffKind.UPDATE
        },
        additions = additions,
        deletions = deletions,
        diff = diff,
    )
}

private val TURN_DIFF_BOUNDARY = Regex("^diff --git ", RegexOption.MULTILINE)
private val TURN_DIFF_HUNK = Regex("^@@ -\\d+(?:,\\d+)? \\+\\d+(?:,\\d+)? @@")
private val EMPTY_FILE_DIFF_HEADER = Regex("diff --git a/(.+) b/\\1")
