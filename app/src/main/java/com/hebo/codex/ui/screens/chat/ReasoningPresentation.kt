package com.hebo.codex.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hebo.codex.R
import com.hebo.codex.domain.ConversationItem

@Composable
internal fun ReasoningItemDetails(
    item: ConversationItem.Reasoning,
    cwd: String,
    onOpenRemoteFile: (RemoteFileTarget) -> Unit,
    onLinkError: (String) -> Unit,
) {
    val summary = reasoningSummaryBody(item.summaryParts)
    val rawContent = reasoningRawContent(item.rawContentParts)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (summary.isNotBlank()) {
            Text(
                stringResource(R.string.reasoning_summary_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectionContainer {
                ChatMarkdown(
                    content = summary,
                    cwd = cwd,
                    onOpenRemoteFile = onOpenRemoteFile,
                    onLinkError = onLinkError,
                )
            }
        }
        if (rawContent.isNotBlank()) {
            ExpandableRawReasoning(
                turnId = item.turnId,
                itemId = item.id,
                content = rawContent,
            )
        }
    }
}

@Composable
private fun ExpandableRawReasoning(turnId: String, itemId: String, content: String) {
    var expanded by rememberSaveable(turnId, itemId) { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = !expanded }) {
            Text(
                stringResource(
                    if (expanded) R.string.reasoning_raw_hide else R.string.reasoning_raw_show,
                ),
            )
        }
        if (expanded) {
            SelectionContainer {
                Text(
                    text = content,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.inverseSurface,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(10.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

internal fun reasoningSummaryTitle(parts: List<String>): String? = parts
    .asReversed()
    .asSequence()
    .mapNotNull(::reasoningPartTitle)
    .firstOrNull()

internal fun reasoningSummaryBody(parts: List<String>): String {
    val content = parts
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filterNot { part -> part.reasoningBodyAfterHeader() == REASONING_EMPTY_PLACEHOLDER }
        .joinToString("\n\n")
    if (content.isEmpty()) return ""

    val headerEnd = content.leadingBoldHeaderEnd() ?: return content
    val body = content.substring(headerEnd)
    return when {
        body.isBlank() -> ""
        body.startsWith('\n') || body.startsWith('\r') -> body.trimStart('\n', '\r')
        else -> content
    }
}

internal fun reasoningRawContent(parts: List<String>): String = parts
    .map(String::trim)
    .filter(String::isNotEmpty)
    .joinToString("\n\n")

private fun reasoningPartTitle(part: String): String? {
    val content = part.trim()
    if (content.isEmpty()) return null
    content.leadingBoldHeaderEnd()?.let { headerEnd ->
        return content.substring(2, headerEnd - 2).trim().takeIf(String::isNotEmpty)
    }
    return content
        .lineSequence()
        .firstOrNull { line -> line.isNotBlank() && line.trim() != REASONING_EMPTY_PLACEHOLDER }
        ?.trim()
        ?.trimStart('#')
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}

private fun String.reasoningBodyAfterHeader(): String {
    val headerEnd = leadingBoldHeaderEnd() ?: return this
    return substring(headerEnd).trim()
}

private fun String.leadingBoldHeaderEnd(): Int? {
    if (!startsWith("**")) return null
    val afterOpen = substring(2)
    val close = afterOpen.indexOf("**")
    return if (close > 0) 2 + close + 2 else null
}

private const val REASONING_EMPTY_PLACEHOLDER = "<!-- -->"
