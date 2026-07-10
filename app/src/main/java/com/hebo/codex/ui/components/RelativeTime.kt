package com.hebo.codex.ui.components

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun relativeTime(
    epochSeconds: Long,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val instant = Instant.ofEpochSecond(epochSeconds)
    val seconds = (now.epochSecond - instant.epochSecond).coerceAtLeast(0)
    val date = instant.atZone(zoneId).toLocalDate()
    val today = now.atZone(zoneId).toLocalDate()
    return when {
        seconds < 60 -> "刚刚"
        seconds < 3_600 -> "${seconds / 60} 分钟前"
        date == today -> "${seconds / 3_600} 小时前"
        date == today.minusDays(1) -> "昨天"
        else -> DateTimeFormatter.ofPattern("M月d日").withZone(zoneId).format(instant)
    }
}
