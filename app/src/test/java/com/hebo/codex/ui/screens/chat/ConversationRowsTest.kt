package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.ActivityKind
import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.ChangedFile
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.FileChangeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRowsTest {
    @Test
    fun `连续活动按回合合并`() {
        val items = listOf(
            command("command-1", "turn-1"),
            command("command-2", "turn-1"),
            ConversationItem.AgentMessage("answer", "turn-1", "完成"),
            command("command-3", "turn-2"),
        )

        val rows = buildConversationRows(items, activeTurnId = null)

        assertEquals(3, rows.size)
        assertTrue(rows[0] is ConversationRow.ActivityGroup)
        assertEquals(2, (rows[0] as ConversationRow.ActivityGroup).items.size)
        assertTrue(rows[1] is ConversationRow.Item)
        assertTrue(rows[2] is ConversationRow.ActivityGroup)
    }

    @Test
    fun `不同回合的活动不会合并`() {
        val rows = buildConversationRows(
            items = listOf(command("command-1", "turn-1"), command("command-2", "turn-2")),
            activeTurnId = null,
        )

        assertEquals(2, rows.size)
        assertTrue(rows.all { it is ConversationRow.ActivityGroup })
    }

    @Test
    fun `相同条目编号在不同回合中具有不同列表键`() {
        val rows = buildConversationRows(
            items = listOf(
                ConversationItem.AgentMessage("item-1", "turn-1", "第一回合"),
                ConversationItem.AgentMessage("item-1", "turn-2", "第二回合"),
            ),
            activeTurnId = null,
        )

        assertEquals(2, rows.size)
        assertEquals(2, rows.map(ConversationRow::key).toSet().size)
    }

    @Test
    fun `相同起始条目编号在不同回合中具有不同活动组键`() {
        val rows = buildConversationRows(
            items = listOf(
                command("item-1", "turn-1"),
                command("item-2", "turn-1"),
                command("item-1", "turn-2"),
                command("item-2", "turn-2"),
            ),
            activeTurnId = null,
        )

        assertEquals(2, rows.size)
        assertTrue(rows.all { it is ConversationRow.ActivityGroup })
        assertEquals(2, rows.map(ConversationRow::key).toSet().size)
    }

    @Test
    fun `推理和操作收纳到同一活动组`() {
        val reasoning = ConversationItem.Reasoning(
            id = "reasoning",
            turnId = "turn-1",
            summaryParts = listOf("分析项目"),
            rawContentParts = listOf("先检查目录"),
        )

        val rows = buildConversationRows(
            items = listOf(reasoning, command("command", "turn-1")),
            activeTurnId = null,
        )

        assertEquals(1, rows.size)
        assertEquals(2, (rows.single() as ConversationRow.ActivityGroup).items.size)
        assertEquals(
            "推理 1 · 命令 1",
            activityGroupLabel(
                items = (rows.single() as ConversationRow.ActivityGroup).items,
                isSealed = true,
            ),
        )
    }

    @Test
    fun `未封口的操作显示当前命令`() {
        val rows = buildConversationRows(
            items = listOf(command("command", "turn-1", ActivityStatus.IN_PROGRESS)),
            activeTurnId = "turn-1",
        )
        val group = rows.single() as ConversationRow.ActivityGroup

        assertTrue(!group.isSealed)
        assertEquals(
            "pwd",
            activityGroupLabel(group.items, group.isSealed),
        )
    }

    @Test
    fun `操作完成后正文出现前仍保持未封口`() {
        val rows = buildConversationRows(
            items = listOf(command("command", "turn-1")),
            activeTurnId = "turn-1",
        )
        val group = rows.single() as ConversationRow.ActivityGroup

        assertTrue(!group.isSealed)
        assertEquals("pwd", activityGroupLabel(group.items, group.isSealed))
    }

    @Test
    fun `封口后的单个活动仍显示活动标题`() {
        val rows = buildConversationRows(
            items = listOf(
                command("command", "turn-1"),
                ConversationItem.AgentMessage("answer", "turn-1", "开始回答"),
            ),
            activeTurnId = "turn-1",
        )
        val group = rows.first() as ConversationRow.ActivityGroup

        assertTrue(group.isSealed)
        assertEquals("pwd", activityGroupLabel(group.items, group.isSealed))
    }

    @Test
    fun `空正文不会为活动组封口`() {
        val rows = buildConversationRows(
            items = listOf(
                command("command", "turn-1"),
                ConversationItem.AgentMessage("answer", "turn-1", ""),
            ),
            activeTurnId = "turn-1",
        )

        assertTrue(!(rows.first() as ConversationRow.ActivityGroup).isSealed)
    }

    @Test
    fun `正文后的新活动形成新的未封口活动组`() {
        val rows = buildConversationRows(
            items = listOf(
                command("command-1", "turn-1"),
                ConversationItem.AgentMessage("answer", "turn-1", "继续处理"),
                command("command-2", "turn-1"),
            ),
            activeTurnId = "turn-1",
        )
        val groups = rows.filterIsInstance<ConversationRow.ActivityGroup>()

        assertTrue(groups[0].isSealed)
        assertTrue(!groups[1].isSealed)
    }

    @Test
    fun `回合结束时为没有正文的活动组封口`() {
        val rows = buildConversationRows(
            items = listOf(command("command", "turn-1")),
            activeTurnId = null,
        )

        assertTrue((rows.single() as ConversationRow.ActivityGroup).isSealed)
    }

    @Test
    fun `文件摘要按不同路径计数`() {
        val firstChange = fileChange(
            id = "change-1",
            paths = listOf("src/Main.kt", "src/Theme.kt"),
        )
        val secondChange = fileChange(
            id = "change-2",
            paths = listOf("src/Main.kt"),
        )

        assertEquals(
            "文件 2",
            activityGroupLabel(listOf(firstChange, secondChange), isSealed = true),
        )
    }

    @Test
    fun `活动组汇总全部类型并保持固定顺序`() {
        val items = listOf(
            command("command", "turn-1"),
            ConversationItem.Activity(
                id = "activity",
                turnId = "turn-1",
                kind = ActivityKind.WEB_SEARCH,
                title = "搜索网页",
                status = ActivityStatus.COMPLETED,
            ),
            fileChange("change", listOf("src/Main.kt", "src/Theme.kt")),
            ConversationItem.Reasoning(
                id = "reasoning",
                turnId = "turn-1",
                summaryParts = listOf("检查项目"),
                rawContentParts = emptyList(),
            ),
        )

        assertEquals(
            listOf(
                ActivityGroupMetric(ActivityGroupMetricType.REASONING, 1),
                ActivityGroupMetric(ActivityGroupMetricType.COMMAND, 1),
                ActivityGroupMetric(ActivityGroupMetricType.FILE, 2),
                ActivityGroupMetric(ActivityGroupMetricType.ACTIVITY, 1),
            ),
            activityGroupMetrics(items),
        )
    }

    @Test
    fun `单次文件修改按一个活动显示标题`() {
        val change = fileChange(
            id = "change",
            paths = listOf("src/Main.kt", "src/Theme.kt"),
        )

        assertEquals(
            "2 个文件",
            activityGroupLabel(listOf(change), isSealed = false),
        )
    }

    @Test
    fun `未封口的思考显示最新摘要`() {
        val reasoning = ConversationItem.Reasoning(
            id = "reasoning",
            turnId = "turn-1",
            summaryParts = listOf("检查项目", "定位活动组状态"),
            rawContentParts = emptyList(),
        )

        assertEquals(
            "定位活动组状态",
            activityGroupLabel(listOf(reasoning), isSealed = false),
        )
    }

    @Test
    fun `单个失败活动显示活动标题和状态`() {
        assertEquals(
            "pwd · 失败",
            activityGroupLabel(
                listOf(command("command", "turn-1", ActivityStatus.FAILED)),
                isSealed = true,
            ),
        )
    }

    @Test
    fun `未封口活动组跟随最后一个进行中活动`() {
        val items = listOf(
            command("command-1", "turn-1"),
            command("command-2", "turn-1", ActivityStatus.IN_PROGRESS),
            command("command-3", "turn-1", ActivityStatus.IN_PROGRESS),
        )

        assertEquals(2, activityFollowTargetIndex(items, isSealed = false))
        assertEquals(4, activityHeaderLazyIndex(2))
    }

    @Test
    fun `未封口活动组暂时没有进行中活动时跟随最后一项`() {
        val items = listOf(
            command("command-1", "turn-1"),
            command("command-2", "turn-1"),
        )

        assertEquals(1, activityFollowTargetIndex(items, isSealed = false))
    }

    @Test
    fun `封口活动组不自动定位失败活动`() {
        val items = listOf(
            command("command-1", "turn-1"),
            command("command-2", "turn-1", ActivityStatus.FAILED),
        )

        assertEquals(null, activityFollowTargetIndex(items, isSealed = true))
    }

    @Test
    fun `计划作为独立的持久化记录展示`() {
        val plan = ConversationItem.Plan(
            id = "plan-1",
            turnId = "turn-1",
            text = "1. 检查实现",
        )

        val rows = buildConversationRows(
            items = listOf(command("command", "turn-1"), plan),
            activeTurnId = null,
        )

        assertEquals(2, rows.size)
        assertTrue(rows[0] is ConversationRow.ActivityGroup)
        assertEquals(plan, (rows[1] as ConversationRow.Item).item)
    }

    private fun command(
        id: String,
        turnId: String,
        status: ActivityStatus = ActivityStatus.COMPLETED,
    ) = ConversationItem.CommandExecution(
        id = id,
        turnId = turnId,
        command = "pwd",
        cwd = "/project",
        output = "",
        exitCode = 0,
        status = status,
    )

    private fun fileChange(
        id: String,
        paths: List<String>,
    ) = ConversationItem.FileChange(
        id = id,
        turnId = "turn-1",
        changes = paths.map { path ->
            ChangedFile(path = path, kind = FileChangeKind.Update(movePath = null), diff = "")
        },
        status = ActivityStatus.COMPLETED,
    )
}
