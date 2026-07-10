package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.UserQuestion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestAnswersTest {
    private val questions = listOf(
        UserQuestion("scope", "范围", "选择范围", emptyList(), allowsOther = true, isSecret = false),
        UserQuestion("mode", "模式", "选择模式", emptyList(), allowsOther = true, isSecret = false),
    )

    @Test
    fun `所有问题都有非空答案时才允许提交`() {
        assertFalse(allQuestionsAnswered(questions, emptyMap()))
        assertFalse(allQuestionsAnswered(questions, mapOf("scope" to listOf("项目"))))
        assertFalse(
            allQuestionsAnswered(
                questions,
                mapOf("scope" to listOf("项目"), "mode" to listOf("   ")),
            ),
        )
        assertTrue(
            allQuestionsAnswered(
                questions,
                mapOf("scope" to listOf("项目"), "mode" to listOf("严格")),
            ),
        )
    }
}
