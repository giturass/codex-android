package com.hebo.codex.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentNavigationDrawer
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
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
import com.hebo.codex.domain.DraftTarget
import com.hebo.codex.domain.QuickPhraseRepository
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.ui.components.CodexNavigationDrawer
import com.hebo.codex.ui.components.UserFacingErrorDialog
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
import com.hebo.codex.ui.screens.hub.ServerHubScreen
import com.hebo.codex.ui.screens.hub.ServerHubViewModel
import com.hebo.codex.ui.screens.servers.ServerEditorScreen
import com.hebo.codex.ui.screens.servers.ServerEditorViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable

private val PermanentDrawerThreshold = 840.dp

@Serializable
internal data class NewTaskRoute(
    val draftId: String,
    val serverId: String? = null,
    val cwd: String? = null,
)

@Serializable
private data class ServerEditorRoute(val serverId: String? = null)

@Serializable
private data class ChatRoute(val serverId: String, val threadId: String)

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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    var technicalError by remember { mutableStateOf<UserFacingError?>(null) }
    val detailsLabel = stringResource(R.string.common_details)
    val scope = rememberCoroutineScope()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val activeThreadId = currentBackStackEntry?.arguments?.getString("threadId")

    suspend fun showUserFacingError(error: UserFacingError) {
        val result = snackbarHostState.showSnackbar(
            message = error.summary,
            actionLabel = error.technicalDetails?.let { detailsLabel },
        )
        if (result == SnackbarResult.ActionPerformed) technicalError = error
    }

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    fun openDrawer() {
        scope.launch { drawerState.open() }
    }

    fun openNewTask(
        serverId: String? = null,
        cwd: String? = null,
        draftId: String = NewTaskViewModel.newDraftId(),
    ) {
        navController.navigate(
            NewTaskRoute(
                draftId = draftId,
                serverId = serverId,
                cwd = cwd?.takeIf(String::isNotBlank),
            ),
        ) {
            popUpTo<NewTaskRoute> { inclusive = true }
        }
    }

    fun openConversation(serverId: String, threadId: String) {
        navController.navigate(ChatRoute(serverId, threadId)) {
            popUpTo<NewTaskRoute> { inclusive = false }
            launchSingleTop = true
        }
    }

    LaunchedEffect(drawerState.targetValue) {
        if (drawerState.targetValue == DrawerValue.Open) {
            focusManager.clearFocus()
            keyboardController?.hide()
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
        val isPermanent = maxWidth >= PermanentDrawerThreshold
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
                activeThreadId = activeThreadId,
                isPermanent = isPermanent,
                onNewConversation = {
                    closeDrawer()
                    openNewTask(homeState.activeServerId)
                },
                onOpenConversation = { threadId ->
                    closeDrawer()
                    homeState.activeServerId?.let { openConversation(it, threadId) }
                },
                onOpenDraft = { draft ->
                    closeDrawer()
                    when (val target = draft.key.target) {
                        is DraftTarget.NewTask -> openNewTask(
                            serverId = draft.key.serverId,
                            cwd = draft.cwd,
                            draftId = target.id,
                        )

                        is DraftTarget.Thread -> openConversation(draft.key.serverId, target.id)
                    }
                },
                onDeleteDraft = homeViewModel::deleteDraft,
                onNewProjectTask = { cwd ->
                    closeDrawer()
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
                    closeDrawer()
                    openNewTask(serverId)
                },
                onReconnectServer = homeViewModel::reconnectServer,
                onEditServer = { serverId ->
                    closeDrawer()
                    navController.navigate(ServerEditorRoute(serverId))
                },
                onRemoveServer = { serverId ->
                    homeViewModel.removeServer(serverId)
                    closeDrawer()
                    openNewTask()
                },
                onAddServer = {
                    closeDrawer()
                    navController.navigate(ServerEditorRoute())
                },
                onOpenSettings = { serverId ->
                    closeDrawer()
                    navController.navigate(ServerHubRoute(serverId))
                },
            )
        }
        val initialDraftId = rememberSaveable { NewTaskViewModel.newDraftId() }
        val content: @Composable () -> Unit = {
            NavHost(
                navController = navController,
                startDestination = NewTaskRoute(draftId = initialDraftId),
            ) {
                composable<NewTaskRoute> { entry ->
                    val route = entry.toRoute<NewTaskRoute>()
                    val newTaskViewModel: NewTaskViewModel = viewModel(
                        key = "new-task:${route.serverId.orEmpty()}:${route.draftId}",
                        factory = NewTaskViewModel.factory(
                            repository = repository,
                            draftRepository = draftRepository,
                            serverId = route.serverId,
                            draftId = route.draftId,
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
                                    openConversation(event.serverId, event.threadId)
                                }
                            }
                        }
                    }
                    HomeScreen(
                        viewModel = homeViewModel,
                        newTaskViewModel = newTaskViewModel,
                        quickPhraseViewModel = quickPhraseViewModel,
                        rateLimitsState = homeState.activeServerId?.let(rateLimitsStates::get) ?: RateLimitsUiState(),
                        showNavigationIcon = !isPermanent,
                        onOpenNavigation = ::openDrawer,
                        onAddServer = { navController.navigate(ServerEditorRoute()) },
                        onRefreshRateLimits = {
                            homeState.activeServerId?.let(rateLimitsViewModel::refresh)
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
                    ServerEditorScreen(viewModel = viewModel, onBack = navController::popBackStack)
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
                        rateLimitsState = rateLimitsStates[route.serverId] ?: RateLimitsUiState(),
                        showNavigationIcon = !isPermanent,
                        onOpenNavigation = ::openDrawer,
                        onNewTask = { cwd -> openNewTask(route.serverId, cwd) },
                        onOpenConversation = { openConversation(route.serverId, it) },
                        onRefreshRateLimits = { rateLimitsViewModel.refresh(route.serverId) },
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

        if (isPermanent) {
            PermanentNavigationDrawer(drawerContent = drawerContent, content = content)
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
