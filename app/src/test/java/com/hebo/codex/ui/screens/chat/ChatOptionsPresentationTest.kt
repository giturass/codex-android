package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.ModelOption
import com.hebo.codex.domain.OptionValue
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatOptionsPresentationTest {
    @Test
    fun `输入框模型标签显示推理强度原始编码`() {
        val model = model(
            reasoningEfforts = listOf(OptionValue("high", "较高")),
        )

        assertEquals(
            "GPT-5 · high",
            composerModelLabel(model.model, "high", listOf(model)),
        )
    }

    @Test
    fun `未知推理强度仍显示原始编码`() {
        val model = model()

        assertEquals(
            "GPT-5 · custom",
            composerModelLabel(model.model, "custom", listOf(model)),
        )
    }

    @Test
    fun `会话默认显示服务端实际模型和深度`() {
        val sessionModel = model().copy(
            id = "gpt-6",
            model = "gpt-6",
            displayName = "GPT-6",
            isDefault = false,
        )

        assertEquals(
            "GPT-6 · ultra",
            composerModelLabel(
                model = null,
                reasoningEffort = null,
                models = listOf(model(), sessionModel),
                defaultModel = sessionModel.model,
                defaultReasoningEffort = "ultra",
            ),
        )
    }

    @Test
    fun `显式深度覆盖会话默认深度`() {
        val model = model()

        assertEquals(
            "GPT-5 · high",
            composerModelLabel(
                model = null,
                reasoningEffort = "high",
                models = listOf(model),
                defaultModel = model.model,
                defaultReasoningEffort = "medium",
            ),
        )
    }

    @Test
    fun `显式选择当前模型仍可沿用会话深度`() {
        val model = model()

        assertEquals(
            "GPT-5 · high",
            composerModelLabel(
                model = model.model,
                reasoningEffort = null,
                models = listOf(model),
                defaultModel = model.model,
                defaultReasoningEffort = "high",
            ),
        )
    }

    @Test
    fun `目录未加载时相同模型ID仍显示服务端实际深度`() {
        assertEquals(
            "custom-model · high",
            composerModelLabel(
                model = "custom-model",
                reasoningEffort = null,
                models = emptyList(),
                defaultModel = "custom-model",
                defaultReasoningEffort = "high",
            ),
        )
    }

    @Test
    fun `模型目录默认深度不代表会话实际深度`() {
        val model = model()

        assertEquals(
            "GPT-5",
            composerModelLabel(
                model = null,
                reasoningEffort = null,
                models = listOf(model),
                defaultModel = model.model,
            ),
        )
    }

    @Test
    fun `服务器默认模型未知时不将目录推荐模型显示为已选模型`() {
        assertEquals(
            "默认模型",
            composerModelLabel(
                model = null,
                reasoningEffort = null,
                models = listOf(model()),
            ),
        )
    }

    @Test
    fun `切换到其他模型不显示旧会话深度`() {
        val model = model()

        assertEquals(
            "GPT-5",
            composerModelLabel(
                model = model.model,
                reasoningEffort = null,
                models = listOf(model),
                defaultModel = "gpt-6",
                defaultReasoningEffort = "ultra",
            ),
        )
    }

    @Test
    fun `未知模型不会沿用会话模型名称或深度`() {
        val model = model()

        assertEquals(
            "custom-model",
            composerModelLabel(
                model = "custom-model",
                reasoningEffort = null,
                models = listOf(model),
                defaultModel = model.model,
                defaultReasoningEffort = "high",
            ),
        )
    }

    @Test
    fun `隐藏模型仍可展示当前会话名称和深度`() {
        val hiddenModel = model().copy(hidden = true)

        assertEquals(
            "GPT-5 · high",
            composerModelLabel(
                model = null,
                reasoningEffort = null,
                models = listOf(hiddenModel),
                defaultModel = hiddenModel.model,
                defaultReasoningEffort = "high",
            ),
        )
    }

    private fun model(reasoningEfforts: List<OptionValue> = emptyList()) = ModelOption(
        id = "gpt-5",
        model = "gpt-5",
        displayName = "GPT-5",
        description = "",
        isDefault = true,
        hidden = false,
        inputModalities = listOf("text"),
        reasoningEfforts = reasoningEfforts,
        defaultReasoningEffort = "medium",
        serviceTiers = emptyList(),
        defaultServiceTier = null,
    )
}
