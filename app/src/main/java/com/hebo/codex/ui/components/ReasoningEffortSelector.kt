package com.hebo.codex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.ModelOption

@Composable
fun ReasoningEffortSelector(
    model: ModelOption,
    selectedEffort: String?,
    defaultEffort: String?,
    defaultLabel: String,
    includeDefault: Boolean = true,
    onSelect: (String?) -> Unit,
) {
    val efforts = model.reasoningEfforts

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("思考深度", style = MaterialTheme.typography.labelLarge)
        if (efforts.isEmpty()) {
            SettingDescription("此模型未提供思考深度选项")
        } else {
            OptionChipRow(
                values = (if (includeDefault) listOf<String?>(null) else emptyList()) + efforts.map { it.value },
                selected = selectedEffort,
                label = { effort ->
                    effort ?: defaultEffort?.let { "$defaultLabel（$it）" } ?: defaultLabel
                },
                onSelect = onSelect,
            )
        }
        val effectiveEffort = selectedEffort ?: defaultEffort.takeIf { includeDefault }
        efforts.firstOrNull { it.value == effectiveEffort }
            ?.description
            ?.takeIf(String::isNotBlank)
            ?.let { SettingDescription(it) }
    }
}
