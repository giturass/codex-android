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

    private fun model(reasoningEfforts: List<OptionValue> = emptyList()) = ModelOption(
        id = "gpt-5",
        model = "gpt-5",
        displayName = "GPT-5",
        description = "",
        isDefault = true,
        hidden = false,
        supportsPersonality = true,
        inputModalities = listOf("text"),
        reasoningEfforts = reasoningEfforts,
        defaultReasoningEffort = "medium",
        serviceTiers = emptyList(),
        defaultServiceTier = null,
    )
}
