package com.hebo.codex.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickPhraseComposerTest {
    @Test
    fun `在光标处原样插入快捷短语`() {
        val value = TextFieldValue("前后", selection = TextRange(1))

        val result = insertQuickPhrase(value, "快捷短语")

        assertEquals("前快捷短语后", result.text)
        assertEquals(TextRange(5), result.selection)
    }

    @Test
    fun `用快捷短语原样替换选区`() {
        val value = TextFieldValue("替换这段文字", selection = TextRange(2, 4))

        val result = insertQuickPhrase(value, "内容")

        assertEquals("替换内容文字", result.text)
        assertEquals(TextRange(4), result.selection)
    }

    @Test
    fun `插入时不添加空格或换行`() {
        val value = TextFieldValue("已有内容", selection = TextRange(4))

        val result = insertQuickPhrase(value, "继续处理")

        assertEquals("已有内容继续处理", result.text)
    }

    @Test
    fun `保留短语本身包含的所有字符`() {
        val value = TextFieldValue("前后", selection = TextRange(1))

        val result = insertQuickPhrase(value, " 短语\n")

        assertEquals("前 短语\n后", result.text)
    }
}
