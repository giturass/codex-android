package com.hebo.codex.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hebo.codex.ui.theme.ActionBlue

private val ComposerCompactCornerRadius = 30.dp
private val ComposerExpandedCornerRadius = 22.dp
private val ComposerCompactControlInset = 56.dp
private val ComposerExpandedTextInset = 12.dp
private val ComposerExpandedActionsHeight = 54.dp
private val ComposerTextFieldHeight = 48.dp
private val ComposerExpandedTextFieldMaxHeight = 140.dp

internal enum class MessageComposerAction {
    SEND,
    STOP,
}

@Composable
internal fun MessageComposer(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    leadingContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    compactEndExtra: Dp = 0.dp,
) {
    val isExpanded = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) ComposerExpandedCornerRadius else ComposerCompactCornerRadius,
        label = "输入框圆角",
    )
    val containerColor by animateColorAsState(
        targetValue = if (isExpanded) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "输入框背景",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isExpanded) {
            MaterialTheme.colorScheme.outlineVariant
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
        },
        label = "输入框边框",
    )

    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(if (isExpanded) Alignment.BottomStart else Alignment.CenterStart)
                    .size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                leadingContent()
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .padding(
                        start = if (isExpanded) ComposerExpandedTextInset else ComposerCompactControlInset,
                        top = if (isExpanded) 6.dp else 0.dp,
                        end = if (isExpanded) {
                            ComposerExpandedTextInset
                        } else {
                            ComposerCompactControlInset + compactEndExtra
                        },
                        bottom = if (isExpanded) ComposerExpandedActionsHeight else 0.dp,
                    )
                    .heightIn(
                        min = ComposerTextFieldHeight,
                        max = if (isExpanded) {
                            ComposerExpandedTextFieldMaxHeight
                        } else {
                            ComposerTextFieldHeight
                        },
                    ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                minLines = 1,
                maxLines = if (isExpanded) 5 else 1,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = if (isExpanded) Alignment.TopStart else Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Row(
                modifier = Modifier.align(
                    if (isExpanded) Alignment.BottomEnd else Alignment.CenterEnd,
                ),
                verticalAlignment = Alignment.CenterVertically,
                content = trailingContent,
            )
        }
    }
}

@Composable
internal fun MessageComposerAddButton(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(Icons.Rounded.Add, contentDescription = contentDescription)
    }
}

@Composable
internal fun MessageComposerActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    action: MessageComposerAction,
    isLoading: Boolean,
    contentDescription: String,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = ActionBlue,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                Modifier.size(19.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )

            action == MessageComposerAction.STOP -> Icon(
                Icons.Rounded.Stop,
                contentDescription = contentDescription,
            )

            else -> Icon(
                Icons.Rounded.ArrowUpward,
                contentDescription = contentDescription,
            )
        }
    }
}
