package com.hebo.codex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

private val PopupGap = 4.dp
private val PopupWindowMargin = 8.dp

@Composable
internal fun AnchoredDetailsPopup(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        AnchoredDetailsPopupPositionProvider(
            gapPx = with(density) { PopupGap.roundToPx() },
            windowMarginPx = with(density) { PopupWindowMargin.roundToPx() },
        )
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            content = content,
        )
    }
}

private class AnchoredDetailsPopupPositionProvider(
    private val gapPx: Int,
    private val windowMarginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchoredDetailsPopupHorizontalPosition(
            windowWidth = windowSize.width,
            popupWidth = popupContentSize.width,
            layoutDirection = layoutDirection,
            windowMarginPx = windowMarginPx,
        )
        val belowY = anchorBounds.bottom + gapPx
        val maxY = windowSize.height - popupContentSize.height - windowMarginPx
        val y = if (belowY <= maxY) {
            belowY
        } else {
            (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(windowMarginPx)
        }
        return IntOffset(x, y)
    }
}

internal fun anchoredDetailsPopupHorizontalPosition(
    windowWidth: Int,
    popupWidth: Int,
    layoutDirection: LayoutDirection,
    windowMarginPx: Int,
): Int = when (layoutDirection) {
    LayoutDirection.Ltr ->
        (windowWidth - popupWidth - windowMarginPx).coerceAtLeast(windowMarginPx)
    LayoutDirection.Rtl -> windowMarginPx
}
