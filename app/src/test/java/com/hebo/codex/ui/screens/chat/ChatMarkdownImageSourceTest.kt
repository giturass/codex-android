package com.hebo.codex.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkdownImageSourceTest {
    @Test
    fun preservesWebUrlEncodingQueriesAndFragments() {
        listOf(
            "https://example.com/image%20one.png?token=a+b#preview",
            "http://example.com/image.png",
            "HTTPS://example.com/image.png",
        ).forEach { value ->
            assertEquals(ChatImageSource.Url(value), resolveChatMarkdownImageSource(" $value ", "/workspace"))
        }
    }

    @Test
    fun preservesImageDataUrlsWithoutDecodingTheirPayload() {
        listOf(
            "data:image/png;base64,+/8=",
            "data:image/svg+xml;charset=utf-8,%3Csvg%3E%E4%B8%AD+%3C/svg%3E",
            "DATA:IMAGE/PNG;base64,AQID",
        ).forEach { value ->
            assertEquals(ChatImageSource.Url(value), resolveChatMarkdownImageSource(value, "/workspace"))
        }
    }

    @Test
    fun resolvesRelativeImagesOnTheConversationServer() {
        assertEquals(
            ChatImageSource.ServerFile("/workspace/images/Screen shot+a.png"),
            resolveChatMarkdownImageSource("../images/Screen%20shot+a.png", "/workspace/app"),
        )
    }

    @Test
    fun resolvesAbsolutePathsAndFileUrisOnTheServer() {
        listOf("/tmp/Screen%20shot.png", "file:///tmp/Screen%20shot.png").forEach { value ->
            assertEquals(
                ChatImageSource.ServerFile("/tmp/Screen shot.png"),
                resolveChatMarkdownImageSource(value, "/workspace"),
            )
        }
        assertEquals(
            ChatImageSource.ServerFile("C:/workspace/images/screenshot.png"),
            resolveChatMarkdownImageSource("images\\screenshot.png", "C:\\workspace"),
        )
    }

    @Test
    fun rejectsUnsupportedSourcesWithoutGuessingTheirProtocol() {
        listOf(
            "data:text/plain;base64,AQID",
            "ftp://example.com/image.png",
            "content://images/1",
            "javascript:alert(1)",
            "//example.com/image.png",
            "#image",
        ).forEach { value ->
            val error = assertThrows(value, IllegalArgumentException::class.java) {
                resolveChatMarkdownImageSource(value, "/workspace")
            }
            assertTrue(error.message.orEmpty().contains("仅支持"))
        }
    }

    @Test
    fun reportsUnresolvableServerPaths() {
        listOf("", "~/image.png", "image%ZZ.png").forEach { value ->
            assertThrows(value, IllegalArgumentException::class.java) {
                resolveChatMarkdownImageSource(value, "/workspace")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolveChatMarkdownImageSource("image.png", "relative-directory")
        }
    }
}
