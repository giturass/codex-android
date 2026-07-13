package com.hebo.codex.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteFileLinksTest {
    @Test
    fun resolvesRelativePathAgainstConversationDirectory() {
        assertEquals(
            ChatLinkDestination.RemoteFile(RemoteFileTarget("/workspace/app/src/Main.kt")),
            resolveChatLink("src/Main.kt", "/workspace/app"),
        )
    }

    @Test
    fun normalizesParentSegmentsAndPercentEncoding() {
        assertEquals(
            ChatLinkDestination.RemoteFile(RemoteFileTarget("/workspace/Example Folder/report+a.md")),
            resolveChatLink("../Example%20Folder/report+a.md", "/workspace/app"),
        )
    }

    @Test
    fun separatesLineAndColumnFromUnixPath() {
        assertEquals(
            ChatLinkDestination.RemoteFile(RemoteFileTarget("/workspace/Main.kt", line = 42, column = 3)),
            resolveChatLink("/workspace/Main.kt:42:3", "/workspace"),
        )
    }

    @Test
    fun treatsBareFileNameWithLineAsRemoteFile() {
        assertEquals(
            ChatLinkDestination.RemoteFile(RemoteFileTarget("/workspace/README.md", line = 42)),
            resolveChatLink("README.md:42", "/workspace"),
        )
    }

    @Test
    fun separatesHashLocationFromFileUri() {
        assertEquals(
            ChatLinkDestination.RemoteFile(RemoteFileTarget("/workspace/Main.kt", line = 12, column = 4)),
            resolveChatLink("file:///workspace/Main.kt#L12C4-L15C8", "/workspace"),
        )
    }

    @Test
    fun preservesWindowsDriveAndResolvesRelativePath() {
        assertEquals(
            ChatLinkDestination.RemoteFile(RemoteFileTarget("C:/workspace/src/Main.kt", line = 9)),
            resolveChatLink("src\\Main.kt:9", "C:\\workspace"),
        )
    }

    @Test
    fun delegatesWebLinksToExternalHandler() {
        assertEquals(
            ChatLinkDestination.External("https://example.com/report.pdf"),
            resolveChatLink("https://example.com/report.pdf", "/workspace"),
        )
    }

    @Test
    fun rejectsRemoteHomeRelativePath() {
        assertTrue(resolveChatLink("~/report.pdf", "/workspace") is ChatLinkDestination.Invalid)
    }
}
