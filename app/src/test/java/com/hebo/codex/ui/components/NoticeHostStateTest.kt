package com.hebo.codex.ui.components

import androidx.compose.runtime.saveable.SaverScope
import com.hebo.codex.domain.UserFacingError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeHostStateTest {
    @Test
    fun `普通反馈关闭后不留下常驻入口`() {
        val state = NoticeHostState()
        state.showMessage("已保存")

        state.dismiss(requireNotNull(state.current))

        assertFalse(state.hasContent)
    }

    @Test
    fun `错误关闭后不留下常驻入口且成功反馈不会恢复旧错误`() {
        val state = NoticeHostState()
        state.showError(UserFacingError("发送失败", "connection closed"))
        state.dismiss(requireNotNull(state.current))

        assertNull(state.current)
        assertFalse(state.hasContent)

        state.showMessage("已复制")
        state.dismiss(requireNotNull(state.current))

        assertNull(state.current)
        assertFalse(state.hasContent)
    }

    @Test
    fun `新提示按顺序显示且旧超时不能关闭重复出现的新错误`() {
        val state = NoticeHostState()
        val error = UserFacingError("操作失败", "timeout")
        state.showError(error)
        val first = requireNotNull(state.current)
        state.showError(error)
        state.showMessage("已保存")
        assertSame(first, state.current)

        state.dismiss(first)
        val second = requireNotNull(state.current)
        assertNotSame(first, second)
        state.dismiss(first)
        assertSame(second, state.current)

        state.dismiss(second)
        assertEquals("已保存", state.current?.message)
        state.dismiss(requireNotNull(state.current))
        assertFalse(state.hasContent)
    }

    @Test
    fun `页面恢复后保留当前提示和队列且关闭后不留下历史入口`() {
        val state = NoticeHostState()
        state.showError(UserFacingError("发送失败", "timeout"))
        state.showMessage("服务端提示", "technical details")
        state.showMessage("已保存")

        val restored = restore(state)

        assertEquals("发送失败", restored.current?.message)
        assertEquals("timeout", restored.current?.technicalDetails)
        assertTrue(requireNotNull(restored.current).isError)
        restored.dismiss(requireNotNull(restored.current))
        assertEquals("服务端提示", restored.current?.message)
        assertEquals("technical details", restored.current?.technicalDetails)
        assertFalse(requireNotNull(restored.current).isError)
        restored.dismiss(requireNotNull(restored.current))
        assertEquals("已保存", restored.current?.message)
        restored.dismiss(requireNotNull(restored.current))
        assertFalse(restored.hasContent)
    }

    @Test
    fun `页面恢复不会重新显示已关闭的错误`() {
        val state = NoticeHostState()
        state.showError(UserFacingError("发送失败", "timeout"))
        state.dismiss(requireNotNull(state.current))

        val restored = restore(state)

        assertNull(restored.current)
        assertFalse(restored.hasContent)
    }

    private fun restore(state: NoticeHostState): NoticeHostState {
        val scope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }
        val saved = with(NoticeHostState.Saver) { scope.save(state) }
        return requireNotNull(NoticeHostState.Saver.restore(requireNotNull(saved)))
    }
}
