package com.hebo.codex.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.offset
import com.hebo.codex.ui.theme.ActionBlue
import kotlin.math.roundToInt

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
) {
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeAnimationSource = WindowInsets.imeAnimationSource
    val imeAnimationTarget = WindowInsets.imeAnimationTarget
    val imeAnimation = remember(imeInsets, imeAnimationSource, imeAnimationTarget) {
        ComposerImeAnimation(imeInsets, imeAnimationSource, imeAnimationTarget)
    }
    val hasText = value.text.isNotEmpty()
    val allowsMultipleLines by remember(density, imeAnimation, hasText) {
        derivedStateOf { hasText || imeAnimation.isVisibleOrOpening(density) }
    }
    val showsExpandedContent by remember(density, imeAnimation, hasText) {
        derivedStateOf { imeAnimation.showsExpandedContent(density, hasText) }
    }
    val compactContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val expandedContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val compactBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    val expandedBorderColor = MaterialTheme.colorScheme.outlineVariant

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        // Insets 在布局前更新；在布局与绘制阶段读取，才能与 imePadding 保持同一帧
        Box(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val fraction = imeAnimation.expansionFraction(this, hasText)
                    this.shadowElevation = shadowElevation.toPx()
                    shape = RoundedCornerShape(
                        lerpDp(ComposerCompactCornerRadius, ComposerExpandedCornerRadius, fraction),
                    )
                    clip = true
                }
                .drawWithContent {
                    val fraction = imeAnimation.expansionFraction(this, hasText)
                    val cornerRadius = lerpDp(
                        ComposerCompactCornerRadius,
                        ComposerExpandedCornerRadius,
                        fraction,
                    ).toPx()
                    drawRoundRect(
                        color = lerpColor(compactContainerColor, expandedContainerColor, fraction),
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
                        imeAnimation = imeAnimation,
                        compactEndExtra = compactEndExtra,
                        hasText = hasText,
                        hasLeadingContent = leadingContent != null,
                    ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                minLines = 1,
                maxLines = if (allowsMultipleLines) 5 else 1,
                decorationBox = { innerTextField ->
                    ComposerTextFieldDecoration(
                        value = value.text,
                        placeholder = placeholder,
                        imeAnimation = imeAnimation,
                        hasText = hasText,
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
    imeAnimation: ComposerImeAnimation,
    compactEndExtra: Dp,
    hasText: Boolean,
    hasLeadingContent: Boolean,
) = layout { measurable, constraints ->
    val fraction = imeAnimation.expansionFraction(this, hasText)
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
    imeAnimation: ComposerImeAnimation,
    hasText: Boolean,
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
        val fraction = imeAnimation.expansionFraction(this, hasText)
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

private class ComposerImeAnimation(
    private val current: WindowInsets,
    private val source: WindowInsets,
    private val target: WindowInsets,
) {
    fun expansionFraction(density: Density, hasText: Boolean): Float = composerExpansionFraction(
        currentBottom = current.getBottom(density),
        sourceBottom = source.getBottom(density),
        targetBottom = target.getBottom(density),
        hasText = hasText,
    )

    fun isVisibleOrOpening(density: Density): Boolean =
        current.getBottom(density) > 0 || target.getBottom(density) > 0

    fun showsExpandedContent(density: Density, hasText: Boolean): Boolean =
        composerExpandedContentVisible(
            currentBottom = current.getBottom(density),
            sourceBottom = source.getBottom(density),
            targetBottom = target.getBottom(density),
            hasText = hasText,
        )
}

internal fun composerExpansionFraction(
    currentBottom: Int,
    sourceBottom: Int,
    targetBottom: Int,
    hasText: Boolean,
): Float {
    if (hasText) return 1f
    if (sourceBottom > 0 && targetBottom > 0) return 1f
    val expandedBottom = maxOf(currentBottom, sourceBottom, targetBottom)
    return if (expandedBottom == 0) {
        0f
    } else {
        (currentBottom.toFloat() / expandedBottom).coerceIn(0f, 1f)
    }
}

internal fun composerExpandedContentVisible(
    currentBottom: Int,
    sourceBottom: Int,
    targetBottom: Int,
    hasText: Boolean,
): Boolean {
    if (hasText) return true
    if (targetBottom == 0) return false
    return composerExpansionFraction(currentBottom, sourceBottom, targetBottom, hasText) == 1f
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
