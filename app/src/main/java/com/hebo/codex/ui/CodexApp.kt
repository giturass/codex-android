package com.hebo.codex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DismissibleNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.hebo.codex.ConversationTarget
import com.hebo.codex.R
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.domain.DraftRepository
import com.hebo.codex.domain.QuickPhraseRepository
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.ui.components.CodexNavigationDrawer
import com.hebo.codex.ui.components.CodexNavigationDrawerWidth
import com.hebo.codex.ui.components.DrawerActiveTarget
import com.hebo.codex.ui.components.UserFacingErrorDialog
import com.hebo.codex.ui.components.navigationAttention
import com.hebo.codex.ui.ratelimits.RateLimitsUiState
import com.hebo.codex.ui.ratelimits.RateLimitsViewModel
import com.hebo.codex.ui.quickphrases.QuickPhraseViewModel
import com.hebo.codex.ui.screens.chat.ChatScreen
import com.hebo.codex.ui.screens.chat.ChatViewModel
import com.hebo.codex.ui.screens.home.HomeEvent
import com.hebo.codex.ui.screens.home.HomeScreen
import com.hebo.codex.ui.screens.home.HomeViewModel
import com.hebo.codex.ui.screens.home.NewTaskEvent
import com.hebo.codex.ui.screens.home.NewTaskViewModel
import com.hebo.codex.ui.screens.proxies.ProxyEditorScreen
import com.hebo.codex.ui.screens.proxies.ProxyEditorViewModel
import com.hebo.codex.ui.screens.proxies.ProxyListScreen
import com.hebo.codex.ui.screens.proxies.ProxyListViewModel
import com.hebo.codex.ui.screens.hub.ServerHubScreen
import com.hebo.codex.ui.screens.hub.ServerHubViewModel
import com.hebo.codex.ui.screens.servers.ServerEditorScreen
import com.hebo.codex.ui.screens.servers.ServerEditorViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable

private val MainContentMinWidth = 600.dp
private val WideDrawerThreshold = CodexNavigationDrawerWidth + MainContentMinWidth

@Serializable
internal data class NewTaskRoute(
    val serverId: String? = null,
    val cwd: String? = null,
)

@Serializable
private data class ServerEditorRoute(val serverId: String? = null)

@Serializable
private data object ProxyListRoute

@Serializable
private data class ProxyEditorRoute(val proxyId: String? = null)

@Serializable
private data class ChatRoute(
    val serverId: String,
    val threadId: String,
    val announcement: String? = null,
)

@Serializable
private data class ServerHubRoute(val serverId: String, val cwd: String? = null)

@Composable
fun CodexApp(
    repository: CodexRepository,
    draftRepository: DraftRepository,
    quickPhraseRepository: QuickPhraseRepository,
    notificationTargets: Flow<ConversationTarget> = emptyFlow(),
    navController: NavHostController = rememberNavController(),
) {
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(repository, draftRepository),
    )
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val quickPhraseViewModel: QuickPhraseViewModel = viewModel(
        factory = QuickPhraseViewModel.factory(quickPhraseRepository),
    )
    val quickPhraseState by quickPhraseViewModel.state.collectAsStateWithLifecycle()
    val rateLimitsViewModel: RateLimitsViewModel = viewModel(factory = RateLimitsViewModel.factory(repository))
    val rateLimitsStates by rateLimitsViewModel.state.collectAsStateWithLifecycle()
    val modalDrawerState = rememberDrawerState(DrawerValue.Closed)
    val wideDrawerState = rememberDrawerState(DrawerValue.Open)
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    var technicalError by remember { mutableStateOf<UserFacingError?>(null) }
    val detailsLabel = stringResource(R.string.common_details)
    val scope = rememberCoroutineScope()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val activeDrawerTarget = currentBackStackEntry?.arguments?.let { arguments ->
        val serverId = arguments.getString("serverId") ?: homeState.activeServerId
        when {
            serverId == null -> null
            arguments.getString("threadId") != null -> DrawerActiveTarget.Thread(
                serverId = serverId,
                threadId = requireNotNull(arguments.getString("threadId")),
            )

            else -> null
        }
    }

    suspend fun showUserFacingError(error: UserFacingError) {
        val result = snackbarHostState.showSnackbar(
            message = error.summary,
            actionLabel = error.technicalDetails?.let { detailsLabel },
        )
        if (result == SnackbarResult.ActionPerformed) technicalError = error
    }

    fun openNewTask(
        serverId: String? = null,
        cwd: String? = null,
    ) {
        navController.navigate(
            NewTaskRoute(
                serverId = serverId,
                cwd = cwd?.takeIf(String::isNotBlank),
            ),
        ) {
            popUpTo<NewTaskRoute> { inclusive = true }
        }
    }

    fun openConversation(serverId: String, threadId: String, announcement: String? = null) {
        navController.navigate(ChatRoute(serverId, threadId, announcement)) {
            popUpTo<NewTaskRoute> { inclusive = false }
            launchSingleTop = true
        }
    }

    fun replaceNewTaskWithConversation(serverId: String, threadId: String) {
        navController.navigate(ChatRoute(serverId, threadId)) {
            popUpTo<NewTaskRoute> { inclusive = true }
            launchSingleTop = true
        }
    }

    LaunchedEffect(homeViewModel) {
        homeViewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenConversation -> openConversation(event.serverId, event.threadId)
                is HomeEvent.ConversationRemoved -> {
                    val arguments = navController.currentBackStackEntry?.arguments
                    val currentServerId = arguments?.getString("serverId")
                    val currentThreadId = arguments?.getString("threadId")
                    if (currentServerId == event.serverId && currentThreadId == event.threadId) {
                        openNewTask(event.serverId)
                    }
                }
            }
        }
    }
    LaunchedEffect(notificationTargets) {
        notificationTargets.collect { target ->
            homeViewModel.selectServer(target.serverId)
            openConversation(target.serverId, target.threadId)
        }
    }
    LaunchedEffect(homeState.errorMessage) {
        homeState.errorMessage?.let { error ->
            showUserFacingError(error)
            homeViewModel.clearError()
        }
    }
    LaunchedEffect(quickPhraseState.error) {
        quickPhraseState.error?.let { error ->
            showUserFacingError(error)
            quickPhraseViewModel.clearError()
        }
    }
    LaunchedEffect(homeState.serverNotices.lastOrNull()?.id) {
        homeState.serverNotices.lastOrNull()?.let { snackbarHostState.showSnackbar(it.message) }
    }

    BoxWithConstraints {
        val isWideLayout = maxWidth >= WideDrawerThreshold
        val drawerState = if (isWideLayout) wideDrawerState else modalDrawerState
        val wideDrawerOffset = wideDrawerState.currentOffset
        val wideDrawerVisibleWidth = if (wideDrawerOffset.isNaN()) {
            if (wideDrawerState.isOpen) CodexNavigationDrawerWidth else 0.dp
        } else {
            with(density) {
                val drawerWidthPx = CodexNavigationDrawerWidth.toPx()
                (drawerWidthPx + wideDrawerOffset)
                    .coerceIn(0f, drawerWidthPx)
                    .toDp()
            }
        }

        fun dismissModalDrawer() {
            if (!isWideLayout) scope.launch { drawerState.close() }
        }

        fun toggleDrawer() {
            scope.launch {
                if (drawerState.isOpen) drawerState.close() else drawerState.open()
            }
        }

        LaunchedEffect(drawerState.targetValue) {
            if (drawerState.targetValue == DrawerValue.Open) {
                focusManager.clearFocus()
                keyboardController?.hide()
            }
        }

        val drawerContent: @Composable () -> Unit = {
            CodexNavigationDrawer(
                servers = homeState.servers,
                activeServer = homeState.activeServer,
                connectionStatuses = homeState.connectionStatuses,
                projects = homeState.recentProjects,
                threads = homeState.threads,
                recentThreads = homeState.recentThreads,
                drafts = homeState.activeDrafts,
                threadAttention = homeState.threadAttention,
                searchTerm = homeState.searchTerm,
                showArchived = homeState.showArchived,
                isLoading = homeState.isLoading,
                isRefreshing = homeState.isRefreshing,
                isLoadingMore = homeState.isLoadingMore,
                hasMore = homeState.nextCursor != null,
                activeTarget = activeDrawerTarget,
                isWideLayout = isWideLayout,
                modifier = if (isWideLayout) Modifier.width(CodexNavigationDrawerWidth) else Modifier,
                onNewConversation = {
                    dismissModalDrawer()
                    openNewTask(homeState.activeServerId)
                },
                onOpenConversation = { threadId ->
                    dismissModalDrawer()
                    homeState.activeServerId?.let { openConversation(it, threadId) }
                },
                onDeleteDraft = homeViewModel::deleteDraft,
                onNewProjectTask = { cwd ->
                    dismissModalDrawer()
                    openNewTask(homeState.activeServerId, cwd)
                },
                onSearch = homeViewModel::search,
                onShowArchived = homeViewModel::setArchived,
                onShowRecent = homeViewModel::showRecent,
                onRefresh = homeViewModel::refresh,
                onLoadMore = homeViewModel::loadMore,
                onRenameConversation = homeViewModel::renameConversation,
                onForkConversation = homeViewModel::forkConversation,
                onArchiveConversation = homeViewModel::archiveConversation,
                onDeleteConversation = homeViewModel::deleteConversation,
                onSelectServer = { serverId ->
                    dismissModalDrawer()
                    openNewTask(serverId)
                },
                onReconnectServer = homeViewModel::reconnectServer,
                onEditServer = { serverId ->
                    dismissModalDrawer()
                    navController.navigate(ServerEditorRoute(serverId))
                },
                onRemoveServer = { serverId ->
                    homeViewModel.removeServer(serverId)
                    dismissModalDrawer()
                    openNewTask()
                },
                onAddServer = {
                    dismissModalDrawer()
                    navController.navigate(ServerEditorRoute())
                },
                onOpenSettings = { serverId ->
                    dismissModalDrawer()
                    navController.navigate(ServerHubRoute(serverId))
                },
            )
        }
        val content: @Composable () -> Unit = {
            NavHost(
                navController = navController,
                startDestination = NewTaskRoute(),
            ) {
                composable<NewTaskRoute> { entry ->
                    val route = entry.toRoute<NewTaskRoute>()
                    val newTaskViewModel: NewTaskViewModel = viewModel(
                        factory = NewTaskViewModel.factory(
                            repository = repository,
                            draftRepository = draftRepository,
                            serverId = route.serverId,
                            initialCwd = route.cwd,
                        ),
                    )
                    LaunchedEffect(route.serverId, homeState.activeServerId) {
                        val serverId = route.serverId
                        if (serverId != null && serverId != homeState.activeServerId) {
                            homeViewModel.selectServer(serverId)
                        }
                    }
                    LaunchedEffect(newTaskViewModel) {
                        newTaskViewModel.events.collect { event ->
                            when (event) {
                                is NewTaskEvent.OpenConversation -> {
                                    replaceNewTaskWithConversation(event.serverId, event.threadId)
                                }
                            }
                        }
                    }
                    HomeScreen(
                        viewModel = homeViewModel,
                        newTaskViewModel = newTaskViewModel,
                        quickPhraseViewModel = quickPhraseViewModel,
                        rateLimitsState = homeState.activeServerId?.let(rateLimitsStates::get) ?: RateLimitsUiState(),
                        navigationOpen = drawerState.isOpen,
                        onToggleNavigation = ::toggleDrawer,
                        onOpenConversation = { threadId ->
                            homeState.activeServerId?.let { openConversation(it, threadId) }
                        },
                        onAddServer = { navController.navigate(ServerEditorRoute()) },
                        onRefreshRateLimits = {
                            homeState.activeServerId?.let(rateLimitsViewModel::refresh)
                        },
                        onConsumeRateLimitReset = { creditId ->
                            homeState.activeServerId?.let { serverId ->
                                rateLimitsViewModel.consumeRateLimitReset(serverId, creditId)
                            }
                        },
                        onShowError = ::showUserFacingError,
                    )
                }
                composable<ServerEditorRoute> { entry ->
                    val route = entry.toRoute<ServerEditorRoute>()
                    val viewModel: ServerEditorViewModel = viewModel(
                        key = "server-editor:${route.serverId.orEmpty()}",
                        factory = ServerEditorViewModel.factory(repository, route.serverId),
                    )
                    ServerEditorScreen(
                        viewModel = viewModel,
                        onBack = navController::popBackStack,
                        onManageProxies = { navController.navigate(ProxyListRoute) },
                    )
                }
                composable<ProxyListRoute> {
                    val viewModel: ProxyListViewModel = viewModel(
                        key = "proxy-list",
                        factory = ProxyListViewModel.factory(repository),
                    )
                    ProxyListScreen(
                        viewModel = viewModel,
                        onBack = navController::popBackStack,
                        onAdd = { navController.navigate(ProxyEditorRoute()) },
                        onEdit = { proxyId -> navController.navigate(ProxyEditorRoute(proxyId)) },
                    )
                }
                composable<ProxyEditorRoute> { entry ->
                    val route = entry.toRoute<ProxyEditorRoute>()
                    val viewModel: ProxyEditorViewModel = viewModel(
                        key = "proxy-editor:${route.proxyId.orEmpty()}",
                        factory = ProxyEditorViewModel.factory(repository, route.proxyId),
                    )
                    ProxyEditorScreen(viewModel = viewModel, onBack = navController::popBackStack)
                }
                composable<ChatRoute> { entry ->
                    val route = entry.toRoute<ChatRoute>()
                    val viewModel: ChatViewModel = viewModel(
                        key = "chat:${route.serverId}:${route.threadId}",
                        factory = ChatViewModel.factory(
                            repository,
                            draftRepository,
                            route.serverId,
                            route.threadId,
                        ),
                    )
                    ChatScreen(
                        viewModel = viewModel,
                        quickPhraseViewModel = quickPhraseViewModel,
                        initialAnnouncement = route.announcement,
                        rateLimitsState = rateLimitsStates[route.serverId] ?: RateLimitsUiState(),
                        navigationAttention = navigationAttention(
                            attentionByThread = homeState.threadAttention,
                            currentThreadId = route.threadId,
                        ),
                        navigationOpen = drawerState.isOpen,
                        onToggleNavigation = ::toggleDrawer,
                        onNewTask = { cwd -> openNewTask(route.serverId, cwd) },
                        onOpenConversation = { threadId, announcement ->
                            openConversation(route.serverId, threadId, announcement)
                        },
                        onRefreshRateLimits = { rateLimitsViewModel.refresh(route.serverId) },
                        onConsumeRateLimitReset = { creditId ->
                            rateLimitsViewModel.consumeRateLimitReset(route.serverId, creditId)
                        },
                    )
                }
                composable<ServerHubRoute> { entry ->
                    val route = entry.toRoute<ServerHubRoute>()
                    val viewModel: ServerHubViewModel = viewModel(
                        key = "hub:${route.serverId}:${route.cwd.orEmpty()}",
                        factory = ServerHubViewModel.factory(repository, route.serverId, route.cwd),
                    )
                    ServerHubScreen(viewModel = viewModel, onBack = navController::popBackStack)
                }
            }
        }

        if (isWideLayout) {
            DismissibleNavigationDrawer(
                drawerState = drawerState,
                drawerContent = drawerContent,
                content = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(end = wideDrawerVisibleWidth),
                    ) {
                        content()
                    }
                },
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = drawerContent,
                content = content,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
    technicalError?.let { error ->
        UserFacingErrorDialog(error = error, onDismiss = { technicalError = null })
    }
}
