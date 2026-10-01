@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.hebo.codex.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Webhook
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.ActivityKind
import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.AgentMessagePhase
import com.hebo.codex.domain.ChangedFile
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.ConversationItemKey
import com.hebo.codex.domain.FileChangeKind
import com.hebo.codex.domain.PendingServerRequest
import com.hebo.codex.domain.TurnDetailsState
import kotlinx.coroutines.launch

@Composable
internal fun TurnActivityCard(onOpenDetails: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onOpenDetails)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "查看处理过程",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ActivityGroupCard(
    items: List<ConversationItem>,
    isSealed: Boolean,
    activeItemKeys: Set<ConversationItemKey>,
    onOpenDetails: () -> Unit,
) {
    if (items.isEmpty()) return
    val singleItem = items.singleOrNull()
    val canOpenDetails = singleItem == null || activityItemHasDetails(singleItem)
    val status = singleItem?.activityStatus(activeItemKeys)
        ?: activityGroupStatus(items, isSealed, activeItemKeys)
    val isSealedGroup = singleItem == null && isSealed
    val contentColor = when (status) {
        ActivityStatus.FAILED, ActivityStatus.DECLINED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val aggregateColor = MaterialTheme.colorScheme.onSurfaceVariant
    val sealedGroupStatusIcon = if (isSealedGroup) {
        when (status) {
            ActivityStatus.DECLINED -> Icons.Rounded.Block
            ActivityStatus.INTERRUPTED -> Icons.Rounded.Block
            ActivityStatus.IN_PROGRESS, ActivityStatus.COMPLETED, ActivityStatus.FAILED -> null
        }
    } else {
        null
    }
    val hasLeadingContent = singleItem != null || sealedGroupStatusIcon != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (canOpenDetails) {
                    Modifier.clickable(role = Role.Button, onClick = onOpenDetails)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 4.dp, vertical = 10.dp)
            .semantics {
                stateDescription = activityStatusDescription(status)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (singleItem != null) {
            ActivityTimelineIcon(singleItem, activeItemKeys)
        } else if (sealedGroupStatusIcon != null) {
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = sealedGroupStatusIcon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = contentColor,
                )
            }
        }
        if (hasLeadingContent) Spacer(Modifier.width(10.dp))
        if (singleItem == null && isSealed) {
            ActivityGroupMetrics(
                metrics = activityGroupMetrics(items),
                color = aggregateColor,
                modifier = Modifier.weight(1f),
            )
        } else if (singleItem == null) {
            ActiveActivityGroupTitle(
                text = activityGroupLabel(items, isSealed, activeItemKeys),
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                text = activityGroupLabel(items, isSealed, activeItemKeys),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canOpenDetails) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "查看处理过程详情",
                modifier = Modifier.size(22.dp),
                tint = if (isSealedGroup && status == ActivityStatus.FAILED) aggregateColor else contentColor,
            )
        }
    }
}

@Composable
private fun ActiveActivityGroupTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    val baseColor = MaterialTheme.colorScheme.onSurfaceVariant
    val highlightColor = lerp(baseColor, MaterialTheme.colorScheme.onSurface, 0.35f)
    var titleWidth by remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "活动组标题流光")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_000, easing = LinearEasing),
        ),
        label = "活动组标题流光进度",
    )
    val shimmerWidth = titleWidth * 0.45f
    val shimmerCenter = -shimmerWidth / 2f + progress * (titleWidth + shimmerWidth)
    val shimmerStart = shimmerCenter - shimmerWidth / 2f
    val shimmerEnd = shimmerCenter + shimmerWidth / 2f
    val titleBrush = if (titleWidth > 0f) {
        Brush.linearGradient(
            colorStops = arrayOf(
                0f to baseColor,
                0.35f to baseColor,
                0.5f to highlightColor,
                0.65f to baseColor,
                1f to baseColor,
            ),
            start = Offset(shimmerStart, 0f),
            end = Offset(shimmerEnd, 0f),
        )
    } else {
        Brush.linearGradient(listOf(baseColor, baseColor))
    }

    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge.copy(
            brush = titleBrush,
        ),
        onTextLayout = { result -> titleWidth = result.getLineRight(0) },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

internal data class ActivityDetailsActions(
    val isRunning: Boolean,
    val pendingRequest: PendingServerRequest?,
    val pendingRequestCount: Int,
    val onStop: () -> Unit,
    val onOpenPendingRequest: (String) -> Unit,
)

@Composable
private fun ActivityGroupMetrics(
    metrics: List<ActivityGroupMetric>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        metrics.forEach { metric ->
            Row(
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = metric.contentDescription()
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BadgedBox(
                    badge = {
                        if (metric.failedCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ) {
                                Text(metric.failedCount.badgeLabel())
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = metric.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = color,
                    )
                }
                Spacer(Modifier.width(metric.iconTextSpacing()))
                Text(
                    text = metric.count.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                    maxLines = 1,
                )
            }
        }
    }
}

internal enum class ActivityGroupMetricType {
    REASONING,
    PROGRESS,
    COMMAND,
    FILE,
    AGENT,
    ACTIVITY,
}

internal data class ActivityGroupMetric(
    val type: ActivityGroupMetricType,
    val count: Int,
    val failedCount: Int = 0,
)

internal fun activityGroupMetrics(items: List<ConversationItem>): List<ActivityGroupMetric> {
    val reasoningCount = items.count { it is ConversationItem.Reasoning }
    val progressCount = items.count {
        it is ConversationItem.Plan ||
            it is ConversationItem.AgentMessage && it.phase == AgentMessagePhase.COMMENTARY
    }
    val commands = items.filterIsInstance<ConversationItem.CommandExecution>()
    val fileChanges = items.filterIsInstance<ConversationItem.FileChange>()
    val activities = items.filterIsInstance<ConversationItem.Activity>()
    val agentItems = items.filter {
        it is ConversationItem.AgentCollaboration || it is ConversationItem.SubAgentActivity
    }
    return buildList {
        if (reasoningCount > 0) add(ActivityGroupMetric(ActivityGroupMetricType.REASONING, reasoningCount))
        if (progressCount > 0) add(ActivityGroupMetric(ActivityGroupMetricType.PROGRESS, progressCount))
        if (commands.isNotEmpty()) {
            add(
                ActivityGroupMetric(
                    type = ActivityGroupMetricType.COMMAND,
                    count = commands.size,
                    failedCount = commands.count { it.status == ActivityStatus.FAILED },
                ),
            )
        }
        if (fileChanges.isNotEmpty()) {
            add(
                ActivityGroupMetric(
                    type = ActivityGroupMetricType.FILE,
                    count = fileChanges.size,
                    failedCount = fileChanges.count { it.status == ActivityStatus.FAILED },
                ),
            )
        }
        if (agentItems.isNotEmpty()) {
            add(
                ActivityGroupMetric(
                    type = ActivityGroupMetricType.AGENT,
                    count = agentItems.size,
                    failedCount = agentItems.count { it.activityStatus() == ActivityStatus.FAILED },
                ),
            )
        }
        if (activities.isNotEmpty()) {
            add(
                ActivityGroupMetric(
                    type = ActivityGroupMetricType.ACTIVITY,
                    count = activities.size,
                    failedCount = activities.count { it.status == ActivityStatus.FAILED },
                ),
            )
        }
    }
}

private fun Int.badgeLabel(): String = if (this > 99) "99+" else toString()

private fun ActivityGroupMetric.iconTextSpacing() = when {
    failedCount > 99 -> 18.dp
    failedCount > 9 -> 14.dp
    failedCount > 0 -> 10.dp
    else -> 4.dp
}

private fun ActivityGroupMetric.icon(): ImageVector = when (type) {
    ActivityGroupMetricType.REASONING -> Icons.Rounded.Psychology
    ActivityGroupMetricType.PROGRESS -> Icons.Rounded.Checklist
    ActivityGroupMetricType.COMMAND -> Icons.Rounded.Terminal
    ActivityGroupMetricType.FILE -> Icons.Rounded.EditNote
    ActivityGroupMetricType.AGENT -> Icons.Rounded.AccountTree
    ActivityGroupMetricType.ACTIVITY -> Icons.Rounded.Extension
}

private fun ActivityGroupMetric.contentDescription(): String {
    val description = when (type) {
        ActivityGroupMetricType.REASONING -> "$count 项推理"
        ActivityGroupMetricType.PROGRESS -> "$count 项进度更新"
        ActivityGroupMetricType.COMMAND -> "$count 条命令"
        ActivityGroupMetricType.FILE -> "$count 次文件修改"
        ActivityGroupMetricType.AGENT -> "$count 次子代理协作"
        ActivityGroupMetricType.ACTIVITY -> "$count 项操作"
    }
    if (failedCount == 0) return description

    val failedUnit = when (type) {
        ActivityGroupMetricType.COMMAND -> "条"
        ActivityGroupMetricType.FILE -> "次"
        ActivityGroupMetricType.AGENT -> "次"
        ActivityGroupMetricType.REASONING,
        ActivityGroupMetricType.PROGRESS,
        ActivityGroupMetricType.ACTIVITY,
        -> "项"
    }
    return "$description，其中 $failedCount ${failedUnit}失败"
}

internal fun activityGroupLabel(
    items: List<ConversationItem>,
    isSealed: Boolean,
    activeItemKeys: Set<ConversationItemKey> = emptySet(),
): String {
    items.singleOrNull()?.let { return activityItemTitle(it) }
    if (!isSealed) return activeActivityLabel(items, activeItemKeys)

    return activityGroupMetrics(items).joinToString(" · ") { metric ->
        when (metric.type) {
            ActivityGroupMetricType.REASONING -> "推理 ${metric.count}"
            ActivityGroupMetricType.PROGRESS -> "进度 ${metric.count}"
            ActivityGroupMetricType.COMMAND -> "命令 ${metric.count}"
            ActivityGroupMetricType.FILE -> "文件 ${metric.count}"
            ActivityGroupMetricType.AGENT -> "协作 ${metric.count}"
            ActivityGroupMetricType.ACTIVITY -> "操作 ${metric.count}"
        }
    }
}

private fun activeActivityLabel(
    items: List<ConversationItem>,
    activeItemKeys: Set<ConversationItemKey>,
): String {
    val activeItem = items.lastOrNull { it.activityStatus(activeItemKeys) == ActivityStatus.IN_PROGRESS }
        ?: items.last()
    return when (activeItem) {
        is ConversationItem.Reasoning -> reasoningSummaryTitle(activeItem.summaryParts) ?: "推理中"

        is ConversationItem.Plan -> activeItem.text
            .lineSequence()
            .lastOrNull(String::isNotBlank)
            ?.trim()
            ?: "制定计划中"

        is ConversationItem.CommandExecution -> activeItem.command.commandPreview()
        is ConversationItem.UserShellCommand -> activeItem.command.commandPreview()
        is ConversationItem.FileChange -> activeItem.changes
            .map(::changedFileLabel)
            .map(ChangedFileLabel::path)
            .distinct()
            .joinToString("、")
            .ifBlank { "修改文件" }

        is ConversationItem.Activity -> activeItem.title
        is ConversationItem.AgentCollaboration -> activeItem.tool.title()
        is ConversationItem.SubAgentActivity -> activeItem.kind.title()
        is ConversationItem.AgentMessage -> agentMessageTitle(activeItem.text)
        is ConversationItem.UserMessage -> error("活动组包含用户消息")
    }
}

private fun activityGroupStatus(
    items: List<ConversationItem>,
    isSealed: Boolean,
    activeItemKeys: Set<ConversationItemKey>,
): ActivityStatus {
    if (!isSealed) return ActivityStatus.IN_PROGRESS
    val statuses = items.map { item -> item.activityStatus(activeItemKeys) }
    return when {
        ActivityStatus.FAILED in statuses -> ActivityStatus.FAILED
        ActivityStatus.DECLINED in statuses -> ActivityStatus.DECLINED
        ActivityStatus.INTERRUPTED in statuses -> ActivityStatus.INTERRUPTED
        else -> ActivityStatus.COMPLETED
    }
}

internal fun ConversationItem.activityStatus(
    activeItemKeys: Set<ConversationItemKey> = emptySet(),
): ActivityStatus {
    return when (this) {
        is ConversationItem.CommandExecution -> status
        is ConversationItem.UserShellCommand -> status
        is ConversationItem.FileChange -> status
        is ConversationItem.Activity -> status
        is ConversationItem.AgentCollaboration -> status
        is ConversationItem.SubAgentActivity -> ActivityStatus.COMPLETED
        is ConversationItem.Reasoning,
        is ConversationItem.Plan,
        is ConversationItem.AgentMessage,
        -> if (ConversationItemKey(turnId, id) in activeItemKeys) {
            ActivityStatus.IN_PROGRESS
        } else {
            ActivityStatus.COMPLETED
        }

        is ConversationItem.UserMessage -> ActivityStatus.COMPLETED
    }
}

@Composable
internal fun ActivityDetailsSheet(
    turnId: String,
    detailsState: TurnDetailsState,
    items: List<ConversationItem>,
    isSealed: Boolean,
    activeItemKeys: Set<ConversationItemKey>,
    activeActions: ActivityDetailsActions?,
    cwd: String,
    onOpenRemoteFile: (RemoteFileTarget) -> Unit,
    onLinkError: (String) -> Unit,
    onOpenRemotePath: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded),
    )
    var expandedItemKeys by rememberSaveable(turnId) {
        mutableStateOf(
            arrayListOf<String>().apply {
                items.singleOrNull()
                    ?.takeIf(::activityItemHasDetails)
                    ?.let { add("${it.turnId}:${it.id}") }
            },
        )
    }
    val followTargetIndex = activityFollowTargetIndex(items, isSealed, activeItemKeys)
    val followTargetKey = followTargetIndex?.let { index -> items[index].activityItemKey() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = followTargetIndex?.let(::activityHeaderLazyIndex) ?: 0,
    )
    val scrollScope = rememberCoroutineScope()
    var autoFollow by rememberSaveable(turnId) { mutableStateOf(true) }
    val userScrollConnection = remember(turnId) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && consumed.y != 0f) autoFollow = false
                return Offset.Zero
            }
        }
    }
    val showReturnToCurrent = followTargetIndex != null && !autoFollow

    LaunchedEffect(followTargetKey, autoFollow) {
        val targetIndex = followTargetIndex?.let(::activityHeaderLazyIndex) ?: return@LaunchedEffect
        if (autoFollow) listState.scrollToItem(targetIndex)
    }

    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.88f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 2.dp, bottom = 6.dp),
            ) {
                Text(
                    text = "处理过程",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                if (activeActions?.isRunning == true) {
                    TextButton(
                        onClick = activeActions.onStop,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Text("停止")
                    }
                }
            }
            val pendingRequest = activeActions?.pendingRequest
            if (pendingRequest != null) {
                PendingRequestBanner(
                    request = pendingRequest,
                    requestCount = activeActions.pendingRequestCount,
                    onClick = { activeActions.onOpenPendingRequest(pendingRequest.requestKey) },
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                if (detailsState != TurnDetailsState.Loaded || items.isEmpty()) {
                    ActivityDetailsStatus(detailsState, onRetry)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().nestedScroll(userScrollConnection),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 20.dp,
                            top = 22.dp,
                            end = 20.dp,
                            bottom = 32.dp,
                        ),
                    ) {
                        items.forEachIndexed { index, item ->
                            val itemKey = item.activityItemKey()
                            val expanded = itemKey in expandedItemKeys
                            stickyHeader(key = "$itemKey:header") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                                ) {
                                    ActivityTimelineHeader(
                                        item = item,
                                        isLast = index == items.lastIndex,
                                        isCurrent = index == followTargetIndex,
                                        activeItemKeys = activeItemKeys,
                                        expanded = expanded,
                                        onToggle = {
                                            expandedItemKeys = ArrayList(expandedItemKeys).apply {
                                                if (!remove(itemKey)) add(itemKey)
                                            }
                                        },
                                    )
                                }
                            }
                            item(key = "$itemKey:body") {
                                ActivityTimelineBody(
                                    item = item,
                                    isLast = index == items.lastIndex,
                                    expanded = expanded,
                                    cwd = cwd,
                                    onOpenRemoteFile = onOpenRemoteFile,
                                    onLinkError = onLinkError,
                                    onOpenRemotePath = onOpenRemotePath,
                                    onOpenConversation = onOpenConversation,
                                )
                            }
                        }
                    }
                }
                ReturnToCurrentStepButton(
                    visible = showReturnToCurrent,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                    onClick = {
                        val targetIndex = followTargetIndex?.let(::activityHeaderLazyIndex)
                            ?: return@ReturnToCurrentStepButton
                        scrollScope.launch {
                            listState.animateScrollToItem(targetIndex)
                            autoFollow = true
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ActivityDetailsStatus(
    state: TurnDetailsState,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            TurnDetailsState.Unloaded -> TextButton(onClick = onRetry) { Text("加载处理过程") }
            is TurnDetailsState.Loading -> {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(12.dp))
                Text("正在加载处理过程", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is TurnDetailsState.Failed -> {
                Text(state.error.summary, textAlign = TextAlign.Center)
                TextButton(onClick = onRetry) { Text("重试") }
            }
            TurnDetailsState.Loaded -> Text(
                "此回合没有处理过程",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReturnToCurrentStepButton(
    visible: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.85f),
        exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.9f),
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "回到当前步骤")
        }
    }
}

internal fun activityFollowTargetIndex(
    items: List<ConversationItem>,
    isSealed: Boolean,
    activeItemKeys: Set<ConversationItemKey> = emptySet(),
): Int? {
    if (isSealed || items.isEmpty()) return null
    return items.indexOfLast { it.activityStatus(activeItemKeys) == ActivityStatus.IN_PROGRESS }
        .takeIf { it >= 0 }
        ?: items.lastIndex
}

internal fun activityHeaderLazyIndex(itemIndex: Int): Int = itemIndex * 2

private fun ConversationItem.activityItemKey(): String = "$turnId:$id"

@Composable
private fun ActivityTimelineHeader(
    item: ConversationItem,
    isLast: Boolean,
    isCurrent: Boolean,
    activeItemKeys: Set<ConversationItemKey>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val hasDetails = activityItemHasDetails(item)
    val arrowRotation by animateFloatAsState(
        targetValue = if (hasDetails && expanded) 180f else 0f,
        animationSpec = tween(EXPANDABLE_CONTENT_FADE_DURATION_MILLIS),
        label = "处理过程详情展开箭头",
    )
    val statusDescription = buildString {
        append(activityStatusDescription(item.activityStatus(activeItemKeys)))
        if (hasDetails) append(if (expanded) "，已展开" else "，已收起")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .height(IntrinsicSize.Min)
            .then(
                if (hasDetails) {
                    Modifier
                        .clickable(role = Role.Button, onClick = onToggle)
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                stateDescription = statusDescription
                if (isCurrent) liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(30.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (!isLast) {
                ActivityTimelineConnector(Modifier.padding(top = 30.dp))
            }
            ActivityTimelineIcon(item, activeItemKeys)
        }
        Spacer(Modifier.width(12.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = activityItemTitle(item),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                activityItemSubtitle(item)?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (hasDetails) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp).rotate(arrowRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActivityTimelineBody(
    item: ConversationItem,
    isLast: Boolean,
    expanded: Boolean,
    cwd: String,
    onOpenRemoteFile: (RemoteFileTarget) -> Unit,
    onLinkError: (String) -> Unit,
    onOpenRemotePath: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    val hasDetails = activityItemHasDetails(item)
    val expansionTarget = hasDetails && expanded
    val expansionPhase = rememberContentExpansionPhase(
        expanded = expansionTarget,
        key = ConversationItemKey(item.turnId, item.id),
    )
    val bodyAlpha by animateFloatAsState(
        targetValue = if (expansionPhase.bodyVisible) 1f else 0f,
        animationSpec = tween(EXPANDABLE_CONTENT_FADE_DURATION_MILLIS),
        label = "处理过程详情透明度",
    )

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(30.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (!isLast) ActivityTimelineConnector()
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .animateContentSize(
                    animationSpec = if (expansionPhase == ContentExpansionPhase.EXPANDED) {
                        snap()
                    } else {
                        tween(EXPANDABLE_CONTENT_SIZE_ANIMATION_DURATION_MILLIS)
                    },
                ),
        ) {
            if (expansionPhase.bodyIncluded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .graphicsLayer { alpha = bodyAlpha },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ActivityItemDetails(
                        item = item,
                        cwd = cwd,
                        onOpenRemoteFile = onOpenRemoteFile,
                        onLinkError = onLinkError,
                        onOpenRemotePath = onOpenRemotePath,
                        onOpenConversation = onOpenConversation,
                    )
                }
            }
            if (!isLast && expansionPhase.bodyIncluded) Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActivityTimelineConnector(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun ActivityTimelineIcon(
    item: ConversationItem,
    activeItemKeys: Set<ConversationItemKey>,
) {
    val status = item.activityStatus(activeItemKeys)
    val icon = activityTypeIcon(item)
    val tint = when (status) {
        ActivityStatus.IN_PROGRESS -> MaterialTheme.colorScheme.onSurfaceVariant
        ActivityStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
        ActivityStatus.INTERRUPTED -> MaterialTheme.colorScheme.onSurfaceVariant
        ActivityStatus.FAILED, ActivityStatus.DECLINED -> MaterialTheme.colorScheme.error
    }
    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(26.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = tint,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = activityTypeDescription(item),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (status == ActivityStatus.IN_PROGRESS) {
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
        }
        if (status == ActivityStatus.FAILED || status == ActivityStatus.DECLINED) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).size(12.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (status == ActivityStatus.FAILED) Icons.Rounded.ErrorOutline else Icons.Rounded.Block,
                        contentDescription = null,
                        modifier = Modifier.size(9.dp),
                    )
                }
            }
        }
    }
}

private fun activityTypeIcon(item: ConversationItem): ImageVector = when (item) {
    is ConversationItem.Reasoning -> Icons.Rounded.Psychology
    is ConversationItem.Plan -> Icons.Rounded.Checklist
    is ConversationItem.CommandExecution -> Icons.Rounded.Terminal
    is ConversationItem.UserShellCommand -> Icons.Rounded.Terminal
    is ConversationItem.FileChange -> Icons.Rounded.EditNote
    is ConversationItem.AgentCollaboration -> Icons.Rounded.AccountTree
    is ConversationItem.SubAgentActivity -> Icons.Rounded.SmartToy
    is ConversationItem.Activity -> when (item.kind) {
        ActivityKind.MCP_TOOL, ActivityKind.DYNAMIC_TOOL -> Icons.Rounded.Extension
        ActivityKind.WEB_SEARCH -> Icons.Rounded.TravelExplore
        ActivityKind.CONTEXT_COMPACTION -> Icons.Rounded.Compress
        ActivityKind.REVIEW -> Icons.AutoMirrored.Rounded.FactCheck
        ActivityKind.HOOK -> Icons.Rounded.Webhook
        ActivityKind.IMAGE_VIEW -> Icons.Rounded.Image
        ActivityKind.IMAGE_GENERATION -> Icons.Rounded.AutoAwesome
        ActivityKind.WAIT -> Icons.Rounded.HourglassTop
        ActivityKind.UNKNOWN -> Icons.AutoMirrored.Rounded.HelpOutline
    }

    is ConversationItem.AgentMessage -> Icons.Rounded.EditNote
    is ConversationItem.UserMessage -> Icons.AutoMirrored.Rounded.HelpOutline
}

private fun activityTypeDescription(item: ConversationItem): String = when (item) {
    is ConversationItem.Reasoning -> "分析推理"
    is ConversationItem.Plan -> "计划"
    is ConversationItem.CommandExecution -> "命令"
    is ConversationItem.UserShellCommand -> "用户 Shell"
    is ConversationItem.FileChange -> "文件修改"
    is ConversationItem.AgentCollaboration -> "子代理协作"
    is ConversationItem.SubAgentActivity -> "子代理"
    is ConversationItem.Activity -> when (item.kind) {
        ActivityKind.MCP_TOOL, ActivityKind.DYNAMIC_TOOL -> "工具调用"
        ActivityKind.WEB_SEARCH -> "网页搜索"
        ActivityKind.CONTEXT_COMPACTION -> "上下文压缩"
        ActivityKind.REVIEW -> "审查"
        ActivityKind.HOOK -> "Hook"
        ActivityKind.IMAGE_VIEW -> "图片"
        ActivityKind.IMAGE_GENERATION -> "图片生成"
        ActivityKind.WAIT -> "等待"
        ActivityKind.UNKNOWN -> "未知活动"
    }

    is ConversationItem.UserMessage -> "用户消息"
    is ConversationItem.AgentMessage -> when (item.phase) {
        AgentMessagePhase.COMMENTARY -> "进度更新"
        AgentMessagePhase.FINAL_ANSWER, null -> "Codex 回复"
    }
}

private fun activityStatusDescription(status: ActivityStatus): String = when (status) {
    ActivityStatus.IN_PROGRESS -> "进行中"
    ActivityStatus.COMPLETED -> "已完成"
    ActivityStatus.FAILED -> "失败"
    ActivityStatus.DECLINED -> "已取消"
    ActivityStatus.INTERRUPTED -> "已中断"
}

internal fun activityItemTitle(item: ConversationItem): String {
    val title = when (item) {
        is ConversationItem.Reasoning -> reasoningSummaryTitle(item.summaryParts)
            ?: if (reasoningRawContent(item.rawContentParts).isNotBlank()) "原始推理" else "推理摘要"

        is ConversationItem.Plan -> "计划"

        is ConversationItem.CommandExecution -> item.command.commandPreview()
        is ConversationItem.UserShellCommand -> item.command.commandPreview()

        is ConversationItem.FileChange -> item.changes
            .map(::changedFileLabel)
            .map(ChangedFileLabel::path)
            .distinct()
            .let { paths -> paths.singleOrNull() ?: "${paths.size} 个文件" }

        is ConversationItem.Activity -> item.title
        is ConversationItem.AgentCollaboration -> item.tool.title()
        is ConversationItem.SubAgentActivity -> item.kind.title()
        is ConversationItem.UserMessage -> "用户消息"
        is ConversationItem.AgentMessage -> when (item.phase) {
            AgentMessagePhase.COMMENTARY -> agentMessageTitle(item.text)
            AgentMessagePhase.FINAL_ANSWER, null -> "Codex 回复"
        }
    }
    return when (item.activityStatus()) {
        ActivityStatus.IN_PROGRESS, ActivityStatus.COMPLETED -> title
        ActivityStatus.FAILED -> "$title · 失败"
        ActivityStatus.DECLINED -> "$title · 已取消"
        ActivityStatus.INTERRUPTED -> "$title · 已中断"
    }
}

internal fun activityItemSubtitle(item: ConversationItem): String? = when (item) {
    is ConversationItem.AgentCollaboration -> item.prompt?.takeIf(String::isNotBlank)
    is ConversationItem.SubAgentActivity -> item.agentPath
    is ConversationItem.Activity -> item.summary?.takeIf(String::isNotBlank)
    else -> null
}

internal fun activityItemHasDetails(item: ConversationItem): Boolean = when (item) {
    is ConversationItem.AgentCollaboration,
    is ConversationItem.SubAgentActivity,
    -> true
    is ConversationItem.Reasoning -> reasoningSummaryBody(item.summaryParts).isNotBlank() ||
        reasoningRawContent(item.rawContentParts).isNotBlank()
    is ConversationItem.Plan -> item.text.isNotBlank()
    is ConversationItem.CommandExecution -> item.command.isNotBlank() ||
        item.cwd.isNotBlank() ||
        item.output.isNotBlank() ||
        item.exitCode != null

    is ConversationItem.UserShellCommand -> true

    is ConversationItem.FileChange -> item.changes.isNotEmpty()
    is ConversationItem.Activity -> !item.summary.isNullOrBlank() ||
        item.details.isNotBlank() ||
        !item.technicalDetails.isNullOrBlank() ||
        item.remotePath != null

    is ConversationItem.AgentMessage -> item.phase == AgentMessagePhase.COMMENTARY && item.text.isNotBlank()
    is ConversationItem.UserMessage -> false
}

@Composable
private fun ActivityItemDetails(
    item: ConversationItem,
    cwd: String,
    onOpenRemoteFile: (RemoteFileTarget) -> Unit,
    onLinkError: (String) -> Unit,
    onOpenRemotePath: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    when (item) {
        is ConversationItem.AgentCollaboration -> AgentCollaborationDetails(
            item = item,
            cwd = cwd,
            onOpenRemoteFile = onOpenRemoteFile,
            onLinkError = onLinkError,
            onOpenConversation = onOpenConversation,
        )
        is ConversationItem.SubAgentActivity -> SubAgentActivityDetails(item, onOpenConversation)
        is ConversationItem.Reasoning -> ReasoningItemDetails(
            item = item,
            cwd = cwd,
            onOpenRemoteFile = onOpenRemoteFile,
            onLinkError = onLinkError,
        )

        is ConversationItem.Plan -> {
            if (item.text.isNotBlank()) {
                SelectionContainer {
                    Text(
                        item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        is ConversationItem.CommandExecution -> ActivityCommandDetails(
            command = item.command, cwd = item.cwd, output = item.output, exitCode = item.exitCode,
        )

        is ConversationItem.UserShellCommand -> ActivityCommandDetails(
            command = item.command, cwd = item.cwd, output = item.output, exitCode = item.exitCode,
        )

        is ConversationItem.FileChange -> {
            item.changes.forEach { change ->
                ChangedFileView(change)
                Spacer(Modifier.height(4.dp))
            }
        }

        is ConversationItem.Activity -> {
            val description = if (item.kind == ActivityKind.WEB_SEARCH) {
                item.details
            } else {
                listOfNotNull(item.summary, item.details.takeIf(String::isNotBlank))
                    .distinct()
                    .joinToString("\n\n")
            }
            if (description.isNotBlank()) {
                SelectionContainer {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item.technicalDetails?.takeIf(String::isNotBlank)?.let { ExpandableTechnicalDetails(it) }
            item.remotePath?.let { path ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { onOpenRemotePath(path) }) {
                        Icon(Icons.Rounded.Visibility, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("查看文件")
                    }
                    CopyTextIconButton(path, "复制文件路径")
                }
            }
        }

        is ConversationItem.AgentMessage -> if (item.phase == AgentMessagePhase.COMMENTARY) {
            SelectionContainer {
                ChatMarkdown(
                    content = item.text,
                    cwd = cwd,
                    onOpenRemoteFile = onOpenRemoteFile,
                    onLinkError = onLinkError,
                )
            }
        } else {
            Unit
        }

        is ConversationItem.UserMessage -> Unit
    }
}

@Composable
private fun ActivityCommandDetails(command: String, cwd: String, output: String, exitCode: Int?) {
    ShellCommandBlock(command)
    if (cwd.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                cwd,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            CopyTextIconButton(cwd, "复制工作目录")
        }
    }
    if (output.isNotBlank()) TerminalOutputBlock(output)
    exitCode?.let {
        Text(
            "退出码 $it",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun agentMessageTitle(text: String): String =
    text.lineSequence().firstOrNull(String::isNotBlank)?.trim() ?: "进度更新"

@Composable
private fun ChangedFileView(change: ChangedFile) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val label = changedFileLabel(change)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${label.action} · ",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                label.path,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
            CopyTextIconButton(label.path, "复制文件路径")
        }
        if (change.diff.isNotBlank()) FileDiffBlock(change)
    }
}

private data class ChangedFileLabel(val action: String, val path: String)

private fun changedFileLabel(change: ChangedFile): ChangedFileLabel = when (val kind = change.kind) {
    FileChangeKind.Add -> ChangedFileLabel(action = "新增", path = change.path)
    FileChangeKind.Delete -> ChangedFileLabel(action = "删除", path = change.path)
    is FileChangeKind.Update -> kind.movePath
        ?.let { destination -> ChangedFileLabel(action = "移动", path = "${change.path} → $destination") }
        ?: ChangedFileLabel(action = "修改", path = change.path)
}

private fun String.commandPreview(): String =
    shellCommandForDisplay(this)
        .lineSequence()
        .firstOrNull()
        ?.trim()
        ?.take(56)
        .orEmpty()
        .ifBlank { "命令" }
