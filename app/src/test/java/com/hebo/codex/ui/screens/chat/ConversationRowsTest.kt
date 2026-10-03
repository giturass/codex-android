package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.ActivityKind
import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.AgentMessagePhase
import com.hebo.codex.domain.ChangedFile
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.ConversationTurn
import com.hebo.codex.domain.ConversationTurnStatus
import com.hebo.codex.domain.FileChangeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRowsTest {
    @Test
    fun `相同条目编号的不同回合各自保留入口和独立列表键`() {
        val firstAnswer = finalAnswer("answer", "turn-1", "第一回合")
        val secondAnswer = finalAnswer("answer", "turn-2", "第二回合")
        val rows = buildConversationRows(
            listOf(
                turn(command("command", "turn-1"), firstAnswer),
                turn(command("command", "turn-2"), secondAnswer),
            ),
        )

        assertEquals(
            listOf(
                ConversationRow.TurnActivity("turn-1"),
                ConversationRow.Item(firstAnswer),
                ConversationRow.TurnActivity("turn-2"),
                ConversationRow.Item(secondAnswer),
            ),
            rows,
        )
        assertEquals(rows.size, rows.map(ConversationRow::key).toSet().size)
    }

    @Test
    fun `推理进度计划和操作只在回合详情中展示`() {
        val reasoning = ConversationItem.Reasoning(
            id = "reasoning",
            turnId = "turn-1",
            summaryParts = listOf("分析项目"),
            rawContentParts = listOf("先检查目录"),
        )

        val commentary = ConversationItem.AgentMessage(
            id = "commentary",
            turnId = "turn-1",
            text = "正在检查实现",
            phase = AgentMessagePhase.COMMENTARY,
        )
        val plan = ConversationItem.Plan(
            id = "plan",
            turnId = "turn-1",
            text = "1. 检查实现",
        )

        val turn = turn(reasoning, commentary, plan, command("command", "turn-1"))

        assertEquals(listOf(ConversationRow.TurnActivity(turn.id)), buildConversationRows(listOf(turn)))
        val details = activityDetailsContent(turn, activeTurnId = turn.id)
        assertEquals(turn.items, details.items)
        assertFalse(details.isSealed)
    }

    @Test
    fun `操作完成后和未标记阶段的空正文均保持详情跟随`() {
        val command = command("command", "turn-1")
        listOf(
            turn(command),
            turn(command, ConversationItem.AgentMessage("answer", "turn-1", "")),
        ).forEach { turn ->
            assertFalse(activityDetailsContent(turn, activeTurnId = turn.id).isSealed)
        }
    }

    @Test
    fun `最终回答开始或未标记阶段的正文出现时停止详情跟随`() {
        listOf(
            finalAnswer("answer", "turn-1", ""),
            ConversationItem.AgentMessage("answer", "turn-1", "完成"),
        ).forEach { answer ->
            val turn = turn(command("command", "turn-1"), answer)

            assertTrue(activityDetailsContent(turn, activeTurnId = turn.id).isSealed)
        }
    }

    @Test
    fun `正文后的新活动恢复详情跟随且不增加主列表入口`() {
        val answer = finalAnswer("answer", "turn-1", "继续处理")
        val turn = turn(
            command("command-1", "turn-1"),
            answer,
            command("command-2", "turn-1"),
        )
        val details = activityDetailsContent(turn, activeTurnId = turn.id)

        assertEquals(
            listOf(ConversationRow.TurnActivity(turn.id), ConversationRow.Item(answer)),
            buildConversationRows(listOf(turn)),
        )
        assertEquals(listOf(turn.items.first(), turn.items.last()), details.items)
        assertFalse(details.isSealed)
    }

    @Test
    fun `回合结束或已有其他活跃回合时停止详情跟随`() {
        val turn = turn(command("command", "turn-1"))

        assertTrue(activityDetailsContent(turn, activeTurnId = null).isSealed)
        assertTrue(activityDetailsContent(turn, activeTurnId = "turn-2").isSealed)
    }

    @Test
    fun `文件摘要按修改过程计数`() {
        val firstChange = fileChange(
            id = "change-1",
            paths = listOf("src/Main.kt"),
        )
        val secondChange = fileChange(
            id = "change-2",
            paths = listOf("src/Main.kt"),
        )

        assertEquals(
            listOf(ActivityGroupMetric(ActivityGroupMetricType.FILE, 2)),
            activityGroupMetrics(listOf(firstChange, secondChange)),
        )
    }

    @Test
    fun `处理过程汇总全部类型并保持固定顺序`() {
        val items = listOf(
            ConversationItem.AgentMessage(
                id = "commentary",
                turnId = "turn-1",
                text = "正在检查",
                phase = AgentMessagePhase.COMMENTARY,
            ),
            ConversationItem.Plan(
                id = "plan",
                turnId = "turn-1",
                text = "1. 检查实现",
            ),
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
                ActivityGroupMetric(ActivityGroupMetricType.PROGRESS, 2),
                ActivityGroupMetric(ActivityGroupMetricType.COMMAND, 1),
                ActivityGroupMetric(ActivityGroupMetricType.FILE, 1),
                ActivityGroupMetric(ActivityGroupMetricType.ACTIVITY, 1),
            ),
            activityGroupMetrics(items),
        )
    }

    @Test
    fun `处理过程汇总各类型的失败过程数`() {
        val items = listOf(
            command("command-completed", "turn-1"),
            command("command-failed", "turn-1", ActivityStatus.FAILED),
            command("command-declined", "turn-1", ActivityStatus.DECLINED),
            fileChange("change-completed", listOf("src/Main.kt")),
            fileChange("change-failed", listOf("src/Main.kt"), ActivityStatus.FAILED),
            ConversationItem.Activity(
                id = "activity-failed",
                turnId = "turn-1",
                kind = ActivityKind.WEB_SEARCH,
                title = "搜索网页",
                status = ActivityStatus.FAILED,
            ),
        )

        assertEquals(
            listOf(
                ActivityGroupMetric(ActivityGroupMetricType.COMMAND, count = 3, failedCount = 1),
                ActivityGroupMetric(ActivityGroupMetricType.FILE, count = 2, failedCount = 1),
                ActivityGroupMetric(ActivityGroupMetricType.ACTIVITY, count = 1, failedCount = 1),
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
            activityItemTitle(change),
        )
    }

    @Test
    fun `思考条目标题显示最新摘要`() {
        val reasoning = ConversationItem.Reasoning(
            id = "reasoning",
            turnId = "turn-1",
            summaryParts = listOf("检查项目", "定位活动组状态"),
            rawContentParts = emptyList(),
        )

        assertEquals(
            "定位活动组状态",
            activityItemTitle(reasoning),
        )
    }

    @Test
    fun `单个失败活动显示活动标题和状态`() {
        assertEquals(
            "pwd · 失败",
            activityItemTitle(command("command", "turn-1", ActivityStatus.FAILED)),
        )
    }

    @Test
    fun `活跃详情跟随最后一个进行中活动`() {
        val items = listOf(
            command("command-1", "turn-1"),
            command("command-2", "turn-1", ActivityStatus.IN_PROGRESS),
            command("command-3", "turn-1", ActivityStatus.IN_PROGRESS),
        )

        assertEquals(2, activityFollowTargetIndex(items, isSealed = false))
        assertEquals(4, activityHeaderLazyIndex(2))
    }

    @Test
    fun `活跃详情暂时没有进行中活动时跟随最后一项`() {
        val items = listOf(
            command("command-1", "turn-1"),
            command("command-2", "turn-1"),
        )

        assertEquals(1, activityFollowTargetIndex(items, isSealed = false))
    }

    @Test
    fun `已封口详情不自动定位失败活动`() {
        val items = listOf(
            command("command-1", "turn-1"),
            command("command-2", "turn-1", ActivityStatus.FAILED),
        )

        assertEquals(null, activityFollowTargetIndex(items, isSealed = true))
    }

    @Test
    fun `用户 Shell 命令作为独立聊天记录展示`() {
        val shell = ConversationItem.UserShellCommand(
            id = "shell",
            turnId = "turn-1",
            command = "/usr/bin/zsh -lc 'pwd'",
            cwd = "/project",
            output = "/project",
            exitCode = 0,
            durationMillis = 100,
            status = ActivityStatus.COMPLETED,
        )

        val turn = turn(command("command", "turn-1"), shell)
        val rows = buildConversationRows(listOf(turn))

        assertEquals(listOf(ConversationRow.TurnActivity(turn.id), ConversationRow.Item(shell)), rows)
        assertEquals(turn.items, activityDetailsContent(turn, activeTurnId = null).items)
    }

    private fun turn(vararg items: ConversationItem) = ConversationTurn(
        id = items.first().turnId,
        status = ConversationTurnStatus.IN_PROGRESS,
        items = items.toList(),
    )

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

    private fun finalAnswer(
        id: String,
        turnId: String,
        text: String,
    ) = ConversationItem.AgentMessage(
        id = id,
        turnId = turnId,
        text = text,
        phase = AgentMessagePhase.FINAL_ANSWER,
    )

    private fun fileChange(
        id: String,
        paths: List<String>,
        status: ActivityStatus = ActivityStatus.COMPLETED,
    ) = ConversationItem.FileChange(
        id = id,
        turnId = "turn-1",
        changes = paths.map { path ->
            ChangedFile(path = path, kind = FileChangeKind.Update(movePath = null), diff = "")
        },
        status = status,
    )
}
