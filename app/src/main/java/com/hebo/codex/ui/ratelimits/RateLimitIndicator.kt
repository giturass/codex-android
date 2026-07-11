@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.ratelimits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hebo.codex.domain.AccountRateLimit
import com.hebo.codex.domain.AccountRateLimits
import com.hebo.codex.domain.RateLimitWindow
import com.hebo.codex.ui.components.relativeTime
import com.hebo.codex.ui.formatDateTime
import com.hebo.codex.ui.formatPercent
import com.hebo.codex.ui.theme.statusColors
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

private val IndicatorTouchSize = 48.dp
private val IndicatorVisualSize = 36.dp
private val RingStrokeWidth = 3.dp
private val InnerRingInset = 7.dp
private const val FiveHourWindowMinutes = 5L * 60L
private const val WeeklyWindowMinutes = 7L * 24L * 60L
private const val WindowDurationTolerance = 0.05
private const val WarningRemainingPercent = 25.0
private const val CriticalRemainingPercent = 10.0
private const val StaleAfterSeconds = 15L * 60L

internal data class CodexRateLimitWindows(
    val limit: AccountRateLimit,
    val fiveHour: RateLimitWindow,
    val weekly: RateLimitWindow?,
)

@Composable
fun RateLimitIndicator(
    state: RateLimitsUiState,
    onRefresh: () -> Unit,
) {
    val windows = state.rateLimits?.codexWindows() ?: return
    var showDetails by remember { mutableStateOf(false) }
    val fiveHourRemaining = windows.fiveHour.remainingPercent()
    val weeklyRemaining = windows.weekly?.remainingPercent()
    val fiveHourColor = rateLimitColor(fiveHourRemaining, MaterialTheme.colorScheme.primary)
    val weeklyColor = weeklyRemaining?.let {
        rateLimitColor(it, MaterialTheme.statusColors.info)
    }
    val description = buildString {
        append("5 小时额度剩余 ${formatPercent(fiveHourRemaining)}")
        weeklyRemaining?.let { append("，周额度剩余 ${formatPercent(it)}") }
    }

    IconButton(
        onClick = { showDetails = true },
        modifier = Modifier
            .size(IndicatorTouchSize)
            .semantics { contentDescription = description },
    ) {
        RateLimitRings(
            fiveHourRemaining = fiveHourRemaining,
            weeklyRemaining = weeklyRemaining,
            fiveHourColor = fiveHourColor,
            weeklyColor = weeklyColor,
        )
    }

    if (showDetails) {
        RateLimitDetailsSheet(
            state = state,
            windows = windows,
            fiveHourColor = fiveHourColor,
            weeklyColor = weeklyColor,
            onRefresh = onRefresh,
            onDismiss = { showDetails = false },
        )
    }
}

@Composable
private fun RateLimitRings(
    fiveHourRemaining: Double,
    weeklyRemaining: Double?,
    fiveHourColor: Color,
    weeklyColor: Color?,
) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Box(
        modifier = Modifier.size(IndicatorVisualSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(IndicatorVisualSize)) {
            val strokeWidth = RingStrokeWidth.toPx()
            val outerInset = strokeWidth / 2f
            drawRing(
                progress = fiveHourRemaining / 100.0,
                color = fiveHourColor,
                trackColor = trackColor,
                inset = outerInset,
                strokeWidth = strokeWidth,
            )
            if (weeklyRemaining != null && weeklyColor != null) {
                drawRing(
                    progress = weeklyRemaining / 100.0,
                    color = weeklyColor,
                    trackColor = trackColor,
                    inset = InnerRingInset.toPx(),
                    strokeWidth = strokeWidth,
                )
            }
        }
        Text(
            text = fiveHourRemaining.roundToInt().toString(),
            modifier = Modifier.clearAndSetSemantics {},
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 8.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRing(
    progress: Double,
    color: Color,
    trackColor: Color,
    inset: Float,
    strokeWidth: Float,
) {
    val diameter = (size.minDimension - inset * 2f).coerceAtLeast(0f)
    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
    val ringSize = Size(diameter, diameter)
    val style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    drawArc(trackColor, -90f, 360f, false, topLeft, ringSize, style = style)
    drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = (progress.coerceIn(0.0, 1.0) * 360.0).toFloat(),
        useCenter = false,
        topLeft = topLeft,
        size = ringSize,
        style = style,
    )
}

@Composable
private fun RateLimitDetailsSheet(
    state: RateLimitsUiState,
    windows: CodexRateLimitWindows,
    fiveHourColor: Color,
    weeklyColor: Color?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    var nowEpochSeconds by remember { mutableLongStateOf(Instant.now().epochSecond) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowEpochSeconds = Instant.now().epochSecond
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("额度", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新额度")
                    }
                }
            }
            RateLimitWindowDetails(
                label = "5 小时限额",
                window = windows.fiveHour,
                color = fiveHourColor,
                nowEpochSeconds = nowEpochSeconds,
            )
            windows.weekly?.let { weekly ->
                HorizontalDivider()
                RateLimitWindowDetails(
                    label = "周限额",
                    window = weekly,
                    color = requireNotNull(weeklyColor),
                    nowEpochSeconds = nowEpochSeconds,
                )
            }
            state.errorMessage?.let { error ->
                Text(
                    text = "刷新失败：${error.summary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.updatedAt?.let { updatedAt ->
                val isStale = nowEpochSeconds - updatedAt >= StaleAfterSeconds
                Text(
                    text = if (isStale) {
                        "数据可能已过期 · 更新于 ${relativeTime(updatedAt, Instant.ofEpochSecond(nowEpochSeconds))}"
                    } else {
                        "更新于 ${relativeTime(updatedAt, Instant.ofEpochSecond(nowEpochSeconds))}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isStale) MaterialTheme.statusColors.warning
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun RateLimitWindowDetails(
    label: String,
    window: RateLimitWindow,
    color: Color,
    nowEpochSeconds: Long,
) {
    val remaining = window.remainingPercent()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(label, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("${formatPercent(remaining)} 剩余", style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(
            progress = { (remaining / 100.0).toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = color.copy(alpha = 0.14f),
        )
        Text(
            text = window.resetsAt?.let { resetAt ->
                "${resetCountdownLabel(resetAt, nowEpochSeconds)} · ${formatDateTime(resetAt)}"
            } ?: "暂未提供重置时间",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun rateLimitColor(remainingPercent: Double, normalColor: Color): Color = when {
    remainingPercent <= CriticalRemainingPercent -> MaterialTheme.colorScheme.error
    remainingPercent <= WarningRemainingPercent -> MaterialTheme.statusColors.warning
    else -> normalColor
}

internal fun AccountRateLimits.codexWindows(): CodexRateLimitWindows? {
    val limit = limits.singleOrNull { it.id == "codex" } ?: return null
    val windows = listOfNotNull(limit.primary, limit.secondary)
    val fiveHour = windows.singleOrNull { it.hasDuration(FiveHourWindowMinutes) } ?: return null
    val weekly = windows.singleOrNull { it.hasDuration(WeeklyWindowMinutes) }
    return CodexRateLimitWindows(limit, fiveHour, weekly)
}

internal fun RateLimitWindow.remainingPercent(): Double =
    (100.0 - usedPercent).coerceIn(0.0, 100.0)

private fun RateLimitWindow.hasDuration(expectedMinutes: Long): Boolean {
    val actualMinutes = windowDurationMinutes ?: return false
    return abs(actualMinutes - expectedMinutes).toDouble() <= expectedMinutes * WindowDurationTolerance
}

internal fun resetCountdownLabel(resetAt: Long, nowEpochSeconds: Long): String {
    val remainingSeconds = resetAt - nowEpochSeconds
    if (remainingSeconds <= 0L) return "等待额度更新"
    if (remainingSeconds < 60L) return "不到 1 分钟后重置"

    val totalMinutes = (remainingSeconds + 59L) / 60L
    val days = totalMinutes / (24L * 60L)
    val hours = totalMinutes % (24L * 60L) / 60L
    val minutes = totalMinutes % 60L
    return when {
        days > 0L -> if (hours > 0L) "$days 天 $hours 小时后重置" else "$days 天后重置"
        hours > 0L -> if (minutes > 0L) "$hours 小时 $minutes 分钟后重置" else "$hours 小时后重置"
        else -> "$minutes 分钟后重置"
    }
}
