package com.hebo.codex.ui.components

import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class AnchoredDetailsPopupTest {
    @Test
    fun alignsPopupToWindowEnd() {
        assertEquals(
            684,
            anchoredDetailsPopupHorizontalPosition(
                windowWidth = 1_000,
                popupWidth = 300,
                layoutDirection = LayoutDirection.Ltr,
                windowMarginPx = 16,
            ),
        )
        assertEquals(
            16,
            anchoredDetailsPopupHorizontalPosition(
                windowWidth = 1_000,
                popupWidth = 300,
                layoutDirection = LayoutDirection.Rtl,
                windowMarginPx = 16,
            ),
        )
    }
}
