package com.hebo.codex.ui.screens.chat

import androidx.compose.ui.text.buildAnnotatedString
import com.mikepenz.markdown.annotator.AnnotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.utils.MARKDOWN_TAG_IMAGE_URL
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode

internal data class ChatMarkdownImageLink(val link: String, val alt: String?)

internal fun resolveChatMarkdownImageLink(
    content: String,
    node: ASTNode,
    settings: AnnotatorSettings,
): ChatMarkdownImageLink? {
    val annotated = buildAnnotatedString {
        buildMarkdownAnnotatedString(content, children = listOf(node), annotatorSettings = settings)
    }
    val imageTagPrefix = "${MARKDOWN_TAG_IMAGE_URL}_"
    val link = annotated.getStringAnnotations(0, annotated.length)
        .firstOrNull { it.item.startsWith(imageTagPrefix) }
        ?.item?.removePrefix(imageTagPrefix)
        ?: return null
    val alt = listOf(MarkdownElementTypes.LINK_TEXT, MarkdownElementTypes.LINK_LABEL).firstNotNullOfOrNull { type ->
        node.descendants().firstOrNull { it.type == type }
            ?.getUnescapedTextInNode(content)?.trim('[', ']')?.trim()?.takeIf(String::isNotEmpty)
    }
    return ChatMarkdownImageLink(link, alt)
}

private fun ASTNode.descendants(): Sequence<ASTNode> =
    children.asSequence().flatMap { sequenceOf(it) + it.descendants() }
