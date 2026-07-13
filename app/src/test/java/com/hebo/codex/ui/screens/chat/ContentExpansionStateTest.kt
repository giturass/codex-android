package com.hebo.codex.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentExpansionStateTest {
    @Test
    fun `展开时先纳入正文布局再显示正文`() {
        val expanding = ContentExpansionPhase.COLLAPSED.startTransition(expanded = true)

        assertEquals(ContentExpansionPhase.EXPANDING, expanding)
        assertTrue(expanding.bodyIncluded)
        assertFalse(expanding.bodyVisible)

        val expanded = expanding.settle()

        assertEquals(ContentExpansionPhase.EXPANDED, expanded)
        assertTrue(expanded.bodyIncluded)
        assertTrue(expanded.bodyVisible)
    }

    @Test
    fun `收起时先隐藏正文再移出正文布局`() {
        val collapsing = ContentExpansionPhase.EXPANDED.startTransition(expanded = false)

        assertEquals(ContentExpansionPhase.COLLAPSING, collapsing)
        assertTrue(collapsing.bodyIncluded)
        assertFalse(collapsing.bodyVisible)

        val collapsed = collapsing.settle()

        assertEquals(ContentExpansionPhase.COLLAPSED, collapsed)
        assertFalse(collapsed.bodyIncluded)
        assertFalse(collapsed.bodyVisible)
    }
}
