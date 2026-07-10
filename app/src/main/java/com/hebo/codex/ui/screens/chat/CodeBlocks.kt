package com.hebo.codex.ui.screens.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.ChangedFile
import com.hebo.codex.domain.FileChangeKind
import com.hebo.codex.ui.theme.statusColors
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ShellCommandBlock(command: String) {
    val darkTheme = isSystemInDarkTheme()
    val highlightedCommand by produceState(
        initialValue = AnnotatedString(command),
        key1 = command,
        key2 = darkTheme,
    ) {
        value = withContext(Dispatchers.Default) {
            buildShellHighlightedText(command, darkTheme)
        }
    }
    CodeTextSurface(highlightedCommand)
}

@Composable
internal fun TerminalOutputBlock(output: String) {
    val text = remember(output) { AnnotatedString(output) }
    CodeTextSurface(text)
}

@Composable
internal fun FileDiffBlock(change: ChangedFile) {
    val additionColor = MaterialTheme.statusColors.success
    val deletionColor = MaterialTheme.colorScheme.error
    val hunkColor = MaterialTheme.colorScheme.primary
    val metadataColor = MaterialTheme.colorScheme.onSurfaceVariant
    val text = remember(
        change.kind,
        change.diff,
        additionColor,
        deletionColor,
        hunkColor,
        metadataColor,
    ) {
        buildDiffAnnotatedString(
            change = change,
            additionColor = additionColor,
            deletionColor = deletionColor,
            hunkColor = hunkColor,
            metadataColor = metadataColor,
        )
    }
    CodeTextSurface(text)
}

@Composable
private fun CodeTextSurface(text: AnnotatedString) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        SelectionContainer {
            Text(
                text = text,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                softWrap = false,
            )
        }
    }
}

internal fun buildShellHighlightedText(command: String, darkTheme: Boolean): AnnotatedString {
    val highlights = Highlights.Builder()
        .code(command)
        .language(SyntaxLanguage.SHELL)
        .theme(SyntaxThemes.default(darkMode = darkTheme))
        .build()
        .getHighlights()
    return buildAnnotatedString {
        append(command)
        highlights.forEach { highlight ->
            val style = when (highlight) {
                is ColorHighlight -> SpanStyle(color = Color(highlight.rgb).copy(alpha = 1f))
                is BoldHighlight -> SpanStyle(fontWeight = FontWeight.Bold)
            }
            addStyle(
                style = style,
                start = highlight.location.start,
                end = highlight.location.end,
            )
        }
    }
}

private fun buildDiffAnnotatedString(
    change: ChangedFile,
    additionColor: Color,
    deletionColor: Color,
    hunkColor: Color,
    metadataColor: Color,
): AnnotatedString = buildAnnotatedString {
    var lineStart = 0
    while (lineStart < change.diff.length) {
        val newlineIndex = change.diff.indexOf('\n', startIndex = lineStart)
        val lineEnd = if (newlineIndex == -1) change.diff.length else newlineIndex
        val segmentEnd = if (newlineIndex == -1) change.diff.length else newlineIndex + 1
        val line = change.diff.substring(lineStart, lineEnd)
        val spanStart = length
        append(change.diff.substring(lineStart, segmentEnd))
        val color = when (classifyDiffLine(change.kind, line)) {
            DiffLineKind.ADDITION -> additionColor
            DiffLineKind.DELETION -> deletionColor
            DiffLineKind.HUNK -> hunkColor
            DiffLineKind.METADATA -> metadataColor
            DiffLineKind.CONTEXT -> null
        }
        if (color != null) {
            addStyle(SpanStyle(color = color), spanStart, length)
        }
        lineStart = segmentEnd
    }
}

internal fun classifyDiffLine(kind: FileChangeKind, line: String): DiffLineKind = when (kind) {
    FileChangeKind.Add -> DiffLineKind.ADDITION
    FileChangeKind.Delete -> DiffLineKind.DELETION
    is FileChangeKind.Update -> when {
        line.startsWith("@@") -> DiffLineKind.HUNK
        line.startsWith("diff --git ") ||
            line.startsWith("index ") ||
            line.startsWith("+++") ||
            line.startsWith("---") ||
            line.startsWith("Moved to: ") -> DiffLineKind.METADATA
        line.startsWith('+') -> DiffLineKind.ADDITION
        line.startsWith('-') -> DiffLineKind.DELETION
        else -> DiffLineKind.CONTEXT
    }
}

internal enum class DiffLineKind {
    ADDITION,
    DELETION,
    HUNK,
    METADATA,
    CONTEXT,
}
