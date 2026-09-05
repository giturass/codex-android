package com.hebo.codex.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.R

@Composable
fun UserFacingErrorDialog(error: UserFacingError, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(error.summary) },
        text = {
            error.technicalDetails?.let { TechnicalErrorText(it, Modifier.verticalScroll(rememberScrollState())) }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) } },
    )
}

@Composable
fun ExpandableErrorDetails(error: UserFacingError, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(error.technicalDetails) { mutableStateOf(false) }
    Column(modifier) {
        Text(error.summary)
        error.technicalDetails?.let { details ->
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    stringResource(
                        if (expanded) R.string.common_hide_technical_details
                        else R.string.common_show_technical_details,
                    ),
                )
            }
            if (expanded) TechnicalErrorText(details)
        }
    }
}

@Composable
private fun TechnicalErrorText(value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        SelectionContainer {
            Text(
                value,
                modifier = Modifier.padding(10.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
