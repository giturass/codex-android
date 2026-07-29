package com.hebo.codex.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun <T> OptionChipRow(
    values: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        values.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelect(item) },
                label = { Text(label(item)) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

@Composable
fun SettingDescription(text: String, critical: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (critical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
