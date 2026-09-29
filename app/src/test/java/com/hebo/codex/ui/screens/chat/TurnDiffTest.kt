package com.hebo.codex.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnDiffTest {
    @Test
    fun parsesAddedUpdatedDeletedAndRenamedFilesWithTheirOriginalPatches() {
        val added = """
            diff --git a/new.txt b/new.txt
            new file mode 100644
            index 0000000..2222222
            --- /dev/null
            +++ b/new.txt
            @@ -0,0 +1,2 @@
            +foo
            +bar
        """.trimIndent() + "\n"
        val updated = """
            diff --git a/main.kt b/main.kt
            index 1111111..2222222
            --- a/main.kt
            +++ b/main.kt
            @@ -1,2 +1,2 @@
             unchanged
            -old
            +new
        """.trimIndent() + "\n"
        val deleted = """
            diff --git a/old.txt b/old.txt
            deleted file mode 100644
            index 1111111..0000000
            --- a/old.txt
            +++ /dev/null
            @@ -1 +0,0 @@
            -old
        """.trimIndent() + "\n"
        val renamed = """
            diff --git a/src.txt b/dst.txt
            index 1111111..2222222
            --- a/src.txt
            +++ b/dst.txt
            @@ -1 +1 @@
            -line
            +line2
        """.trimIndent() + "\n"

        assertEquals(
            ParsedTurnDiff(
                listOf(
                    TurnDiffFile("new.txt", null, TurnDiffKind.ADD, 2, 0, added),
                    TurnDiffFile("main.kt", null, TurnDiffKind.UPDATE, 1, 1, updated),
                    TurnDiffFile("old.txt", null, TurnDiffKind.DELETE, 0, 1, deleted),
                    TurnDiffFile("dst.txt", "src.txt", TurnDiffKind.RENAME, 1, 1, renamed),
                ),
                incomplete = false,
            ),
            parseTurnDiff(added + updated + deleted + renamed),
        )
    }

    @Test
    fun preservesSpacesChineseAndDelimiterLikeTextInPaths() {
        val diff = """
            diff --git a/旧 文档 b/说明.md b/新 文档 b/指南.md
            index 1111111..2222222
            --- a/旧 文档 b/说明.md
            +++ b/新 文档 b/指南.md
            @@ -1 +1 @@
            -旧内容
            +新内容
        """.trimIndent()

        assertEquals(
            ParsedTurnDiff(
                listOf(TurnDiffFile("新 文档 b/指南.md", "旧 文档 b/说明.md", TurnDiffKind.RENAME, 1, 1, diff)),
                incomplete = false,
            ),
            parseTurnDiff(diff),
        )
    }

    @Test
    fun countsOnlyHunkContentIncludingLinesThatLookLikeFileHeaders() {
        val diff = """
            diff --git a/example.txt b/example.txt
            index 1111111..2222222
            --- a/example.txt
            +++ b/example.txt
            @@ -1,2 +1,3 @@
             context
            --- removed
            +++ added
            +second
            @@ -10 +11 @@
            -last old
            \ No newline at end of file
            +last new
            \ No newline at end of file
        """.trimIndent()

        val parsed = parseTurnDiff(diff)

        assertFalse(parsed.incomplete)
        assertEquals(3, parsed.files.single().additions)
        assertEquals(2, parsed.files.single().deletions)
    }

    @Test
    fun parsesEmptyFileAdditionsAndDeletionsWithoutHunks() {
        listOf("new file mode" to TurnDiffKind.ADD, "deleted file mode" to TurnDiffKind.DELETE).forEach { (mode, kind) ->
            val diff = """
                diff --git a/空 文件 b/说明.txt b/空 文件 b/说明.txt
                $mode 100644
                index 0000000..e69de29
            """.trimIndent()

            assertEquals(
                ParsedTurnDiff(
                    listOf(TurnDiffFile("空 文件 b/说明.txt", null, kind, 0, 0, diff)),
                    incomplete = false,
                ),
                parseTurnDiff(diff),
            )
        }
    }

    @Test
    fun keepsCompleteFilesWhenOtherPartsAreUnrecognizedOrTruncated() {
        val complete = """
            diff --git a/complete.txt b/complete.txt
            index 1111111..2222222
            --- a/complete.txt
            +++ b/complete.txt
            @@ -1 +1 @@
            -before
            +after
        """.trimIndent() + "\n"
        val missingHeader = "@@ -1 +1 @@\n-old\n+new\n"
        val missingHunk = "diff --git a/partial.txt b/partial.txt\n--- a/partial.txt\n+++ b/partial.txt\n"

        val parsed = parseTurnDiff(missingHeader + complete + missingHunk)

        assertTrue(parsed.incomplete)
        assertEquals(
            listOf(TurnDiffFile("complete.txt", null, TurnDiffKind.UPDATE, 1, 1, complete)),
            parsed.files,
        )
    }

    @Test
    fun rejectsUnknownContentAndUnverifiableFilePaths() {
        listOf(
            "无法识别的内容",
            "@@ -1 +1 @@\n-before\n+after\n",
            "diff --git a/missing.txt b/missing.txt\n",
            "diff --git a/wrong.txt b/wrong.txt\n--- a/actual.txt\n+++ b/actual.txt\n@@ -1 +1 @@\n-a\n+b\n",
            "diff --git a/file.txt b/file.txt\n--- file.txt\n+++ file.txt\n@@ -1 +1 @@\n-a\n+b\n",
            "diff --git a/file.txt b/file.txt\nnew file mode 100644\n",
        ).forEach { diff ->
            assertEquals(diff, ParsedTurnDiff(emptyList(), incomplete = true), parseTurnDiff(diff))
        }
    }

    @Test
    fun blankDiffHasNoChangesOrMissingInformation() {
        listOf("", "\n \t\n").forEach { diff ->
            assertEquals(ParsedTurnDiff(emptyList(), incomplete = false), parseTurnDiff(diff))
        }
    }
}
