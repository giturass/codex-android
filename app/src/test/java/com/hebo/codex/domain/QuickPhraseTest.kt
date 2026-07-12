package com.hebo.codex.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickPhraseTest {
    @Test
    fun `快捷短语仅生成独立文本输入`() {
        assertEquals(
            listOf(UserInputPart.Text("快捷短语")),
            quickPhraseInput("快捷短语"),
        )
    }

    @Test
    fun `快捷短语保留全部字符`() {
        assertEquals(
            listOf(UserInputPart.Text(" 短语\n")),
            quickPhraseInput(" 短语\n"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `快捷短语不能为空`() {
        quickPhraseInput(" \n")
    }
}
