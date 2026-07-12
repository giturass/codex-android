@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hebo.codex.R
import com.hebo.codex.domain.ApprovalDecision
import com.hebo.codex.domain.ApprovalPolicy
import com.hebo.codex.domain.ApprovalReviewer
import com.hebo.codex.domain.ApprovalType
import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.ConversationState
import com.hebo.codex.domain.DraftAttachment
import com.hebo.codex.domain.FeatureEntry
import com.hebo.codex.domain.ModelOption
import com.hebo.codex.domain.NetworkPolicyRuleAction
import com.hebo.codex.domain.PendingServerRequest
import com.hebo.codex.domain.Personality
import com.hebo.codex.domain.PlanStepStatus
import com.hebo.codex.domain.QuickPhrase
import com.hebo.codex.domain.ReasoningSummary
import com.hebo.codex.domain.ReviewDelivery
import com.hebo.codex.domain.ReviewTarget
import com.hebo.codex.domain.SandboxMode
import com.hebo.codex.domain.ThreadGoal
import com.hebo.codex.domain.ThreadGoalStatus
import com.hebo.codex.domain.TokenUsage
import com.hebo.codex.domain.TokenUsageBreakdown
import com.hebo.codex.domain.TurnPlan
import com.hebo.codex.domain.TurnOptions
import com.hebo.codex.domain.UserInputPart
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.domain.projectName
import com.hebo.codex.ui.displayDescriptionRes
import com.hebo.codex.ui.displayLabelRes
import com.hebo.codex.ui.formatCompactCount
import com.hebo.codex.ui.formatCount
import com.hebo.codex.ui.formatPercent
import com.hebo.codex.ui.components.MessageComposer
import com.hebo.codex.ui.components.MessageComposerAction
import com.hebo.codex.ui.components.MessageComposerActionButton
import com.hebo.codex.ui.components.MessageComposerAddButton
import com.hebo.codex.ui.components.QuickPhraseManagerDialog
import com.hebo.codex.ui.components.QuickPhraseMenuContent
import com.hebo.codex.ui.components.ScrollChromeState
import com.hebo.codex.ui.components.UserFacingErrorDialog
import com.hebo.codex.ui.components.afterUserScroll
import com.hebo.codex.ui.components.insertQuickPhrase
import com.hebo.codex.ui.components.settleTarget
import com.hebo.codex.ui.quickphrases.QuickPhraseViewModel
import com.hebo.codex.ui.ratelimits.RateLimitIndicator
import com.hebo.codex.ui.ratelimits.RateLimitsUiState
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max

private val highlightedMarkdownComponents = markdownComponents(
    codeBlock = highlightedCodeBlock,
    codeFence = highlightedCodeFence,
)

private const val CHAT_LIST_END_KEY = "chat-list-end"
private const val SCROLL_TO_BOTTOM_BUTTON_DELAY_MILLIS = 500L
private val ChatHeaderControlsHeight = 64.dp
private val ChatHeaderContentSpacing = 24.dp
private val ChatComposerOverlayMinHeight = 116.dp
private val ChatComposerMaxWidth = 760.dp
private const val CHAT_CHROME_SETTLE_DURATION_MILLIS = 160

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    quickPhraseViewModel: QuickPhraseViewModel,
    rateLimitsState: RateLimitsUiState,
    showNavigationIcon: Boolean,
    onOpenNavigation: () -> Unit,
    onNewTask: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
    onRefreshRateLimits: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val quickPhraseState by quickPhraseViewModel.state.collectAsStateWithLifecycle()
    val conversation = state.conversation
    val conversationRows = remember(conversation?.items) {
        buildConversationRows(conversation?.items.orEmpty())
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    var autoFollow by rememberSaveable(conversation?.threadId) { mutableStateOf(true) }
    var showScrollToBottomButton by remember(conversation?.threadId) { mutableStateOf(false) }
    var chromeScrollState by remember(conversation?.threadId) { mutableStateOf(ScrollChromeState()) }
    var chromeSettleJob by remember(conversation?.threadId) { mutableStateOf<Job?>(null) }
    var topControlsHeightPx by remember { mutableStateOf(0) }
    var bottomControlsHeightPx by remember { mutableStateOf(0) }
    var bottomControlsTranslationHeightPx by remember { mutableStateOf(0) }
    var dialog by remember { mutableStateOf<ChatDialog?>(null) }
    var showTurnOptions by remember { mutableStateOf(false) }
    var activeRequestKey by rememberSaveable(conversation?.threadId) { mutableStateOf<String?>(null) }
    var shownNoticeId by rememberSaveable(conversation?.threadId) { mutableStateOf<String?>(null) }
    var technicalError by remember { mutableStateOf<UserFacingError?>(null) }
    val detailsLabel = stringResource(R.string.common_details)
    val activeRequest = conversation?.pendingRequests?.firstOrNull { it.requestKey == activeRequestKey }
    val density = LocalDensity.current
    val draftHasInput = state.draft.message.isNotBlank() || state.draft.attachments.isNotEmpty()
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val bottomControlsCanHide = !draftHasInput &&
        !imeVisible &&
        !state.isSendingDraft &&
        conversation?.pendingRequests.isNullOrEmpty()
    val chromeHideDistancePx = maxOf(
        topControlsHeightPx,
        bottomControlsTranslationHeightPx.takeIf { bottomControlsCanHide } ?: 0,
    ).toFloat()
    val userScrollConnection = remember(
        conversation?.threadId,
        chromeHideDistancePx,
    ) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && consumed.y != 0f) {
                    chromeSettleJob?.cancel()
                    chromeSettleJob = null
                    autoFollow = false
                    chromeScrollState = chromeScrollState.afterUserScroll(
                        hideDelta = consumed.y,
                        hideDistance = chromeHideDistancePx,
                    )
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val targetOffset = chromeScrollState.settleTarget(chromeHideDistancePx)
                val targetHidden = chromeHideDistancePx > 0f && targetOffset == chromeHideDistancePx
                chromeScrollState = chromeScrollState.copy(settledHidden = targetHidden)
                if (targetOffset != chromeScrollState.offset) {
                    val initialOffset = chromeScrollState.offset
                    chromeSettleJob?.cancel()
                    chromeSettleJob = scrollScope.launch {
                        animate(
                            initialValue = initialOffset,
                            targetValue = targetOffset,
                            animationSpec = tween(CHAT_CHROME_SETTLE_DURATION_MILLIS),
                        ) { value, _ ->
                            chromeScrollState = chromeScrollState.copy(offset = value)
                        }
                        chromeScrollState = if (targetOffset == 0f) {
                            ScrollChromeState()
                        } else {
                            ScrollChromeState(
                                fullyHidden = true,
                                offset = targetOffset,
                                settledHidden = true,
                            )
                        }
                    }
                }
                return Velocity.Zero
            }
        }
    }

    LifecycleResumeEffect(viewModel) {
        onRefreshRateLimits()
        onPauseOrDispose { viewModel.flushDraft() }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatEvent.OpenConversation -> onOpenConversation(event.threadId)
            }
        }
    }
    LaunchedEffect(state.screenError, conversation?.errorMessage) {
        val error = state.screenError ?: conversation?.errorMessage
        if (error != null) {
            val result = snackbarHostState.showSnackbar(
                message = error.summary,
                actionLabel = error.technicalDetails?.let { detailsLabel },
            )
            if (result == SnackbarResult.ActionPerformed) technicalError = error
            viewModel.clearError()
        }
    }
    LaunchedEffect(conversation?.notices?.lastOrNull()?.id) {
        val notice = conversation?.notices?.lastOrNull() ?: return@LaunchedEffect
        if (notice.id == shownNoticeId) return@LaunchedEffect
        shownNoticeId = notice.id
        val result = snackbarHostState.showSnackbar(
            message = notice.message,
            actionLabel = notice.technicalDetails?.let { detailsLabel },
        )
        if (result == SnackbarResult.ActionPerformed) {
            technicalError = UserFacingError(notice.message, notice.technicalDetails.orEmpty())
        }
    }
    LaunchedEffect(conversation?.pendingRequests?.map { it.requestKey }) {
        if (activeRequestKey != null && activeRequest == null) {
            activeRequestKey = conversation?.pendingRequests?.firstOrNull()?.requestKey
        }
    }
    LaunchedEffect(listState, autoFollow) {
        if (autoFollow) {
            snapshotFlow {
                val lastIndex = listState.layoutInfo.totalItemsCount - 1
                lastIndex.takeIf { autoFollow && it >= 0 && listState.canScrollForward }
            }.filterNotNull().collectLatest { lastIndex ->
                listState.scrollToItem(lastIndex)
            }
        } else {
            snapshotFlow {
                !listState.isScrollInProgress && !listState.canScrollForward
            }.first { it }
            autoFollow = true
        }
    }
    LaunchedEffect(autoFollow) {
        if (autoFollow) {
            chromeSettleJob?.cancel()
            chromeSettleJob = null
            chromeScrollState = ScrollChromeState()
        }
    }
    LaunchedEffect(chromeHideDistancePx) {
        chromeScrollState = if (chromeScrollState.fullyHidden) {
            chromeScrollState.copy(offset = chromeHideDistancePx)
        } else {
            chromeScrollState.copy(offset = chromeScrollState.offset.coerceAtMost(chromeHideDistancePx))
        }
    }
    LaunchedEffect(listState, autoFollow) {
        snapshotFlow {
            !autoFollow && !listState.isScrollInProgress && listState.canScrollForward
        }.collectLatest { shouldShow ->
            if (shouldShow) {
                delay(SCROLL_TO_BOTTOM_BUTTON_DELAY_MILLIS)
            }
            showScrollToBottomButton = shouldShow
        }
    }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomSystemPadding = maxOf(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
    )
    val topControlsHeight = with(density) { topControlsHeightPx.toDp() }
    val bottomControlsHeight = with(density) { bottomControlsHeightPx.toDp() }
    val showFullBottomControls = !bottomControlsCanHide || !chromeScrollState.fullyHidden
    val showCompactStop = bottomControlsCanHide &&
        chromeScrollState.fullyHidden &&
        conversation?.isRunning == true
    val topContentPadding = maxOf(
        topControlsHeight,
        statusBarPadding + ChatHeaderControlsHeight,
    ) + ChatHeaderContentSpacing
    val bottomOverlayMinHeight = when {
        showFullBottomControls -> ChatComposerOverlayMinHeight
        showCompactStop -> 64.dp
        else -> 0.dp
    }
    val bottomContentPadding = if (conversation == null) {
        bottomSystemPadding + 16.dp
    } else {
        maxOf(
            bottomControlsHeight.takeIf { showFullBottomControls } ?: 0.dp,
            bottomOverlayMinHeight,
        ) + bottomSystemPadding
    }
    val topControlsTranslationY = -chromeScrollState.offset.coerceAtMost(topControlsHeightPx.toFloat())
    val bottomControlsTranslationY = if (bottomControlsCanHide) {
        chromeScrollState.offset.coerceAtMost(bottomControlsTranslationHeightPx.toFloat())
    } else {
        0f
    }

    Box(Modifier.fillMaxSize()) {
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            conversation == null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("无法打开会话")
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().nestedScroll(userScrollConnection),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = topContentPadding,
                    end = 20.dp,
                    bottom = bottomContentPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(conversationRows, key = ConversationRow::key) { row ->
                    when (row) {
                        is ConversationRow.Item -> ConversationItemView(row.item)
                        is ConversationRow.ActivityGroup -> ActivityGroupCard(row.items)
                    }
                }
                item(CHAT_LIST_END_KEY) {
                    Spacer(Modifier.height(1.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = topControlsTranslationY },
        ) {
            ConversationFloatingHeader(
                conversation = conversation,
                newConversationTitle = null,
                newConversationSubtitle = null,
                connectionStatus = state.connectionStatus,
                rateLimitsState = rateLimitsState,
                showNavigationIcon = showNavigationIcon,
                onOpenNavigation = onOpenNavigation,
                actions = ConversationHeaderActions(
                    onNewTask = { conversation?.let { onNewTask(it.cwd) } },
                    onRename = { dialog = ChatDialog.Rename },
                    onFork = { dialog = ChatDialog.Fork },
                    onOpenGoal = { dialog = ChatDialog.Goal },
                    onOpenStatistics = { dialog = ChatDialog.Statistics },
                ),
                onRefreshRateLimits = onRefreshRateLimits,
                modifier = Modifier.onSizeChanged { topControlsHeightPx = it.height },
            )
        }

        if (conversation != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { bottomControlsTranslationHeightPx = it.height }
                    .graphicsLayer { translationY = bottomControlsTranslationY }
                    .navigationBarsPadding()
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier
                        .widthIn(max = ChatComposerMaxWidth)
                        .fillMaxWidth()
                        .onSizeChanged { bottomControlsHeightPx = it.height },
                ) {
                    conversation.pendingRequests.firstOrNull()?.let { request ->
                        PendingRequestBanner(
                            request = request,
                            requestCount = conversation.pendingRequests.size,
                            onClick = { activeRequestKey = request.requestKey },
                        )
                    }
                    ConversationComposer(
                        isRunning = conversation.isRunning,
                        plan = conversation.plan,
                        activeTurnId = conversation.activeTurnId,
                        message = state.draft.message,
                        attachments = state.draft.attachments,
                        modelLabel = composerModelLabel(
                            model = state.draft.options.model,
                            reasoningEffort = state.draft.options.reasoningEffort,
                            models = state.models,
                        ),
                        resources = state.inputResources,
                        quickPhrases = quickPhraseState.phrases,
                        isLoadingResources = state.isLoadingResources,
                        isSending = state.isSendingDraft,
                        sendEnabled = state.connectionStatus is ConnectionStatus.Connected,
                        placeholder = when {
                            state.connectionStatus !is ConnectionStatus.Connected -> "服务器已断开"
                            conversation.isRunning -> "继续对话"
                            else -> "向 Codex 发送消息"
                        },
                        commands = ComposerCommand.entries,
                        onLoadResources = viewModel::loadInputResources,
                        onAddQuickPhrase = quickPhraseViewModel::add,
                        onUpdateQuickPhrase = quickPhraseViewModel::update,
                        onDeleteQuickPhrase = quickPhraseViewModel::delete,
                        onMoveQuickPhraseUp = quickPhraseViewModel::moveUp,
                        onMoveQuickPhraseDown = quickPhraseViewModel::moveDown,
                        onMessageChange = viewModel::updateDraftMessage,
                        onAddAttachment = viewModel::addDraftAttachment,
                        onRemoveAttachment = viewModel::removeDraftAttachment,
                        onInputError = viewModel::reportInputError,
                        onOpenOptions = { showTurnOptions = true },
                        onCommand = { command ->
                            when (command) {
                                ComposerCommand.GOAL -> dialog = ChatDialog.Goal
                                ComposerCommand.REVIEW -> dialog = ChatDialog.Review
                                ComposerCommand.COMPACT -> viewModel.compact()
                                else -> Unit
                            }
                        },
                        onSend = {
                            chromeSettleJob?.cancel()
                            chromeSettleJob = null
                            chromeScrollState = ScrollChromeState()
                            autoFollow = true
                            viewModel.sendDraft()
                        },
                        onStop = viewModel::interrupt,
                    )
                }
            }

            AnimatedVisibility(
                visible = showCompactStop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 10.dp),
                enter = fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.85f),
                exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.9f),
            ) {
                SmallFloatingActionButton(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    onClick = viewModel::interrupt,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = "停止")
                }
            }
        }

        AnimatedVisibility(
            visible = conversation != null && showScrollToBottomButton,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomContentPadding + 8.dp),
            enter = fadeIn(tween(160)) +
                scaleIn(tween(160), initialScale = 0.85f),
            exit = fadeOut(tween(100)) +
                scaleOut(tween(100), targetScale = 0.9f),
        ) {
            SmallFloatingActionButton(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                onClick = {
                    showScrollToBottomButton = false
                    chromeSettleJob?.cancel()
                    chromeSettleJob = null
                    chromeScrollState = ScrollChromeState()
                    scrollScope.launch {
                        val lastIndex = listState.layoutInfo.totalItemsCount - 1
                        if (lastIndex >= 0) {
                            listState.animateScrollToItem(lastIndex)
                            autoFollow = true
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "回到底部")
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = bottomContentPadding + 8.dp,
                ),
        )
    }

    when (dialog) {
        ChatDialog.Rename -> RenameConversationDialog(
            title = conversation?.title.orEmpty(),
            onDismiss = { dialog = null },
            onRename = {
                viewModel.rename(it)
                dialog = null
            },
        )

        ChatDialog.Fork -> ForkConversationSheet(
            turns = conversation?.items.orEmpty()
                .filter { it.turnId != conversation?.activeTurnId && it.turnId != "pending" }
                .distinctBy(ConversationItem::turnId)
                .map { it.turnId },
            canForkLatest = conversation?.isRunning != true,
            onDismiss = { dialog = null },
            onFork = { lastTurnId ->
                viewModel.fork(lastTurnId)
                dialog = null
            },
        )

        ChatDialog.Statistics -> ConversationStatisticsSheet(
            usage = conversation?.tokenUsage,
            onDismiss = { dialog = null },
        )

        ChatDialog.Review -> ReviewDialog(
            onDismiss = { dialog = null },
            onSubmit = { target, delivery ->
                viewModel.review(target, delivery)
                dialog = null
            },
        )

        ChatDialog.Goal -> GoalDialog(
            goal = state.goal,
            onDismiss = { dialog = null },
            onSave = { objective, status, budget ->
                viewModel.setGoal(objective, status, budget)
                dialog = null
            },
            onClear = {
                viewModel.clearGoal()
                dialog = null
            },
        )

        null -> Unit
    }

    activeRequest?.let { request ->
        val pendingRequests = conversation.pendingRequests
        val requestIndex = pendingRequests.indexOfFirst { it.requestKey == request.requestKey }.coerceAtLeast(0)
        ModalBottomSheet(
            onDismissRequest = { activeRequestKey = null },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "待处理 ${requestIndex + 1}/${pendingRequests.size}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(
                        onClick = { activeRequestKey = pendingRequests[requestIndex - 1].requestKey },
                        enabled = requestIndex > 0,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一项")
                    }
                    IconButton(
                        onClick = { activeRequestKey = pendingRequests[requestIndex + 1].requestKey },
                        enabled = requestIndex < pendingRequests.lastIndex,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一项")
                    }
                }
                Box(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                ) {
                    Column {
                        PendingRequestCard(
                            request = request,
                            onApproval = viewModel::respondToApproval,
                            onExecPolicy = viewModel::respondToApprovalWithExecPolicy,
                            onNetworkPolicy = viewModel::respondToApprovalWithNetworkPolicy,
                            onQuestions = viewModel::respondToQuestions,
                            onMcp = viewModel::respondToMcpElicitation,
                            onPermissions = viewModel::respondToPermissions,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
    technicalError?.let { error ->
        UserFacingErrorDialog(error = error, onDismiss = { technicalError = null })
    }
    if (showTurnOptions) {
        TurnOptionsSheet(
            value = state.draft.options,
            models = state.models,
            onDismiss = { showTurnOptions = false },
            onSave = {
                viewModel.updateDraftOptions(it)
                showTurnOptions = false
            },
        )
    }
}

internal data class ConversationHeaderActions(
    val onNewTask: () -> Unit,
    val onRename: () -> Unit,
    val onFork: () -> Unit,
    val onOpenGoal: () -> Unit,
    val onOpenStatistics: () -> Unit,
)

@Composable
internal fun ConversationFloatingHeader(
    conversation: ConversationState?,
    newConversationTitle: String?,
    newConversationSubtitle: String?,
    connectionStatus: ConnectionStatus,
    rateLimitsState: RateLimitsUiState,
    showNavigationIcon: Boolean,
    onOpenNavigation: () -> Unit,
    actions: ConversationHeaderActions?,
    onRefreshRateLimits: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var actionsExpanded by remember { mutableStateOf(false) }
    val headerTitle = conversation?.title ?: newConversationTitle
    val headerSubtitle = conversation?.let {
        val status = when {
            connectionStatus !is ConnectionStatus.Connected -> "已断开"
            it.pendingRequests.isNotEmpty() -> "需要处理"
            it.isRunning -> "运行中"
            it.errorMessage != null -> "失败"
            else -> "可继续"
        }
        "$status · ${projectName(it.cwd)} · ${it.serverName}"
    } ?: newConversationSubtitle
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (showNavigationIcon) {
            Surface(
                modifier = Modifier.align(Alignment.CenterStart).size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 2.dp,
            ) {
                IconButton(onClick = onOpenNavigation) {
                    Icon(Icons.Rounded.Menu, contentDescription = "打开导航")
                }
            }
        } else if (headerTitle != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 116.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 2.dp,
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(
                        headerTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        headerSubtitle.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (headerTitle != null) {
            Surface(
                modifier = Modifier.align(Alignment.CenterEnd),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 2.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RateLimitIndicator(
                        state = rateLimitsState,
                        onRefresh = onRefreshRateLimits,
                    )
                    if (showNavigationIcon && actions != null) {
                        IconButton(onClick = actions.onNewTask) {
                            Icon(Icons.Rounded.Edit, contentDescription = "新建任务")
                        }
                    }
                    if (actions != null) {
                        Box {
                            IconButton(onClick = { actionsExpanded = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "会话操作")
                            }
                            DropdownMenu(
                                expanded = actionsExpanded,
                                onDismissRequest = { actionsExpanded = false },
                            ) {
                                ChatActionMenuItem(
                                    text = "重命名",
                                    icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                    onClick = {
                                        actionsExpanded = false
                                        actions.onRename()
                                    },
                                )
                                ChatActionMenuItem(
                                    text = "分叉会话",
                                    icon = { Icon(Icons.AutoMirrored.Rounded.CallSplit, contentDescription = null) },
                                    onClick = {
                                        actionsExpanded = false
                                        actions.onFork()
                                    },
                                )
                                ChatActionMenuItem(
                                    text = "目标管理",
                                    icon = { Icon(Icons.Rounded.Flag, contentDescription = null) },
                                    onClick = {
                                        actionsExpanded = false
                                        actions.onOpenGoal()
                                    },
                                )
                                ChatActionMenuItem(
                                    text = "对话统计",
                                    icon = { Icon(Icons.Rounded.BarChart, contentDescription = null) },
                                    onClick = {
                                        actionsExpanded = false
                                        actions.onOpenStatistics()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatActionMenuItem(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = icon,
    )
}

@Composable
private fun ConversationItemView(item: ConversationItem) {
    when (item) {
        is ConversationItem.UserMessage -> UserMessage(item)
        is ConversationItem.AgentMessage -> AgentMessage(item)
        is ConversationItem.Plan -> PlanSnapshot(item)
        else -> ActivityGroupCard(listOf(item))
    }
}

@Composable
private fun PlanSnapshot(item: ConversationItem.Plan) {
    val markdownState = rememberMarkdownState(
        content = item.text.ifEmpty { "正在制定计划…" },
        retainState = true,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("执行计划", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (item.isStreaming) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
            SelectionContainer {
                Markdown(
                    markdownState = markdownState,
                    components = highlightedMarkdownComponents,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AgentMessage(item: ConversationItem.AgentMessage) {
    val markdownState = rememberMarkdownState(
        content = item.text.ifEmpty { "…" },
        retainState = true,
    )

    Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        SelectionContainer {
            Markdown(
                markdownState = markdownState,
                components = highlightedMarkdownComponents,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PendingRequestBanner(
    request: PendingServerRequest,
    requestCount: Int,
    onClick: () -> Unit,
) {
    val isCritical = request is PendingServerRequest.Permissions
    val icon = when (request) {
        is PendingServerRequest.Approval -> if (request.type == ApprovalType.COMMAND_EXECUTION) {
            Icons.Rounded.Code
        } else {
            Icons.Rounded.Description
        }
        is PendingServerRequest.Questions -> Icons.AutoMirrored.Rounded.HelpOutline
        is PendingServerRequest.McpElicitation -> Icons.Rounded.Extension
        is PendingServerRequest.Permissions -> Icons.Rounded.Lock
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(pendingRequestTitle(request), style = MaterialTheme.typography.labelLarge)
                Text(
                    if (requestCount > 1) "还有 ${requestCount - 1} 项等待处理" else pendingRequestSummary(request),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("查看", style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun pendingRequestTitle(request: PendingServerRequest): String = when (request) {
    is PendingServerRequest.Approval -> if (request.type == ApprovalType.COMMAND_EXECUTION) {
        "命令等待确认"
    } else {
        "文件修改等待确认"
    }

    is PendingServerRequest.Questions -> "Codex 需要你的回答"
    is PendingServerRequest.McpElicitation -> "${request.serverName} 请求确认"
    is PendingServerRequest.Permissions -> "权限请求"
}

private fun pendingRequestSummary(request: PendingServerRequest): String = when (request) {
    is PendingServerRequest.Approval -> request.reason ?: request.command ?: request.cwd ?: "查看详情后决定"
    is PendingServerRequest.Questions -> request.questions.firstOrNull()?.question ?: "请补充信息"
    is PendingServerRequest.McpElicitation -> request.message
    is PendingServerRequest.Permissions -> request.reason ?: request.cwd
}

@Composable
private fun PendingRequestCard(
    request: PendingServerRequest,
    onApproval: (String, ApprovalDecision) -> Unit,
    onExecPolicy: (String, String) -> Unit,
    onNetworkPolicy: (String, String) -> Unit,
    onQuestions: (String, Map<String, List<String>>) -> Unit,
    onMcp: (String, String, String?) -> Unit,
    onPermissions: (String, String, Boolean, Boolean, Boolean) -> Unit,
) {
    when (request) {
        is PendingServerRequest.Approval -> ApprovalCard(
            approval = request,
            onDecision = { decision -> onApproval(request.requestKey, decision) },
            onExecPolicy = { amendment -> onExecPolicy(request.requestKey, amendment) },
            onNetworkPolicy = { amendment -> onNetworkPolicy(request.requestKey, amendment) },
        )

        is PendingServerRequest.Questions -> QuestionsCard(request) { answers ->
            onQuestions(request.requestKey, answers)
        }

        is PendingServerRequest.McpElicitation -> McpElicitationCard(request) { action, content ->
            onMcp(request.requestKey, action, content)
        }

        is PendingServerRequest.Permissions -> PermissionsCard(request) { grant, session, strictAutoReview ->
            onPermissions(
                request.requestKey,
                request.requestedPermissions.responseJson,
                grant,
                session,
                strictAutoReview,
            )
        }
    }
}

@Composable
private fun ApprovalCard(
    approval: PendingServerRequest.Approval,
    onDecision: (ApprovalDecision) -> Unit,
    onExecPolicy: (String) -> Unit,
    onNetworkPolicy: (String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (approval.type == ApprovalType.COMMAND_EXECUTION) "命令等待审批" else "文件修改等待审批",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            approval.reason?.takeIf(String::isNotBlank)?.let { Text(it) }
            approval.command?.takeIf(String::isNotBlank)?.let { ShellCommandBlock(it) }
            approval.cwd?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            approval.networkHost?.let { Text("网络主机：$it") }
            approval.grantRoot?.let { Text("授权目录：$it") }
            if (approval.commandActions.isNotEmpty()) {
                Text("这条命令将执行", style = MaterialTheme.typography.labelLarge)
                approval.commandActions.forEach { CommandActionView(it) }
            }
            approval.proposedExecPolicy?.let { amendment ->
                PolicyProposal(
                    title = "以后允许执行同类命令",
                    description = amendment.command.joinToString(" "),
                    technicalDetails = amendment.responseJson,
                )
                OutlinedButton(
                    onClick = { onExecPolicy(amendment.responseJson) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存命令规则并允许本次操作")
                }
            }
            approval.proposedNetworkPolicies.forEach { amendment ->
                PolicyProposal(
                    title = if (amendment.action == NetworkPolicyRuleAction.ALLOW) {
                        "以后允许访问此主机"
                    } else {
                        "以后阻止访问此主机"
                    },
                    description = amendment.host,
                    technicalDetails = amendment.responseJson,
                )
                OutlinedButton(
                    onClick = { onNetworkPolicy(amendment.responseJson) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存网络规则并允许本次操作") }
            }
            Button(
                onClick = { onDecision(ApprovalDecision.ACCEPT) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("允许一次") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onDecision(ApprovalDecision.ACCEPT_FOR_SESSION) },
                    modifier = Modifier.weight(1f),
                ) { Text("本会话允许") }
                TextButton(
                    onClick = { onDecision(ApprovalDecision.DECLINE) },
                    modifier = Modifier.weight(1f),
                ) { Text("拒绝") }
            }
        }
    }
}

@Composable
private fun QuestionsCard(
    request: PendingServerRequest.Questions,
    onSubmit: (Map<String, List<String>>) -> Unit,
) {
    var answers by remember(request.requestKey) { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var otherQuestions by remember(request.requestKey) {
        mutableStateOf(request.questions.filter { it.options.isEmpty() }.map { it.id }.toSet())
    }
    var remainingMillis by remember(request.requestKey) {
        mutableStateOf(
            request.autoResolutionMillis?.let {
                max(0L, it - (System.currentTimeMillis() - request.receivedAtMillis))
            },
        )
    }
    LaunchedEffect(request.requestKey, request.autoResolutionMillis) {
        if (request.autoResolutionMillis != null) {
            while ((remainingMillis ?: 0L) > 0L) {
                delay(1_000)
                remainingMillis = max(
                    0L,
                    request.autoResolutionMillis - (System.currentTimeMillis() - request.receivedAtMillis),
                )
            }
        }
    }
    val canSubmit = allQuestionsAnswered(request.questions, answers) && remainingMillis != 0L
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Codex 需要你的回答", style = MaterialTheme.typography.titleMedium)
            request.questions.forEach { question ->
                Text(question.header, style = MaterialTheme.typography.labelLarge)
                Text(question.question)
                if (question.options.isNotEmpty()) {
                    question.options.forEach { option ->
                        val selected = question.id !in otherQuestions &&
                            answers[question.id].orEmpty().singleOrNull() == option.label
                        Surface(
                            onClick = {
                                otherQuestions = otherQuestions - question.id
                                answers = answers + (question.id to listOf(option.label))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected, onClick = null)
                                Column(Modifier.weight(1f)) {
                                    Text(option.label, style = MaterialTheme.typography.labelLarge)
                                    if (option.description.isNotBlank()) {
                                        Text(
                                            option.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (question.options.isEmpty() || question.allowsOther) {
                    val selected = question.options.isEmpty() || question.id in otherQuestions
                    if (question.options.isNotEmpty()) {
                        Surface(
                            onClick = {
                                otherQuestions = otherQuestions + question.id
                                answers = answers + (question.id to emptyList())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected, onClick = null)
                                Text("其他回答", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    if (selected) {
                        OutlinedTextField(
                            value = answers[question.id].orEmpty().singleOrNull().orEmpty(),
                            onValueChange = { value -> answers = answers + (question.id to listOf(value)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (question.isSecret) "保密回答" else "回答") },
                            visualTransformation = if (question.isSecret) {
                                PasswordVisualTransformation()
                            } else {
                                androidx.compose.ui.text.input.VisualTransformation.None
                            },
                        )
                    }
                }
            }
            remainingMillis?.let {
                Text(
                    if (it > 0L) "将在 ${(it + 999L) / 1_000L} 秒后自动处理" else "该请求正在自动处理",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { onSubmit(answers) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("提交回答") }
        }
    }
}

@Composable
private fun McpElicitationCard(
    request: PendingServerRequest.McpElicitation,
    onDecision: (String, String?) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var content by rememberSaveable(request.requestKey) { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${request.serverName} 请求确认", style = MaterialTheme.typography.titleMedium)
            Text(request.message)
            request.requestedSchema?.let {
                Text("该服务需要结构化响应", style = MaterialTheme.typography.labelLarge)
                ExpandableTechnicalDetails(it)
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("响应内容（JSON 格式）") },
                    minLines = 3,
                )
            }
            request.url?.let { url ->
                OutlinedButton(onClick = { uriHandler.openUri(url) }, modifier = Modifier.fillMaxWidth()) {
                    Text("打开授权页面")
                }
            }
            Button(
                onClick = { onDecision("accept", content.ifBlank { null }) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("接受") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onDecision("decline", null) }, modifier = Modifier.weight(1f)) {
                    Text("拒绝")
                }
                TextButton(onClick = { onDecision("cancel", null) }, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
            }
        }
    }
}

@Composable
private fun PermissionsCard(
    request: PendingServerRequest.Permissions,
    onDecision: (grant: Boolean, session: Boolean, strictAutoReview: Boolean) -> Unit,
) {
    var strictAutoReview by rememberSaveable(request.requestKey) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("权限请求", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            request.reason?.let { Text(it) }
            Text(request.cwd, style = MaterialTheme.typography.labelSmall)
            PermissionSummary(request.requestedPermissions)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("本回合后续命令严格自动审查", modifier = Modifier.weight(1f))
                Switch(checked = strictAutoReview, onCheckedChange = { strictAutoReview = it })
            }
            Button(
                onClick = { onDecision(true, false, strictAutoReview) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("允许一次") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onDecision(true, true, false) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("本会话允许")
                }
                TextButton(onClick = { onDecision(false, false, false) }, modifier = Modifier.weight(1f)) {
                    Text("拒绝")
                }
            }
        }
    }
}

@Composable
internal fun ConversationComposer(
    isRunning: Boolean,
    plan: TurnPlan?,
    activeTurnId: String?,
    message: String,
    attachments: List<DraftAttachment>,
    modelLabel: String,
    resources: List<FeatureEntry>,
    quickPhrases: List<QuickPhrase>,
    isLoadingResources: Boolean,
    isSending: Boolean,
    sendEnabled: Boolean,
    placeholder: String,
    commands: List<ComposerCommand>,
    onLoadResources: (Boolean) -> Unit,
    onAddQuickPhrase: (String) -> Unit,
    onUpdateQuickPhrase: (String, String) -> Unit,
    onDeleteQuickPhrase: (String) -> Unit,
    onMoveQuickPhraseUp: (String) -> Unit,
    onMoveQuickPhraseDown: (String) -> Unit,
    onMessageChange: (String) -> Unit,
    onAddAttachment: (UserInputPart, String?) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onInputError: (Throwable) -> Unit,
    onOpenOptions: () -> Unit,
    onCommand: (ComposerCommand) -> Unit,
    onSend: () -> Unit,
    onStop: (() -> Unit)?,
) {
    var dialog by remember { mutableStateOf<ComposerDialog?>(null) }
    var addMenuExpanded by remember { mutableStateOf(false) }
    var showQuickPhrases by remember { mutableStateOf(false) }
    var showQuickPhraseManager by remember { mutableStateOf(false) }
    var isAddingImage by remember { mutableStateOf(false) }
    var composerValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = message,
                selection = TextRange(message.length),
            ),
        )
    }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val composerScope = rememberCoroutineScope()
    LaunchedEffect(message) {
        if (message != composerValue.text) {
            composerValue = TextFieldValue(
                text = message,
                selection = TextRange(message.length),
            )
        }
    }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            composerScope.launch {
                isAddingImage = true
                runCatching { uri.toImageDataUrl(context) }
                    .onSuccess { onAddAttachment(UserInputPart.Image(it), uri.toString()) }
                    .onFailure(onInputError)
                isAddingImage = false
            }
        }
    }
    val activeToken = composerValue.selection.takeIf { it.collapsed }?.let {
        activeComposerToken(composerValue.text, it.start)
    }
    val paletteEntries = remember(activeToken, resources, commands) {
        composerPaletteEntries(activeToken, resources, commands)
    }
    LaunchedEffect(activeToken?.trigger) {
        if (activeToken?.trigger == '$' || activeToken?.trigger == '@') onLoadResources(false)
    }
    val hasInput = composerValue.text.isNotBlank() || attachments.isNotEmpty()

    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 12.dp),
    ) {
        if (isRunning) {
            ActivePlanCard(
                plan = plan,
                activeTurnId = activeTurnId,
            )
        }
        if (activeToken != null && (paletteEntries.isNotEmpty() || isLoadingResources)) {
            ComposerPalette(
                entries = paletteEntries,
                isLoading = isLoadingResources,
                onSelect = { entry ->
                    val nextMessage = removeActiveComposerToken(composerValue.text, activeToken)
                    composerValue = composerValue.copy(
                        text = nextMessage,
                        selection = TextRange(activeToken.start.coerceAtMost(nextMessage.length)),
                        composition = null,
                    )
                    onMessageChange(nextMessage)
                    when (entry) {
                        is ComposerPaletteEntry.Resource -> onAddAttachment(entry.input, null)
                        is ComposerPaletteEntry.Command -> when (entry.command) {
                            ComposerCommand.PHRASE -> {
                                showQuickPhrases = true
                                addMenuExpanded = true
                            }
                            ComposerCommand.SETTINGS -> {
                                onLoadResources(false)
                                onOpenOptions()
                            }
                            ComposerCommand.ATTACH -> addMenuExpanded = true
                            else -> onCommand(entry.command)
                        }
                    }
                },
            )
            Spacer(Modifier.height(6.dp))
        }
        if (attachments.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                attachments.forEach { attachment ->
                    DraftAttachmentChip(
                        attachment = attachment,
                        onRemove = { onRemoveAttachment(attachment.id) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        val compactEndExtra = (if (isAddingImage) 28.dp else 0.dp) +
            (if (isRunning && hasInput) 40.dp else 0.dp)
        MessageComposer(
            value = composerValue,
            onValueChange = {
                composerValue = it
                onMessageChange(it.text)
            },
            placeholder = placeholder,
            enabled = !isSending,
            compactEndExtra = compactEndExtra,
            leadingContent = {
                ComposerAddMenu(
                    expanded = addMenuExpanded,
                    onExpandedChange = {
                        addMenuExpanded = it
                        if (!it) showQuickPhrases = false
                    },
                    showQuickPhrases = showQuickPhrases,
                    onShowQuickPhrases = { showQuickPhrases = it },
                    quickPhrases = quickPhrases,
                    enabled = !isSending,
                    onPickImage = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onAddImageAddress = { dialog = ComposerDialog.ImageAddress },
                    onManageQuickPhrases = {
                        addMenuExpanded = false
                        showQuickPhrases = false
                        showQuickPhraseManager = true
                    },
                    onSelectQuickPhrase = { phrase ->
                        val nextValue = insertQuickPhrase(composerValue, phrase.content)
                        composerValue = nextValue
                        onMessageChange(nextValue.text)
                        addMenuExpanded = false
                        showQuickPhrases = false
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                    onInsertTrigger = { trigger ->
                        val nextValue = insertComposerTrigger(composerValue, trigger)
                        composerValue = nextValue
                        onMessageChange(nextValue.text)
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                )
            },
            expandedContent = {
                TextButton(
                    onClick = onOpenOptions,
                    enabled = !isRunning && !isSending,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(modelLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            trailingContent = {
                if (isAddingImage) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                if (isRunning && hasInput) {
                    IconButton(
                        onClick = requireNotNull(onStop),
                        enabled = !isSending,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = "停止")
                    }
                }
                val stopsActiveTurn = isRunning && !hasInput
                val action = if (stopsActiveTurn) requireNotNull(onStop) else onSend
                MessageComposerActionButton(
                    onClick = action,
                    enabled = if (stopsActiveTurn) {
                        !isSending
                    } else {
                        hasInput && sendEnabled && !isSending
                    },
                    action = if (stopsActiveTurn) {
                        MessageComposerAction.STOP
                    } else {
                        MessageComposerAction.SEND
                    },
                    isLoading = isSending,
                    contentDescription = if (stopsActiveTurn) "停止" else "发送",
                )
            },
            shadowElevation = 6.dp,
            focusRequester = focusRequester,
        )
    }

    if (showQuickPhraseManager) {
        QuickPhraseManagerDialog(
            phrases = quickPhrases,
            onDismiss = { showQuickPhraseManager = false },
            onAdd = onAddQuickPhrase,
            onUpdate = onUpdateQuickPhrase,
            onDelete = onDeleteQuickPhrase,
            onMoveUp = onMoveQuickPhraseUp,
            onMoveDown = onMoveQuickPhraseDown,
        )
    }

    when (dialog) {
        ComposerDialog.ImageAddress -> ImageAddressDialog(
            onDismiss = { dialog = null },
            onAdd = {
                onAddAttachment(it, null)
                dialog = null
            },
        )

        null -> Unit
    }
}

internal fun composerModelLabel(
    model: String?,
    reasoningEffort: String?,
    models: List<ModelOption>,
): String {
    val selectedModel = models.firstOrNull { it.model == model }
    val effortLabel = reasoningEffort?.let { effort ->
        selectedModel?.reasoningEfforts?.firstOrNull { it.value == effort }?.label ?: effort
    }
    return listOfNotNull(
        selectedModel?.displayName ?: model ?: "默认模型",
        effortLabel,
    ).joinToString(" · ")
}

@Composable
private fun ActivePlanCard(plan: TurnPlan?, activeTurnId: String?) {
    val canExpand = plan?.steps?.isNotEmpty() == true
    val activeStep = plan?.steps?.firstOrNull { it.status == PlanStepStatus.IN_PROGRESS }
    var expanded by rememberSaveable(activeTurnId) { mutableStateOf(false) }
    var expansionPhase by remember(activeTurnId) {
        mutableStateOf(PlanExpansionPhase.COLLAPSED)
    }
    val bodyAlpha by animateFloatAsState(
        targetValue = if (expansionPhase.bodyVisible) 1f else 0f,
        animationSpec = tween(ACTIVE_PLAN_CONTENT_FADE_DURATION_MILLIS),
        label = "工作计划内容透明度",
    )
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(ACTIVE_PLAN_CONTENT_FADE_DURATION_MILLIS),
        label = "工作计划展开箭头",
    )

    LaunchedEffect(expanded) {
        expansionPhase = expansionPhase.startTransition(expanded)
        when (expansionPhase) {
            PlanExpansionPhase.EXPANDING -> {
                delay(ACTIVE_PLAN_SIZE_ANIMATION_DURATION_MILLIS.toLong())
                expansionPhase = expansionPhase.settle()
            }

            PlanExpansionPhase.COLLAPSING -> {
                delay(ACTIVE_PLAN_CONTENT_FADE_DURATION_MILLIS.toLong())
                expansionPhase = expansionPhase.settle()
            }

            PlanExpansionPhase.COLLAPSED,
            PlanExpansionPhase.EXPANDED,
            -> Unit
        }
    }

    Surface(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.animateContentSize(
                animationSpec = tween(ACTIVE_PLAN_SIZE_ANIMATION_DURATION_MILLIS),
            ),
        ) {
            Row(
                modifier = Modifier
                    .then(if (expansionPhase.bodyIncluded) Modifier.fillMaxWidth() else Modifier)
                    .clickable(enabled = canExpand, role = Role.Button) { expanded = !expanded }
                    .semantics {
                        if (canExpand) stateDescription = if (expanded) "已展开" else "已收起"
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = activeStep?.text ?: "正在处理",
                    modifier = Modifier.widthIn(max = 400.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (canExpand) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).rotate(arrowRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (expansionPhase.bodyIncluded && plan != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        .graphicsLayer { alpha = bodyAlpha },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    plan.explanation?.takeIf(String::isNotBlank)?.let { explanation ->
                        Text(
                            text = explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    plan.steps.forEach { step ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            val (icon, description) = when (step.status) {
                                PlanStepStatus.COMPLETED -> Icons.Rounded.Check to "已完成"
                                PlanStepStatus.IN_PROGRESS -> Icons.Rounded.HourglassTop to "进行中"
                                PlanStepStatus.PENDING -> Icons.Rounded.RadioButtonUnchecked to "待处理"
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = description,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = step.text,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val ACTIVE_PLAN_SIZE_ANIMATION_DURATION_MILLIS = 220
private const val ACTIVE_PLAN_CONTENT_FADE_DURATION_MILLIS = 100

@Composable
private fun ComposerAddMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    showQuickPhrases: Boolean,
    onShowQuickPhrases: (Boolean) -> Unit,
    quickPhrases: List<QuickPhrase>,
    enabled: Boolean,
    onPickImage: () -> Unit,
    onAddImageAddress: () -> Unit,
    onManageQuickPhrases: () -> Unit,
    onSelectQuickPhrase: (QuickPhrase) -> Unit,
    onInsertTrigger: (Char) -> Unit,
) {
    Box {
        MessageComposerAddButton(
            onClick = {
                onShowQuickPhrases(false)
                onExpandedChange(!expanded)
            },
            enabled = enabled,
            contentDescription = "添加内容",
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.widthIn(min = 280.dp, max = 400.dp),
            properties = PopupProperties(focusable = false),
        ) {
            if (showQuickPhrases) {
                QuickPhraseMenuContent(
                    phrases = quickPhrases,
                    onBack = { onShowQuickPhrases(false) },
                    onManage = onManageQuickPhrases,
                    onSelect = onSelectQuickPhrase,
                )
            } else {
                DropdownMenuItem(
                    text = { Text("快捷短语") },
                    leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                    onClick = { onShowQuickPhrases(true) },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("设备图片") },
                    leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                    onClick = {
                        onExpandedChange(false)
                        onPickImage()
                    },
                )
                DropdownMenuItem(
                    text = { Text("图片地址") },
                    leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                    onClick = {
                        onExpandedChange(false)
                        onAddImageAddress()
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Skill") },
                    leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                    onClick = {
                        onExpandedChange(false)
                        onInsertTrigger('$')
                    },
                )
                DropdownMenuItem(
                    text = { Text("App 与 Plugin") },
                    leadingIcon = { Icon(Icons.Rounded.Apps, contentDescription = null) },
                    onClick = {
                        onExpandedChange(false)
                        onInsertTrigger('@')
                    },
                )
            }
        }
    }
}

@Composable
private fun ImageAddressDialog(
    onDismiss: () -> Unit,
    onAdd: (UserInputPart) -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(ManualAttachment.IMAGE_URL) }
    var value by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加图片地址") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionRow(
                    values = ManualAttachment.entries,
                    selected = kind,
                    label = { if (it == ManualAttachment.IMAGE_URL) "图片 URL" else "服务端图片" },
                    onSelect = { kind = it },
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            if (kind == ManualAttachment.IMAGE_URL) "URL 或 data URL" else "服务端绝对路径",
                        )
                    },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(
                        if (kind == ManualAttachment.IMAGE_URL) UserInputPart.Image(value.trim())
                        else UserInputPart.LocalImage(value.trim()),
                    )
                },
                enabled = value.isNotBlank(),
            ) {
                Text("添加")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun TurnOptionsSheet(
    value: TurnOptions,
    models: List<ModelOption>,
    onDismiss: () -> Unit,
    onSave: (TurnOptions) -> Unit,
) {
    var options by remember(value) { mutableStateOf(value) }
    var writableRoots by remember(value) { mutableStateOf(value.sandboxWritableRoots.joinToString("\n")) }
    var showAdvanced by rememberSaveable(value) { mutableStateOf(false) }
    val selectableModels = models.filterNot(ModelOption::hidden)
    val model = selectableModels.firstOrNull { it.model == options.model }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("下一轮设置", style = MaterialTheme.typography.titleLarge)
            Text(
                "设置仅用于下一次发送，服务端确认接收后恢复会话默认值",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selectableModels.isNotEmpty()) {
                Text("模型", style = MaterialTheme.typography.labelLarge)
                OptionRow(
                    values = listOf<ModelOption?>(null) + selectableModels,
                    selected = model,
                    label = { it?.displayName ?: "会话默认" },
                    onSelect = { selected ->
                        options = options.copy(
                            model = selected?.model,
                            serviceTier = selected?.defaultServiceTier,
                            reasoningEffort = selected?.defaultReasoningEffort,
                        )
                    },
                )
            }
            model?.reasoningEfforts?.takeIf(List<*>::isNotEmpty)?.let { efforts ->
                Text("推理强度", style = MaterialTheme.typography.labelLarge)
                OptionRow(
                    values = listOf<String?>(null) + efforts.map { it.value },
                    selected = options.reasoningEffort,
                    label = { effort -> efforts.firstOrNull { it.value == effort }?.label ?: "默认" },
                    onSelect = { options = options.copy(reasoningEffort = it) },
                )
            }
            Text("审批者", style = MaterialTheme.typography.labelLarge)
            OptionRow(
                values = listOf<ApprovalReviewer?>(null) + ApprovalReviewer.entries,
                selected = options.approvalsReviewer,
                label = { it?.let { value -> stringResource(value.displayLabelRes) }
                    ?: stringResource(R.string.common_default) },
                onSelect = { options = options.copy(approvalsReviewer = it) },
            )
            options.approvalsReviewer?.let { SettingDescription(stringResource(it.displayDescriptionRes)) }
            Text("审批策略", style = MaterialTheme.typography.labelLarge)
            OptionRow(
                values = listOf<ApprovalPolicy?>(null) + ApprovalPolicy.entries,
                selected = options.approvalPolicy,
                label = { it?.let { value -> stringResource(value.displayLabelRes) }
                    ?: stringResource(R.string.common_default) },
                onSelect = { options = options.copy(approvalPolicy = it) },
            )
            options.approvalPolicy?.let { SettingDescription(stringResource(it.displayDescriptionRes)) }
            Text("沙箱", style = MaterialTheme.typography.labelLarge)
            OptionRow(
                values = listOf<SandboxMode?>(null) + SandboxMode.entries,
                selected = options.sandboxMode,
                label = { it?.let { value -> stringResource(value.displayLabelRes) }
                    ?: stringResource(R.string.common_default) },
                onSelect = { options = options.copy(sandboxMode = it) },
            )
            options.sandboxMode?.let {
                SettingDescription(
                    stringResource(it.displayDescriptionRes),
                    critical = it == SandboxMode.DANGER_FULL_ACCESS,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showAdvanced) "收起高级设置" else "展开高级设置")
            }
            if (showAdvanced) {
                OutlinedTextField(
                    value = options.cwd.orEmpty(),
                    onValueChange = { options = options.copy(cwd = it.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("工作目录覆盖，可留空") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = options.model.orEmpty(),
                    onValueChange = { options = options.copy(model = it.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型 ID，可留空") },
                    singleLine = true,
                )
                model?.serviceTiers?.takeIf(List<*>::isNotEmpty)?.let { tiers ->
                    Text("服务层级", style = MaterialTheme.typography.labelLarge)
                    OptionRow(
                        values = listOf<String?>(null) + tiers.map { it.value },
                        selected = options.serviceTier,
                        label = { tier -> tiers.firstOrNull { it.value == tier }?.label ?: "默认" },
                        onSelect = { options = options.copy(serviceTier = it) },
                    )
                }
                OutlinedTextField(
                    value = options.serviceTier.orEmpty(),
                    onValueChange = { options = options.copy(serviceTier = it.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("服务层级，可留空") },
                    singleLine = true,
                )
                Text("推理摘要", style = MaterialTheme.typography.labelLarge)
                OptionRow(
                    values = listOf<ReasoningSummary?>(null) + ReasoningSummary.entries,
                    selected = options.reasoningSummary,
                    label = { it?.let { value -> stringResource(value.displayLabelRes) }
                        ?: stringResource(R.string.common_default) },
                    onSelect = { options = options.copy(reasoningSummary = it) },
                )
                if (model == null || model.supportsPersonality) {
                    Text("人格", style = MaterialTheme.typography.labelLarge)
                    OptionRow(
                        values = listOf<Personality?>(null) + Personality.entries,
                        selected = options.personality,
                        label = { it?.let { value -> stringResource(value.displayLabelRes) }
                            ?: stringResource(R.string.common_default) },
                        onSelect = { options = options.copy(personality = it) },
                    )
                }
                if (options.sandboxMode == SandboxMode.READ_ONLY || options.sandboxMode == SandboxMode.WORKSPACE_WRITE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("允许网络访问", modifier = Modifier.weight(1f))
                        Switch(
                            checked = options.sandboxNetworkAccess,
                            onCheckedChange = { options = options.copy(sandboxNetworkAccess = it) },
                        )
                    }
                }
                if (options.sandboxMode == SandboxMode.WORKSPACE_WRITE) {
                    OutlinedTextField(
                        value = writableRoots,
                        onValueChange = { writableRoots = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("额外可写绝对路径，每行一个") },
                        minLines = 2,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("排除 TMPDIR", modifier = Modifier.weight(1f))
                        Switch(
                            checked = options.sandboxExcludeTmpdirEnvVar,
                            onCheckedChange = { options = options.copy(sandboxExcludeTmpdirEnvVar = it) },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("排除 /tmp", modifier = Modifier.weight(1f))
                        Switch(
                            checked = options.sandboxExcludeSlashTmp,
                            onCheckedChange = { options = options.copy(sandboxExcludeSlashTmp = it) },
                        )
                    }
                }
                OutlinedTextField(
                    value = options.outputSchema.orEmpty(),
                    onValueChange = { options = options.copy(outputSchema = it.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("结构化输出 JSON Schema，可留空") },
                    minLines = 3,
                )
            }
            Button(
                onClick = {
                    onSave(
                        options.copy(
                            sandboxWritableRoots = writableRoots.lineSequence()
                                .map(String::trim)
                                .filter(String::isNotEmpty)
                                .toList(),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("应用设置") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun <T> OptionRow(
    values: List<T>,
    selected: T,
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
private fun SettingDescription(text: String, critical: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (critical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RenameConversationDialog(
    title: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by rememberSaveable(title) { mutableStateOf(title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名会话") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("会话名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                onClick = { onRename(name.trim()) },
                enabled = name.isNotBlank() && name.trim() != title,
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ForkConversationSheet(
    turns: List<String>,
    canForkLatest: Boolean,
    onDismiss: () -> Unit,
    onFork: (String?) -> Unit,
) {
    var lastTurnId by rememberSaveable(turns, canForkLatest) { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("分叉会话", style = MaterialTheme.typography.titleLarge)
            if (turns.isNotEmpty()) {
                Text("选择新会话包含的最后一个回合", style = MaterialTheme.typography.bodyMedium)
                OptionRow(
                    values = if (canForkLatest) listOf<String?>(null) + turns else turns,
                    selected = lastTurnId,
                    label = { turnId ->
                        turnId?.let { "第 ${turns.indexOf(it) + 1} 轮" } ?: "最新回合"
                    },
                    onSelect = { lastTurnId = it },
                )
            } else {
                Text(
                    if (canForkLatest) "从当前会话的最新状态创建分叉" else "当前没有可分叉的已完成回合",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { onFork(lastTurnId) },
                enabled = lastTurnId != null || canForkLatest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("创建分叉会话")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("取消") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ConversationStatisticsSheet(
    usage: TokenUsage?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("对话统计", style = MaterialTheme.typography.titleLarge)
            if (usage == null) {
                Text(
                    "暂无统计数据",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ContextUsageSection(usage)
                HorizontalDivider()
                TokenUsageSection(
                    title = "会话累计",
                    description = "本对话所有模型调用累计消耗的 Token",
                    usage = usage.total,
                )
                HorizontalDivider()
                TokenUsageSection(
                    title = "最近一次模型调用",
                    description = "最近一次调用返回的 Token 明细",
                    usage = usage.last,
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ContextUsageSection(usage: TokenUsage) {
    val contextWindow = usage.modelContextWindow?.takeIf { it > 0 }
    val contextTokens = usage.last.totalTokens
    val ratio = contextWindow?.let { (contextTokens.toFloat() / it).coerceIn(0f, 1f) }
    Text("当前上下文", style = MaterialTheme.typography.titleMedium)
    Text(
        if (contextWindow == null) {
            "已用 ${formatCompactCount(contextTokens)} Token"
        } else {
            "已用 ${formatCompactCount(contextTokens)} / ${formatCompactCount(contextWindow)} Token" +
                " · ${formatPercent(contextTokens.toDouble() / contextWindow * 100.0)}"
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    if (ratio != null) {
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TokenUsageSection(
    title: String,
    description: String,
    usage: TokenUsageBreakdown,
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(
        description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TokenUsageMetric("总计", usage.totalTokens)
    TokenUsageMetric("输入", usage.inputTokens)
    if (usage.cachedInputTokens > 0) TokenUsageMetric("缓存输入", usage.cachedInputTokens)
    TokenUsageMetric("输出", usage.outputTokens)
    if (usage.reasoningOutputTokens > 0) TokenUsageMetric("推理输出", usage.reasoningOutputTokens)
}

@Composable
private fun TokenUsageMetric(label: String, value: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${formatCount(value)} Token", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ReviewDialog(
    onDismiss: () -> Unit,
    onSubmit: (ReviewTarget, ReviewDelivery) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(ReviewType.UNCOMMITTED) }
    var value by rememberSaveable { mutableStateOf("") }
    var detached by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("代码审查") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OptionRow(
                    values = ReviewType.entries,
                    selected = type,
                    label = { it.label },
                    onSelect = { type = it },
                )
                if (type != ReviewType.UNCOMMITTED) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(type.inputLabel) },
                        minLines = if (type == ReviewType.CUSTOM) 3 else 1,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("在独立会话中审查", modifier = Modifier.weight(1f))
                    Switch(checked = detached, onCheckedChange = { detached = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = when (type) {
                        ReviewType.UNCOMMITTED -> ReviewTarget.UncommittedChanges
                        ReviewType.BASE_BRANCH -> ReviewTarget.BaseBranch(value.trim())
                        ReviewType.COMMIT -> ReviewTarget.Commit(value.trim())
                        ReviewType.CUSTOM -> ReviewTarget.Custom(value.trim())
                    }
                    onSubmit(target, if (detached) ReviewDelivery.DETACHED else ReviewDelivery.INLINE)
                },
                enabled = type == ReviewType.UNCOMMITTED || value.isNotBlank(),
            ) { Text("开始") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun GoalDialog(
    goal: ThreadGoal?,
    onDismiss: () -> Unit,
    onSave: (String, ThreadGoalStatus, Long?) -> Unit,
    onClear: () -> Unit,
) {
    var objective by rememberSaveable(goal?.objective) { mutableStateOf(goal?.objective.orEmpty()) }
    var budget by rememberSaveable(goal?.tokenBudget) { mutableStateOf(goal?.tokenBudget?.toString().orEmpty()) }
    var status by rememberSaveable(goal?.status) { mutableStateOf(goal?.status ?: ThreadGoalStatus.ACTIVE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("目标管理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = objective,
                    onValueChange = { objective = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("目标") },
                    minLines = 3,
                )
                Text("状态", style = MaterialTheme.typography.labelLarge)
                OptionRow(
                    values = ThreadGoalStatus.entries,
                    selected = status,
                    label = { stringResource(it.displayLabelRes) },
                    onSelect = { status = it },
                )
                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Token 预算，可留空") },
                    singleLine = true,
                )
                if (goal != null) TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("清除目标") }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(objective.trim(), status, budget.toLongOrNull()) }, enabled = objective.isNotBlank()) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private sealed interface ChatDialog {
    data object Rename : ChatDialog
    data object Fork : ChatDialog
    data object Review : ChatDialog
    data object Goal : ChatDialog
    data object Statistics : ChatDialog
}

private enum class ComposerDialog {
    ImageAddress,
}

private enum class ManualAttachment {
    IMAGE_URL,
    LOCAL_IMAGE,
}

private enum class ReviewType(val label: String, val inputLabel: String) {
    UNCOMMITTED("未提交修改", ""),
    BASE_BRANCH("相对分支", "基础分支"),
    COMMIT("提交", "提交 SHA"),
    CUSTOM("自定义", "审查说明"),
}
