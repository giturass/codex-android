package com.hebo.codex.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanExpansionStateTest {
    @Test
    fun `没有计划时不允许展开`() {
        assertFalse(planExpansionTarget(expanded = true, canExpand = false))
    }

    @Test
    fun `有计划且请求展开时允许展开`() {
        assertTrue(planExpansionTarget(expanded = true, canExpand = true))
    }

    @Test
    fun `展开时先纳入正文布局再显示正文`() {
        val expanding = PlanExpansionPhase.COLLAPSED.startTransition(expanded = true)

        assertEquals(PlanExpansionPhase.EXPANDING, expanding)
        assertTrue(expanding.bodyIncluded)
        assertFalse(expanding.bodyVisible)

        val expanded = expanding.settle()

        assertEquals(PlanExpansionPhase.EXPANDED, expanded)
        assertTrue(expanded.bodyIncluded)
        assertTrue(expanded.bodyVisible)
    }

    @Test
    fun `收起时先隐藏正文再移出正文布局`() {
        val collapsing = PlanExpansionPhase.EXPANDED.startTransition(expanded = false)

        assertEquals(PlanExpansionPhase.COLLAPSING, collapsing)
        assertTrue(collapsing.bodyIncluded)
        assertFalse(collapsing.bodyVisible)

        val collapsed = collapsing.settle()

        assertEquals(PlanExpansionPhase.COLLAPSED, collapsed)
        assertFalse(collapsed.bodyIncluded)
        assertFalse(collapsed.bodyVisible)
    }
}
