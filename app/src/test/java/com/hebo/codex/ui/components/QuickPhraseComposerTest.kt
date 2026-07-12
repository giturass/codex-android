package com.hebo.codex.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickPhraseComposerTest {
    @Test
    fun `快捷短语替换全部已有内容`() {
        val value = TextFieldValue("前后", selection = TextRange(1))

        val result = replaceWithQuickPhrase(value, "快捷短语")

        assertEquals("快捷短语", result.text)
        assertEquals(TextRange(4), result.selection)
    }

    @Test
    fun `快捷短语替换选区之外的内容`() {
        val value = TextFieldValue("替换这段文字", selection = TextRange(2, 4))

        val result = replaceWithQuickPhrase(value, "内容")

        assertEquals("内容", result.text)
        assertEquals(TextRange(2), result.selection)
    }

    @Test
    fun `保留短语本身包含的所有字符`() {
        val value = TextFieldValue("前后", selection = TextRange(1))

        val result = replaceWithQuickPhrase(value, " 短语\n")

        assertEquals(" 短语\n", result.text)
    }
}
