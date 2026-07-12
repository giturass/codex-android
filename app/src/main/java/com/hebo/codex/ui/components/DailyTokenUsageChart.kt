package com.hebo.codex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.DailyTokenUsage
import com.hebo.codex.ui.formatCompactCount
import com.hebo.codex.ui.formatDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

internal const val DAILY_TOKEN_USAGE_DAY_COUNT = 30

private val chartDateFormatter = DateTimeFormatter.ofPattern("M/d", Locale.CHINA)

@Composable
internal fun DailyTokenUsageChart(
    buckets: List<DailyTokenUsage>,
    endDate: LocalDate = LocalDate.now(),
) {
    val values = remember(buckets, endDate) {
        normalizeDailyTokenUsage(buckets, endDate, DAILY_TOKEN_USAGE_DAY_COUNT)
    }
    var selectedIndex by rememberSaveable(
        values.first().date.toEpochDay(),
        values.last().date.toEpochDay(),
    ) {
        mutableIntStateOf(values.indexOfLast { it.tokens > 0 }.takeIf { it >= 0 } ?: values.lastIndex)
    }
    var detailsExpanded by rememberSaveable(endDate.toEpochDay()) { mutableStateOf(false) }
    val selected = values[selectedIndex.coerceIn(values.indices)]

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatDate(selected.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("${formatCompactCount(selected.tokens)} Token", style = MaterialTheme.typography.labelLarge)
        }
        DailyTokenUsagePlot(
            values = values,
            selectedIndex = selectedIndex,
            onSelected = { selectedIndex = it },
        )
        TextButton(
            onClick = { detailsExpanded = !detailsExpanded },
            modifier = Modifier.align(Alignment.Start),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            Text(if (detailsExpanded) "收起每日明细" else "查看每日明细")
        }
        if (detailsExpanded) {
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                values.asReversed().forEach { value ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(formatDate(value.date), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${formatCompactCount(value.tokens)} Token", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompactDailyTokenUsageChart(
    buckets: List<DailyTokenUsage>,
    endDate: LocalDate = LocalDate.now(),
) {
    val values = remember(buckets, endDate) {
        normalizeDailyTokenUsage(buckets, endDate, DAILY_TOKEN_USAGE_DAY_COUNT)
    }
    var selectedIndex by rememberSaveable(
        values.first().date.toEpochDay(),
        values.last().date.toEpochDay(),
    ) {
        mutableIntStateOf(values.indexOfLast { it.tokens > 0 }.takeIf { it >= 0 } ?: values.lastIndex)
    }
    val selected = values[selectedIndex.coerceIn(values.indices)]

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatDate(selected.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("${formatCompactCount(selected.tokens)} Token", style = MaterialTheme.typography.labelLarge)
        }
        DailyTokenUsagePlot(
            values = values,
            selectedIndex = selectedIndex,
            onSelected = { selectedIndex = it },
            height = 92.dp,
        )
    }
}

@Composable
private fun DailyTokenUsagePlot(
    values: List<DailyTokenUsage>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    height: Dp = 124.dp,
) {
    val maxTokens = values.maxOf(DailyTokenUsage::tokens)
    val axisMaximum = dailyTokenUsageAxisMaximum(maxTokens)
    val primary = MaterialTheme.colorScheme.primary
    val inactiveBar = primary.copy(alpha = 0.42f)
    val selectedColumn = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val selected = values[selectedIndex.coerceIn(values.indices)]
    val chartDescription = remember(selected, values) {
        "最近 $DAILY_TOKEN_USAGE_DAY_COUNT 天每日 Token 用量图表，当前选择${formatDate(selected.date)}，" +
            "${formatCompactCount(selected.tokens)} Token，可点击或滑动查看每日用量"
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.width(44.dp).height(height)) {
            if (axisMaximum > 0) {
                Text(
                    formatCompactCount(axisMaximum),
                    modifier = Modifier.align(Alignment.TopEnd),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (axisMaximum >= 10) {
                    Text(
                        formatCompactCount(axisMaximum / 2),
                        modifier = Modifier.align(Alignment.CenterEnd),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "0",
                modifier = Modifier.align(Alignment.BottomEnd),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .pointerInput(values) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            onSelected(dailyTokenUsageIndexAt(down.position.x, size.width.toFloat(), values.size))
                            val dragStart = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume()
                            }
                            if (dragStart != null) {
                                onSelected(
                                    dailyTokenUsageIndexAt(
                                        dragStart.position.x,
                                        size.width.toFloat(),
                                        values.size,
                                    ),
                                )
                                horizontalDrag(dragStart.id) { change ->
                                    onSelected(
                                        dailyTokenUsageIndexAt(
                                            change.position.x,
                                            size.width.toFloat(),
                                            values.size,
                                        ),
                                    )
                                    change.consume()
                                }
                            }
                        }
                    }
                    .semantics { contentDescription = chartDescription },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cellWidth = size.width / values.size
                    val gridStroke = 1.dp.toPx()
                    val selectedLeft = selectedIndex.coerceIn(values.indices) * cellWidth
                    drawRect(
                        color = selectedColumn,
                        topLeft = Offset(selectedLeft, 0f),
                        size = Size(cellWidth, size.height),
                    )
                    listOf(0f, 0.5f, 1f).forEach { fraction ->
                        val y = ((1f - fraction) * (size.height - gridStroke)) + gridStroke / 2
                        drawLine(
                            color = grid,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = gridStroke,
                        )
                    }
                    if (axisMaximum > 0) {
                        val barWidth = (cellWidth * 0.62f).coerceAtMost(8.dp.toPx())
                        val minimumBarHeight = 1.dp.toPx()
                        values.forEachIndexed { index, value ->
                            if (value.tokens <= 0) return@forEachIndexed
                            val ratio = value.tokens.toDouble() / axisMaximum.toDouble()
                            val barHeight = (size.height * ratio.toFloat()).coerceAtLeast(minimumBarHeight)
                            val radius = minOf(2.dp.toPx(), barWidth / 2, barHeight / 2)
                            drawRoundRect(
                                color = if (index == selectedIndex) primary else inactiveBar,
                                topLeft = Offset(
                                    x = index * cellWidth + (cellWidth - barWidth) / 2,
                                    y = size.height - barHeight,
                                ),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(radius, radius),
                            )
                        }
                    }
                }
                if (axisMaximum == 0L) {
                    Text(
                        "最近 $DAILY_TOKEN_USAGE_DAY_COUNT 天暂无用量",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0, 7, 14, 21, values.lastIndex).forEach { index ->
                    Text(
                        if (index == values.lastIndex) "今天" else chartDateFormatter.format(values[index].date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

internal fun normalizeDailyTokenUsage(
    buckets: List<DailyTokenUsage>,
    endDate: LocalDate,
    dayCount: Int,
): List<DailyTokenUsage> {
    require(dayCount > 0) { "每日 Token 用量统计天数必须大于零" }
    val startDate = endDate.minusDays((dayCount - 1).toLong())
    val totalsByDate = mutableMapOf<LocalDate, Long>()
    buckets.forEach { bucket ->
        if (bucket.date.isBefore(startDate) || bucket.date.isAfter(endDate)) return@forEach
        totalsByDate[bucket.date] = totalsByDate.getOrDefault(bucket.date, 0) + bucket.tokens.coerceAtLeast(0)
    }
    return List(dayCount) { offset ->
        val date = startDate.plusDays(offset.toLong())
        DailyTokenUsage(date = date, tokens = totalsByDate.getOrDefault(date, 0))
    }
}

internal fun dailyTokenUsageAxisMaximum(maxValue: Long): Long {
    if (maxValue <= 0) return 0
    val exponent = floor(log10(maxValue.toDouble()))
    val magnitude = 10.0.pow(exponent)
    val normalized = maxValue / magnitude
    val rounded = when {
        normalized <= 1 -> 1.0
        normalized <= 2 -> 2.0
        normalized <= 5 -> 5.0
        else -> 10.0
    }
    return (rounded * magnitude).roundToLong().coerceAtLeast(maxValue)
}

internal fun dailyTokenUsageIndexAt(positionX: Float, width: Float, itemCount: Int): Int {
    require(itemCount > 0) { "每日 Token 用量图表数据不能为空" }
    if (width <= 0) return 0
    return ((positionX.coerceIn(0f, width) / width) * itemCount)
        .toInt()
        .coerceIn(0, itemCount - 1)
}
