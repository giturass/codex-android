package com.hebo.codex.ui.screens.chat

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.hebo.codex.domain.FeatureEntry
import com.hebo.codex.domain.FeatureKind
import com.hebo.codex.domain.UserInputPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerPaletteTest {
    @Test
    fun `识别末尾快捷输入并移除令牌`() {
        val input = "请处理这个问题 ${'$'}des"
        val token = requireNotNull(activeComposerToken(input))

        assertEquals('$', token.trigger)
        assertEquals("des", token.query)
        assertEquals("请处理这个问题", removeActiveComposerToken(input, token))
    }

    @Test
    fun `在光标处插入 Skill 触发符`() {
        val value = TextFieldValue("请处理问题", selection = TextRange(3))

        val result = insertComposerTrigger(value, '$')

        assertEquals("请处理 ${'$'}问题", result.text)
        assertEquals(5, result.selection.start)
        assertEquals("", requireNotNull(activeComposerToken(result.text, result.selection.start)).query)
    }

    @Test
    fun `Skill 和提及按触发符过滤`() {
        val resources = listOf(
            entry(FeatureKind.SKILL, "design"),
            entry(FeatureKind.APP, "docs"),
        )

        val skills = composerPaletteEntries(ComposerToken(0, 4, '$', "des"), resources)
        val mentions = composerPaletteEntries(ComposerToken(0, 4, '@', "doc"), resources)

        assertTrue((skills.single() as ComposerPaletteEntry.Resource).input is UserInputPart.Skill)
        assertTrue((mentions.single() as ComposerPaletteEntry.Resource).input is UserInputPart.Mention)
    }

    @Test
    fun `通过 phrase 命令打开快捷短语`() {
        val entries = composerPaletteEntries(ComposerToken(0, 5, '/', "phra"), emptyList())

        assertEquals(ComposerCommand.PHRASE, (entries.single() as ComposerPaletteEntry.Command).command)
    }

    private fun entry(kind: FeatureKind, title: String) = FeatureEntry(
        kind = kind,
        id = title,
        title = title,
        description = "$title description",
        enabled = true,
        installed = true,
        metadata = mapOf("accessible" to "true"),
        rawJson = "{}",
    )
}
