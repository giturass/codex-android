package com.hebo.codex.data.remote

import com.hebo.codex.domain.RuntimeActivityStatus
import com.hebo.codex.domain.RuntimeStatusEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeStatusMapperTest {
    @Test
    fun `Hook 完成通知保留警告与输出并复用运行标识`() {
        val event = notification(
            "hook/completed",
            """{"threadId":"thread","turnId":"turn","run":{"id":"hook","eventName":"preToolUse","status":"completed","entries":[{"kind":"warning","text":"需要检查配置"}]}}""",
        ) as RuntimeStatusEvent.ActivityChanged

        assertEquals("hook:hook", event.activity.id)
        assertEquals(RuntimeActivityStatus.WARNING, event.activity.status)
        assertEquals("需要检查配置", event.activity.text)
    }

    @Test
    fun `安全检查结束更新同一活动避免永久显示进行中`() {
        val running = notification(
            "model/safetyBuffering/updated",
            """{"threadId":"thread","turnId":"turn","model":"gpt","showBufferingUi":true,"reasons":[],"useCases":[]}""",
        ) as RuntimeStatusEvent.ActivityChanged
        val completed = notification(
            "model/safetyBuffering/updated",
            """{"threadId":"thread","turnId":"turn","model":"gpt","showBufferingUi":false,"reasons":[],"useCases":[]}""",
        ) as RuntimeStatusEvent.ActivityChanged

        assertEquals(running.activity.id, completed.activity.id)
        assertEquals(RuntimeActivityStatus.RUNNING, running.activity.status)
        assertEquals(RuntimeActivityStatus.COMPLETED, completed.activity.status)
    }

    @Test
    fun `自动审批拒绝保留拒绝原因`() {
        val event = notification(
            "item/autoApprovalReview/completed",
            """{"threadId":"thread","turnId":"turn","reviewId":"review","review":{"status":"denied","rationale":"超出授权范围"}}""",
        ) as RuntimeStatusEvent.ActivityChanged

        assertEquals(RuntimeActivityStatus.FAILED, event.activity.status)
        assertEquals("操作被拒绝 · 超出授权范围", event.activity.detail)
    }

    @Test
    fun `MCP 完成通知使用回合和条目标识清除进度`() {
        val progress = notification(
            "item/mcpToolCall/progress",
            """{"threadId":"thread","turnId":"turn","itemId":"tool","message":"正在读取"}""",
        ) as RuntimeStatusEvent.ActivityChanged
        val completion = notification(
            "item/completed",
            """{"threadId":"thread","turnId":"turn","item":{"id":"tool","type":"mcpToolCall"}}""",
        ) as RuntimeStatusEvent.ActivityCompleted

        assertEquals(progress.activity.id, completion.id)
    }

    @Test
    fun `输入队列保留混合输入顺序及分页游标但不展示媒体载荷`() {
        val page = RuntimeStatusMapper.queuePage(
            Json.parseToJsonElement(
                """{"data":[{"id":"entry","clientUserMessageId":"message","input":[{"type":"text","text":"继续处理"},{"type":"image","url":"data:image/png;base64,private"},{"type":"skill","name":"review","path":"/skills/review"}]}],"nextCursor":"next"}""",
            ).jsonObject,
        )

        assertEquals("next", page.nextCursor)
        assertEquals("entry", page.data.single().id)
        assertEquals("继续处理\n已附加图片\n技能 review · /skills/review", page.data.single().description)
        assertFalse(page.data.single().description.contains("private"))
    }

    @Test
    fun `过长汇总变更明确记录截断`() {
        val event = notification(
            "turn/diff/updated",
            """{"threadId":"thread","turnId":"turn","diff":"${"x".repeat(40_000)}"}""",
        ) as RuntimeStatusEvent.DiffChanged

        assertEquals(32_768, event.diff.diff.length)
        assertTrue(event.diff.truncated)
    }

    private fun notification(method: String, params: String) =
        RuntimeStatusMapper.notification(method, Json.parseToJsonElement(params).jsonObject)
}
