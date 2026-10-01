package com.hebo.codex.ui.screens.chat

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import com.mikepenz.markdown.annotator.DefaultAnnotatorSettings
import com.mikepenz.markdown.model.ReferenceLinkHandlerImpl
import com.mikepenz.markdown.model.markdownAnnotator
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatMarkdownImageLinkTest {
    @Test
    fun resolvesInlineImageDestinationAndAltThroughParsedMarkdown() {
        assertEquals(
            ChatMarkdownImageLink("images/screen(one).png", "Build screenshot"),
            resolve("Before ![Build screenshot](images/screen\\(one\\).png) after"),
        )
    }

    @Test
    fun resolvesFullReferenceImagesUsingTheRendererReferenceHandler() {
        assertEquals(
            ChatMarkdownImageLink("https://example.com/build.png", "Build screenshot"),
            resolve(
                "![Build screenshot][Shot]\n\n[shot]: https://example.com/build.png",
                "[shot]" to "https://example.com/build.png",
            ),
        )
    }

    @Test
    fun resolvesShortReferenceImageAndUsesItsLabelAsAlt() {
        assertEquals(
            ChatMarkdownImageLink("/workspace/build.png", "Screenshot"),
            resolve("![Screenshot]\n\n[Screenshot]: /workspace/build.png", "[Screenshot]" to "/workspace/build.png"),
        )
    }

    @Test
    fun leavesEmptyAltUnspecified() {
        assertEquals(ChatMarkdownImageLink("/tmp/build.png", null), resolve("![](/tmp/build.png)"))
    }

    @Test
    fun doesNotInventADestinationForAnUnresolvedReference() {
        assertNull(resolve("![Screenshot][missing]"))
    }

    private fun resolve(content: String, vararg references: Pair<String, String>): ChatMarkdownImageLink? {
        val tree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(content)
        val imageNode = findImage(tree) ?: error("测试 Markdown 中缺少图片节点")
        val referenceHandler = ReferenceLinkHandlerImpl()
        references.forEach { (label, link) -> referenceHandler.store(label, link) }
        return resolveChatMarkdownImageLink(
            content = content,
            node = imageNode,
            settings = DefaultAnnotatorSettings(
                linkTextSpanStyle = TextLinkStyles(),
                codeSpanStyle = SpanStyle(),
                annotator = markdownAnnotator(),
                referenceLinkHandler = referenceHandler,
            ),
        )
    }

    private fun findImage(node: ASTNode): ASTNode? =
        if (node.type == MarkdownElementTypes.IMAGE) node else node.children.firstNotNullOfOrNull(::findImage)
}
