package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.ActivityKind
import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.ChangedFile
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.ConversationItemKey
import com.hebo.codex.domain.FileChangeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityItemPresentationTest {
    @Test
    fun `思考只有被实时激活时才显示进行中`() {
        val item = ConversationItem.Reasoning(
            id = "reasoning",
            turnId = "turn",
            summaryParts = listOf("检查实现"),
            rawContentParts = emptyList(),
        )

        assertEquals(ActivityStatus.COMPLETED, item.activityStatus())
        assertEquals(
            ActivityStatus.IN_PROGRESS,
            item.activityStatus(setOf(ConversationItemKey("turn", "reasoning"))),
        )
    }

    @Test
    fun `失败命令标题保留命令预览`() {
        val item = ConversationItem.CommandExecution(
            id = "command",
            turnId = "turn",
            command = "./gradlew test\n--stacktrace",
            cwd = "/workspace",
            output = "",
            exitCode = 1,
            status = ActivityStatus.FAILED,
        )

        assertEquals("./gradlew test · 失败", activityItemTitle(item))
    }

    @Test
    fun `命令标题隐藏 shell 执行包装`() {
        val item = ConversationItem.CommandExecution(
            id = "command",
            turnId = "turn",
            command = "/usr/bin/zsh -lc \"./gradlew test --stacktrace\"",
            cwd = "/workspace",
            output = "",
            exitCode = null,
            status = ActivityStatus.IN_PROGRESS,
        )

        assertEquals("./gradlew test --stacktrace", activityItemTitle(item))
    }

    @Test
    fun `失败文件修改标题保留文件数量`() {
        val item = ConversationItem.FileChange(
            id = "change",
            turnId = "turn",
            changes = listOf(
                ChangedFile(path = "first.kt", kind = FileChangeKind.Add, diff = ""),
                ChangedFile(path = "second.kt", kind = FileChangeKind.Delete, diff = ""),
            ),
            status = ActivityStatus.FAILED,
        )

        assertEquals("2 个文件 · 失败", activityItemTitle(item))
    }

    @Test
    fun `推理摘要或原始推理存在时条目可以展开`() {
        val emptyItem = ConversationItem.Reasoning(
            id = "empty",
            turnId = "turn",
            summaryParts = emptyList(),
            rawContentParts = emptyList(),
        )
        val summaryItem = emptyItem.copy(
            id = "summary",
            summaryParts = listOf("**检查实现**\n\n正在定位问题"),
        )
        val rawItem = emptyItem.copy(id = "raw", rawContentParts = listOf("原始推理"))

        assertFalse(activityItemHasDetails(emptyItem))
        assertTrue(activityItemHasDetails(summaryItem))
        assertTrue(activityItemHasDetails(rawItem))
    }

    @Test
    fun `推理标题使用最新摘要分段的加粗标题`() {
        val item = ConversationItem.Reasoning(
            id = "reasoning",
            turnId = "turn",
            summaryParts = listOf(
                "**检查项目**\n\n读取目录结构",
                "**定位问题**\n\n检查消息渲染",
            ),
            rawContentParts = emptyList(),
        )

        assertEquals("定位问题", activityItemTitle(item))
    }

    @Test
    fun `推理摘要正文移除首个标题和空占位分段`() {
        assertEquals(
            "检查消息渲染",
            reasoningSummaryBody(
                listOf(
                    "**准备分析**\n\n<!-- -->",
                    "**定位问题**\n\n检查消息渲染",
                ),
            ),
        )
    }

    @Test
    fun `远程文件入口属于可展开详情`() {
        val item = ConversationItem.Activity(
            id = "activity",
            turnId = "turn",
            kind = ActivityKind.IMAGE_GENERATION,
            title = "生成图片",
            remotePath = "/tmp/image.png",
            status = ActivityStatus.COMPLETED,
        )

        assertTrue(activityItemHasDetails(item))
    }

    @Test
    fun `单个文件标题直接显示路径`() {
        val item = ConversationItem.FileChange(
            id = "change",
            turnId = "turn",
            changes = listOf(
                ChangedFile(path = "src/Main.kt", kind = FileChangeKind.Update(movePath = null), diff = ""),
            ),
            status = ActivityStatus.COMPLETED,
        )

        assertEquals("src/Main.kt", activityItemTitle(item))
    }

    @Test
    fun `通用活动摘要作为次级文字`() {
        val item = ConversationItem.Activity(
            id = "activity",
            turnId = "turn",
            kind = ActivityKind.MCP_TOOL,
            title = "fetch",
            summary = "github",
            status = ActivityStatus.COMPLETED,
        )

        assertEquals("fetch", activityItemTitle(item))
        assertEquals("github", activityItemSubtitle(item))
    }
}
