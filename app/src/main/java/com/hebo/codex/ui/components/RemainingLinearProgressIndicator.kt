@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.components

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun RemainingLinearProgressIndicator(
    remainingRatio: Float,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
) {
    LinearProgressIndicator(
        progress = { remainingRatio.coerceIn(0f, 1f) },
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
