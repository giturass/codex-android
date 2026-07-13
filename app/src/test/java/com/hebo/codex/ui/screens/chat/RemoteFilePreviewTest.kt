package com.hebo.codex.ui.screens.chat

import dev.snipme.highlights.model.SyntaxLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteFilePreviewTest {
    @Test
    fun selectsRichTextModesFromFileExtension() {
        assertEquals(TextPreviewKind.Markdown, textPreviewKind("README.md"))
        assertEquals(TextPreviewKind.Json, textPreviewKind("package.JSON"))
        assertEquals(TextPreviewKind.Plain, textPreviewKind("config.yaml"))
    }

    @Test
    fun automaticallyPreviewsCommonFilesWithoutReadingUnknownBinaryTypes() {
        assertTrue(shouldAutoPreviewRemoteFile("README.md"))
        assertTrue(shouldAutoPreviewRemoteFile("Main.kt"))
        assertTrue(shouldAutoPreviewRemoteFile("diagram.SVG"))
        assertTrue(shouldAutoPreviewRemoteFile("Dockerfile"))
        assertFalse(shouldAutoPreviewRemoteFile("report.pdf"))
        assertFalse(shouldAutoPreviewRemoteFile("archive.zip"))
        assertFalse(shouldAutoPreviewRemoteFile("unknown"))
    }

    @Test
    fun selectsSupportedSyntaxFromTechnicalFileNames() {
        assertEquals(SyntaxLanguage.KOTLIN, syntaxLanguageForFileName("build.gradle.kts"))
        assertEquals(SyntaxLanguage.TYPESCRIPT, syntaxLanguageForFileName("Component.tsx"))
        assertEquals(SyntaxLanguage.SHELL, syntaxLanguageForFileName(".zshrc"))
        assertNull(syntaxLanguageForFileName("config.yaml"))
    }

    @Test
    fun resolvesRemoteDocumentParentAcrossPathStyles() {
        assertEquals("/workspace/docs", remoteFileParent("/workspace/docs/README.md"))
        assertEquals("C:/workspace", remoteFileParent("C:\\workspace\\README.md"))
        assertEquals("/", remoteFileParent("/README.md"))
    }

    @Test
    fun decodesUtf8AndBomDeclaredUtf16WithoutGuessingBinaryContent() {
        assertEquals("中文", decodeRemoteText("中文".toByteArray()))
        assertEquals(
            "hello",
            decodeRemoteText(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "hello".toByteArray(Charsets.UTF_16LE)),
        )
        assertNull(decodeRemoteText(byteArrayOf(0x41, 0x00, 0x42)))
    }

    @Test
    fun identifiesFormatsThatSupportOriginalImageSubSampling() {
        assertTrue(
            supportsBitmapRegionDecoding(
                byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00),
            ),
        )
        assertTrue(
            supportsBitmapRegionDecoding(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
            ),
        )
        assertTrue(supportsBitmapRegionDecoding("RIFF0000WEBP".toByteArray()))
        assertFalse(supportsBitmapRegionDecoding("GIF89a".toByteArray()))
    }

    @Test
    fun keepsOriginalPixelsForTilesAndBoundsUnsupportedBitmapsInMemory() {
        assertEquals(
            4,
            bitmapPreviewSampleSize(width = 8_192, height = 4_096, supportsSubSampling = true),
        )
        assertEquals(
            1,
            bitmapPreviewSampleSize(width = 4_000, height = 2_000, supportsSubSampling = false),
        )
        assertEquals(
            2,
            bitmapPreviewSampleSize(width = 4_001, height = 2_000, supportsSubSampling = false),
        )
    }
}
