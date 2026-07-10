package com.hebo.codex.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class DiffReviewTest {
    @Test
    fun `解析文件统计和行号`() {
        val files = parseUnifiedDiff(
            """
            diff --git a/src/App.kt b/src/App.kt
            index 111..222 100644
            --- a/src/App.kt
            +++ b/src/App.kt
            @@ -10,2 +10,3 @@
             keep
            -old
            +new
            +added
            """.trimIndent(),
        )

        assertEquals(1, files.size)
        assertEquals("src/App.kt", files.single().path)
        assertEquals(2, files.single().additions)
        assertEquals(1, files.single().deletions)
        val changed = files.single().lines.filter {
            it.kind == ReviewLineKind.ADDITION || it.kind == ReviewLineKind.DELETION
        }
        assertEquals(11, changed[0].oldLine)
        assertEquals(11, changed[1].newLine)
        assertEquals(12, changed[2].newLine)
    }

    @Test
    fun `空差异返回空列表`() {
        assertEquals(emptyList<ReviewFile>(), parseUnifiedDiff(""))
    }
}
