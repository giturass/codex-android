package com.hebo.codex.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.offset
import com.hebo.codex.ui.theme.ActionBlue
import com.hebo.codex.ui.theme.ComposerTheme
import kotlin.math.roundToInt

private val ComposerCompactCornerRadius = 30.dp
private val ComposerExpandedCornerRadius = 22.dp
private val ComposerCompactControlInset = 56.dp
private val ComposerExpandedTextInset = 12.dp
private val ComposerExpandedActionsHeight = 54.dp
private val ComposerTextFieldHeight = 48.dp
private val ComposerExpandedTextFieldMaxHeight = 140.dp
private const val ComposerExpansionDurationMillis = 200
private const val ComposerCollapseDurationMillis = 160
private val ComposerSendColor = Color(0xFF2196F3)

internal enum class MessageComposerAction {
    EXECUTE,
    SEND,
    STOP,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MessageComposer(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    enabled: Boolean,
    leadingContent: (@Composable () -> Unit)? = null,
    expandedContent: @Composable RowScope.() -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    compactEndExtra: Dp = 0.dp,
    shadowElevation: Dp = 2.dp,
    focusRequester: FocusRequester? = null,
) = ComposerTheme {
    val density = LocalDensity.current
    val imeAnimationTarget = WindowInsets.imeAnimationTarget
    val hasText = value.text.isNotEmpty()
    val expansionTarget = composerExpansionTarget(
        imeTargetBottom = imeAnimationTarget.getBottom(density),
        hasText = hasText,
    )
    val expansionTransition = updateTransition(
        targetState = expansionTarget,
        label = "输入框形态",
    )
    val expansionFraction = expansionTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) {
                    ComposerExpansionDurationMillis
                } else {
                    ComposerCollapseDurationMillis
                },
            )
        },
        label = "输入框展开进度",
    ) { expanded ->
        if (expanded) 1f else 0f
    }
    val showsExpandedContent = composerExpandedContentVisible(
        currentExpanded = expansionTransition.currentState,
        targetExpanded = expansionTransition.targetState,
        isTransitionRunning = expansionTransition.isRunning,
    )
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val compactBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    val expandedBorderColor = MaterialTheme.colorScheme.outlineVariant

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        // IME 只决定目标形态，输入框的动画进度由自身 Transition 独立驱动
        Box(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val fraction = expansionFraction.value
                    this.shadowElevation = shadowElevation.toPx()
                    shape = RoundedCornerShape(
                        lerpDp(ComposerCompactCornerRadius, ComposerExpandedCornerRadius, fraction),
                    )
                    clip = true
                }
                .drawWithContent {
                    val fraction = expansionFraction.value
                    val cornerRadius = lerpDp(
                        ComposerCompactCornerRadius,
                        ComposerExpandedCornerRadius,
                        fraction,
                    ).toPx()
                    drawRoundRect(
                        color = containerColor,
                        cornerRadius = CornerRadius(cornerRadius),
                    )
                    drawContent()
                    val strokeWidth = 1.dp.toPx()
                    drawRoundRect(
                        color = lerpColor(compactBorderColor, expandedBorderColor, fraction),
                        topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                        size = Size(
                            width = (size.width - strokeWidth).coerceAtLeast(0f),
                            height = (size.height - strokeWidth).coerceAtLeast(0f),
                        ),
                        cornerRadius = CornerRadius((cornerRadius - strokeWidth / 2f).coerceAtLeast(0f)),
                        style = Stroke(strokeWidth),
                    )
                }
                .padding(horizontal = 6.dp, vertical = 5.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                    .composerTextFieldLayout(
                        expansionFraction = expansionFraction,
                        compactEndExtra = compactEndExtra,
                        hasLeadingContent = leadingContent != null,
                    ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                minLines = 1,
                maxLines = if (expansionTarget) 5 else 1,
                decorationBox = { innerTextField ->
                    ComposerTextFieldDecoration(
                        value = value.text,
                        placeholder = placeholder,
                        expansionFraction = expansionFraction,
                        innerTextField = innerTextField,
                    )
                },
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingContent != null) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        leadingContent()
                    }
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showsExpandedContent) expandedContent()
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    content = trailingContent,
                )
            }
        }
    }
}

private fun Modifier.composerTextFieldLayout(
    expansionFraction: State<Float>,
    compactEndExtra: Dp,
    hasLeadingContent: Boolean,
) = layout { measurable, constraints ->
    val fraction = expansionFraction.value
    val startPadding = lerpDp(
        if (hasLeadingContent) ComposerCompactControlInset else ComposerExpandedTextInset,
        ComposerExpandedTextInset,
        fraction,
    ).roundToPx()
    val endPadding = lerpDp(
        ComposerCompactControlInset + compactEndExtra,
        ComposerExpandedTextInset,
        fraction,
    ).roundToPx()
    val topPadding = lerpDp(0.dp, 6.dp, fraction).roundToPx()
    val bottomPadding = lerpDp(0.dp, ComposerExpandedActionsHeight, fraction).roundToPx()
    val horizontalPadding = startPadding + endPadding
    val verticalPadding = topPadding + bottomPadding
    val contentConstraints = constraints.offset(-horizontalPadding, -verticalPadding)
    val maxTextFieldHeight = lerpDp(
        ComposerTextFieldHeight,
        ComposerExpandedTextFieldMaxHeight,
        fraction,
    ).roundToPx()
        .coerceAtMost(contentConstraints.maxHeight)
        .coerceAtLeast(contentConstraints.minHeight)
    val minTextFieldHeight = lerpDp(ComposerTextFieldHeight, 0.dp, fraction).roundToPx()
        .coerceAtLeast(contentConstraints.minHeight)
        .coerceAtMost(maxTextFieldHeight)
    val placeable = measurable.measure(
        contentConstraints.copy(
            minHeight = minTextFieldHeight,
            maxHeight = maxTextFieldHeight,
        ),
    )
    val width = constraints.constrainWidth(placeable.width + horizontalPadding)
    val height = constraints.constrainHeight(placeable.height + verticalPadding)

    layout(width, height) {
        placeable.placeRelative(startPadding, topPadding)
    }
}

@Composable
private fun ComposerTextFieldDecoration(
    value: String,
    placeholder: String,
    expansionFraction: State<Float>,
    innerTextField: @Composable () -> Unit,
) {
    Layout(
        content = {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            innerTextField()
        },
    ) { measurables, constraints ->
        val fraction = expansionFraction.value
        val contentConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(contentConstraints) }
        val width = constraints.constrainWidth(placeables.maxOfOrNull { it.width } ?: 0)
        val height = constraints.constrainHeight(placeables.maxOfOrNull { it.height } ?: 0)

        layout(width, height) {
            placeables.forEach { placeable ->
                val centeredY = (height - placeable.height).coerceAtLeast(0) / 2f
                val y = (centeredY * (1f - fraction)).roundToInt()
                placeable.placeRelative(0, y)
            }
        }
    }
}

internal fun composerExpansionTarget(
    imeTargetBottom: Int,
    hasText: Boolean,
): Boolean = hasText || imeTargetBottom > 0

internal fun composerExpandedContentVisible(
    currentExpanded: Boolean,
    targetExpanded: Boolean,
    isTransitionRunning: Boolean,
): Boolean = currentExpanded && targetExpanded && !isTransitionRunning

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
            containerColor = if (action == MessageComposerAction.SEND) ComposerSendColor else ActionBlue,
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

            action == MessageComposerAction.EXECUTE -> Icon(
                Icons.Rounded.Terminal,
                contentDescription = contentDescription,
            )

            else -> Icon(
                Icons.Rounded.ArrowUpward,
                contentDescription = contentDescription,
            )
        }
    }
}
