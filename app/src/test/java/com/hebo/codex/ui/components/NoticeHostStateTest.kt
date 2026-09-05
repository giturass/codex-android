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
    fun `错误收起后详情仍可查看且不被普通成功反馈清除`() {
        val state = NoticeHostState()
        state.showError(UserFacingError("发送失败", "connection closed"))
        val error = requireNotNull(state.current)
        state.dismiss(error)
        state.showMessage("已复制")
        state.dismiss(requireNotNull(state.current))

        assertNull(state.current)
        assertTrue(state.hasContent)
        assertSame(error, state.recent)
        assertEquals("connection closed", state.recent?.technicalDetails)
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
        assertSame(second, state.recent)
    }

    @Test
    fun `页面恢复后保留当前提示待显示提示和最近错误`() {
        val state = NoticeHostState()
        state.showError(UserFacingError("发送失败", "timeout"))
        state.dismiss(requireNotNull(state.current))
        state.showMessage("已复制")
        state.showMessage("已保存")

        val scope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }
        val saved = with(NoticeHostState.Saver) { scope.save(state) }
        val restored = requireNotNull(NoticeHostState.Saver.restore(requireNotNull(saved)))

        assertEquals("已复制", restored.current?.message)
        restored.dismiss(requireNotNull(restored.current))
        assertEquals("已保存", restored.current?.message)
        restored.dismiss(requireNotNull(restored.current))
        assertEquals("发送失败", restored.recent?.message)
        assertEquals("timeout", restored.recent?.technicalDetails)
        assertTrue(requireNotNull(restored.recent).isError)
    }
}
