package com.hebo.codex.ui.screens.chat

import androidx.compose.ui.text.AnnotatedString
import com.hebo.codex.domain.FileChangeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeBlocksTest {
    @Test
    fun classifiesWholeAddedAndDeletedFilesByChangeKind() {
        assertEquals(DiffLineKind.ADDITION, classifyDiffLine(FileChangeKind.Add, "-literal content"))
        assertEquals(DiffLineKind.DELETION, classifyDiffLine(FileChangeKind.Delete, "+literal content"))
    }

    @Test
    fun classifiesUnifiedDiffLines() {
        val kind = FileChangeKind.Update(movePath = null)

        assertEquals(DiffLineKind.HUNK, classifyDiffLine(kind, "@@ -1 +1 @@"))
        assertEquals(DiffLineKind.METADATA, classifyDiffLine(kind, "--- a/File.kt"))
        assertEquals(DiffLineKind.METADATA, classifyDiffLine(kind, "+++ b/File.kt"))
        assertEquals(DiffLineKind.ADDITION, classifyDiffLine(kind, "+val current = true"))
        assertEquals(DiffLineKind.DELETION, classifyDiffLine(kind, "-val old = true"))
        assertEquals(DiffLineKind.CONTEXT, classifyDiffLine(kind, " val unchanged = true"))
    }

    @Test
    fun highlightsShellCommandWithoutChangingItsText() {
        val command = "if true; then echo \"ready\"; fi"

        val highlighted = buildShellHighlightedText(command, darkTheme = false)

        assertEquals(command, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())
    }

    @Test
    fun previewsFirstLinesOfLongText() {
        val preview = AnnotatedString(numberedLines(1..81)).previewLines(maxLines = 80, fromEnd = false)

        assertEquals(numberedLines(1..80) + "\n…", preview.text)
    }

    @Test
    fun previewsLastLinesOfLongText() {
        val preview = AnnotatedString(numberedLines(1..81)).previewLines(maxLines = 80, fromEnd = true)

        assertEquals("…\n" + numberedLines(2..81), preview.text)
    }

    private fun numberedLines(range: IntRange): String = range.joinToString("\n") { "line-$it" }
}
