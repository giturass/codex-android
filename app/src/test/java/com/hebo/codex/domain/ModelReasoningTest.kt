package com.hebo.codex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ModelReasoningTest {
    @Test
    fun `会话默认模型仍可解析隐藏模型的深度能力`() {
        val hidden = model("hidden", hidden = true)
        val default = model("default", isDefault = true)

        assertSame(hidden, listOf(default, hidden).resolveModelOption(null, "hidden"))
        assertSame(default, listOf(default, hidden).resolveModelOption(null))
    }

    @Test
    fun `未知模型不会套用其他模型的能力`() {
        val models = listOf(model("default", isDefault = true))

        assertNull(models.resolveModelOption("custom"))
        assertNull(models.resolveModelOption(null, "custom"))
    }

    @Test
    fun `切换模型保留仍被支持的深度`() {
        val options = TurnOptions(model = "old", reasoningEffort = "high")
        val selected = options.selectModel(model("new", efforts = listOf("medium", "high")))

        assertEquals("new", selected.model)
        assertEquals("high", selected.reasoningEffort)
    }

    @Test
    fun `从会话默认切换模型时保留兼容的实际深度`() {
        val selected = TurnOptions().selectModel(
            model("new", efforts = listOf("medium", "high")),
            inheritedReasoningEffort = "high",
        )

        assertEquals("high", selected.reasoningEffort)
    }

    @Test
    fun `新模型不支持当前深度时使用模型声明的默认值`() {
        val options = TurnOptions(model = "old", reasoningEffort = "ultra")
        val selected = options.selectModel(model("new", efforts = listOf("medium", "high")))

        assertEquals("medium", selected.reasoningEffort)
    }

    @Test
    fun `目录可以声明任意非空思考深度`() {
        val custom = model("new", efforts = listOf("adaptive"), defaultEffort = "adaptive")

        assertEquals("adaptive", ThreadOptions().selectModel(custom).reasoningEffort)
    }

    @Test
    fun `模型没有思考深度选项时清除旧深度`() {
        val model = model("new", efforts = emptyList())

        assertNull(ThreadOptions(model = "old", reasoningEffort = "high").selectModel(model).reasoningEffort)
        assertNull(TurnOptions(model = "old", reasoningEffort = "high").selectModel(model).reasoningEffort)
    }

    @Test
    fun `重新选择同一模型保留默认选择和服务层级`() {
        val model = model("selected")
        val turn = TurnOptions(model = "selected", serviceTier = "fast", reasoningEffort = null)
        val thread = ThreadOptions(model = "selected", serviceTier = "fast", reasoningEffort = null)

        assertSame(turn, turn.selectModel(model))
        assertSame(thread, thread.selectModel(model))
    }

    @Test
    fun `选择会话默认清除模型相关覆盖而保留其他设置`() {
        val options = TurnOptions(
            model = "selected",
            serviceTier = "fast",
            reasoningEffort = "high",
            approvalPolicy = ApprovalPolicy.ON_REQUEST,
        ).selectModel(null)

        assertNull(options.model)
        assertNull(options.reasoningEffort)
        assertNull(options.serviceTier)
        assertEquals(ApprovalPolicy.ON_REQUEST, options.approvalPolicy)
    }

    @Test
    fun `新建任务默认选择和模型切换使用相同的深度规则`() {
        val model = model("new", efforts = listOf("medium", "high"))

        assertEquals("medium", ThreadOptions().selectModel(model).reasoningEffort)
        assertEquals("high", ThreadOptions(model = "old", reasoningEffort = "high").selectModel(model).reasoningEffort)
        assertEquals("medium", ThreadOptions(model = "old", reasoningEffort = "ultra").selectModel(model).reasoningEffort)
    }

    @Test
    fun `首回合和失败迁移使用新任务选择的完整设置`() {
        val options = ThreadOptions(
            model = "selected",
            reasoningEffort = "ultra",
            serviceTier = "fast",
            approvalPolicy = ApprovalPolicy.ON_REQUEST,
            approvalsReviewer = ApprovalReviewer.USER,
            sandboxMode = SandboxMode.WORKSPACE_WRITE,
        )

        assertEquals(
            TurnOptions(
                model = "selected",
                reasoningEffort = "ultra",
                serviceTier = "fast",
                approvalPolicy = ApprovalPolicy.ON_REQUEST,
                approvalsReviewer = ApprovalReviewer.USER,
                sandboxMode = SandboxMode.WORKSPACE_WRITE,
            ),
            options.toTurnOptions(),
        )
    }

    private fun model(
        id: String,
        efforts: List<String> = listOf("medium", "high"),
        defaultEffort: String = "medium",
        hidden: Boolean = false,
        isDefault: Boolean = false,
    ) = ModelOption(
        id = id,
        model = id,
        displayName = id,
        description = "",
        isDefault = isDefault,
        hidden = hidden,
        inputModalities = listOf("text"),
        reasoningEfforts = efforts.map { OptionValue(it, it) },
        defaultReasoningEffort = defaultEffort,
        serviceTiers = emptyList(),
        defaultServiceTier = null,
    )
}
