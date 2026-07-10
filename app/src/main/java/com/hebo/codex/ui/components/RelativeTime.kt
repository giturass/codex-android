package com.hebo.codex.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun relativeTime(
    epochSeconds: Long,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val instant = Instant.ofEpochSecond(epochSeconds)
    val seconds = (now.epochSecond - instant.epochSecond).coerceAtLeast(0)
    val date = LocalDate.ofInstant(instant, zoneId)
    val today = LocalDate.ofInstant(now, zoneId)
    return when {
        seconds < 60 -> "刚刚"
        seconds < 3_600 -> "${seconds / 60} 分钟前"
        date == today -> "${seconds / 3_600} 小时前"
        date == today.minusDays(1) -> "昨天"
        else -> DateTimeFormatter.ofPattern("M月d日").withZone(zoneId).format(instant)
    }
}
