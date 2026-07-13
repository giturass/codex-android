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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.hebo.codex.ui.components.AnchoredDetailsPopup
import com.hebo.codex.ui.components.CompactDailyTokenUsageChart
import com.hebo.codex.ui.components.DAILY_TOKEN_USAGE_DAY_COUNT
import com.hebo.codex.ui.components.RemainingLinearProgressIndicator
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
private val DetailsPopupWidth = 300.dp
private const val FiveHourWindowMinutes = 5L * 60L
private const val WeeklyWindowMinutes = 7L * 24L * 60L
private const val MonthlyWindowMinutes = 30L * 24L * 60L
private const val WindowDurationTolerance = 0.05
private const val WarningRemainingPercent = 25.0
private const val CriticalRemainingPercent = 10.0
private const val StaleAfterSeconds = 15L * 60L

internal data class PresentedRateLimitWindow(
    val window: RateLimitWindow,
    val label: String,
    val indicatorLabel: String?,
)

internal data class CodexRateLimitWindows(
    val limit: AccountRateLimit,
    val featured: PresentedRateLimitWindow,
    val all: List<PresentedRateLimitWindow>,
)

internal enum class RateLimitEmphasis {
    NORMAL,
    WARNING,
    CRITICAL,
}

@Composable
fun RateLimitIndicator(
    state: RateLimitsUiState,
    onRefresh: () -> Unit,
) {
    val windows = state.rateLimits?.codexWindows() ?: return
    var showDetails by remember { mutableStateOf(false) }
    val featured = windows.featured
    val featuredRemaining = featured.window.remainingPercent()
    val featuredColor = rateLimitWindowColor(featured)
    val description = "${featured.label}，剩余 ${formatPercent(featuredRemaining)}"

    Box {
        IconButton(
            onClick = { showDetails = true },
            modifier = Modifier
                .size(IndicatorTouchSize)
                .semantics { contentDescription = description },
        ) {
            RateLimitRing(
                remaining = featuredRemaining,
                color = featuredColor,
                indicatorLabel = featured.indicatorLabel,
            )
        }

        if (showDetails) {
            RateLimitDetailsPopup(
                state = state,
                windows = windows,
                onRefresh = onRefresh,
                onDismiss = { showDetails = false },
            )
        }
    }
}

@Composable
private fun RateLimitRing(
    remaining: Double,
    color: Color,
    indicatorLabel: String?,
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
                progress = remaining / 100.0,
                color = color,
                trackColor = trackColor,
                inset = outerInset,
                strokeWidth = strokeWidth,
            )
        }
        if (indicatorLabel == null) {
            Text(
                text = remaining.roundToInt().toString(),
                modifier = Modifier.clearAndSetSemantics {},
                color = color,
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        } else {
            Column(
                modifier = Modifier.clearAndSetSemantics {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = remaining.roundToInt().toString(),
                    color = color,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = indicatorLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 8.sp,
                    lineHeight = 8.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
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
private fun RateLimitDetailsPopup(
    state: RateLimitsUiState,
    windows: CodexRateLimitWindows,
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

    AnchoredDetailsPopup(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(DetailsPopupWidth),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            windows.all.forEachIndexed { index, presented ->
                if (index > 0) HorizontalDivider()
                RateLimitWindowDetails(
                    label = presented.label,
                    window = presented.window,
                    color = rateLimitWindowColor(presented),
                    nowEpochSeconds = nowEpochSeconds,
                )
            }
            HorizontalDivider()
            TokenUsageDetails(state)
            state.errorMessage?.let { error ->
                Text(
                    text = "额度刷新失败：${error.summary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.updatedAt?.let { updatedAt ->
                val isStale = nowEpochSeconds - updatedAt >= StaleAfterSeconds
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isStale) {
                            "数据可能已过期 · 更新于 ${relativeTime(updatedAt, Instant.ofEpochSecond(nowEpochSeconds))}"
                        } else {
                            "更新于 ${relativeTime(updatedAt, Instant.ofEpochSecond(nowEpochSeconds))}"
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isStale) MaterialTheme.statusColors.warning
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新额度与 Token 用量")
                        }
                    }
                }
            }
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(label, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("${formatPercent(remaining)} 剩余", style = MaterialTheme.typography.labelLarge)
        }
        RemainingLinearProgressIndicator(
            remainingRatio = (remaining / 100.0).toFloat(),
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
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
private fun TokenUsageDetails(state: RateLimitsUiState) {
    val buckets = state.usage?.dailyBuckets
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Token 用量", style = MaterialTheme.typography.labelLarge)
            Text(
                "最近 $DAILY_TOKEN_USAGE_DAY_COUNT 天",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            buckets == null && state.isRefreshing -> Text(
                "正在加载每日用量",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            buckets == null -> Text(
                "服务端暂未提供每日用量明细",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            buckets.isEmpty() -> Text(
                "暂无每日用量记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> CompactDailyTokenUsageChart(buckets)
        }
        state.usageErrorMessage?.let { error ->
            Text(
                text = "Token 用量刷新失败：${error.summary}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

internal fun rateLimitEmphasis(remainingPercent: Double): RateLimitEmphasis = when {
    remainingPercent <= CriticalRemainingPercent -> RateLimitEmphasis.CRITICAL
    remainingPercent <= WarningRemainingPercent -> RateLimitEmphasis.WARNING
    else -> RateLimitEmphasis.NORMAL
}

@Composable
private fun rateLimitWindowColor(window: PresentedRateLimitWindow): Color =
    when (rateLimitEmphasis(window.window.remainingPercent())) {
        RateLimitEmphasis.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
        RateLimitEmphasis.WARNING -> MaterialTheme.statusColors.warning
        RateLimitEmphasis.CRITICAL -> MaterialTheme.colorScheme.error
    }

internal fun AccountRateLimits.codexWindows(): CodexRateLimitWindows? {
    val limit = limits.singleOrNull { it.id == "codex" } ?: return null
    val windows = listOfNotNull(
        limit.primary?.toPresentedRateLimitWindow(fallbackLabel = "主限额"),
        limit.secondary?.toPresentedRateLimitWindow(fallbackLabel = "次限额"),
    ).sortedBy { it.window.windowDurationMinutes ?: Long.MAX_VALUE }
    return CodexRateLimitWindows(
        limit = limit,
        featured = windows.firstOrNull() ?: return null,
        all = windows,
    )
}

internal fun RateLimitWindow.remainingPercent(): Double =
    (100.0 - usedPercent).coerceIn(0.0, 100.0)

private fun RateLimitWindow.hasDuration(expectedMinutes: Long): Boolean {
    val actualMinutes = windowDurationMinutes ?: return false
    return abs(actualMinutes - expectedMinutes).toDouble() <= expectedMinutes * WindowDurationTolerance
}

private fun RateLimitWindow.toPresentedRateLimitWindow(
    fallbackLabel: String,
): PresentedRateLimitWindow {
    val isWeekly = hasDuration(WeeklyWindowMinutes)
    return PresentedRateLimitWindow(
        window = this,
        label = when {
            hasDuration(FiveHourWindowMinutes) -> "5 小时"
            isWeekly -> "周限额"
            hasDuration(MonthlyWindowMinutes) -> "月限额"
            else -> windowDurationMinutes?.toRateLimitLabel() ?: fallbackLabel
        },
        indicatorLabel = if (isWeekly) "周" else null,
    )
}

private fun Long.toRateLimitLabel(): String = when {
    this % (24L * 60L) == 0L -> "${this / (24L * 60L)} 天限额"
    this % 60L == 0L -> "${this / 60L} 小时限额"
    else -> "$this 分钟限额"
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
