package com.hebo.codex.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.ConversationTarget
import com.hebo.codex.ui.components.CodexNavigationDrawer
import com.hebo.codex.ui.screens.chat.ChatScreen
import com.hebo.codex.ui.screens.chat.ChatViewModel
import com.hebo.codex.ui.screens.home.HomeEvent
import com.hebo.codex.ui.screens.home.HomeScreen
import com.hebo.codex.ui.screens.home.HomeViewModel
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
private data object NewTaskRoute

@Serializable
private data class ServerEditorRoute(val serverId: String? = null)

@Serializable
private data class ChatRoute(val serverId: String, val threadId: String)

@Serializable
private data class ServerHubRoute(val serverId: String, val cwd: String? = null)

@Composable
fun CodexApp(
    repository: CodexRepository,
    notificationTargets: Flow<ConversationTarget> = emptyFlow(),
    navController: NavHostController = rememberNavController(),
) {
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(repository))
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val activeThreadId = currentBackStackEntry?.arguments?.getString("threadId")
    val newTaskSelected = currentBackStackEntry?.destination?.route == NewTaskRoute::class.qualifiedName

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    fun openNewTask() {
        navController.navigate(NewTaskRoute) {
            popUpTo<NewTaskRoute> { inclusive = false }
            launchSingleTop = true
        }
    }

    fun openConversation(serverId: String, threadId: String) {
        navController.navigate(ChatRoute(serverId, threadId)) {
            popUpTo<NewTaskRoute> { inclusive = false }
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
                        openNewTask()
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
                threadAttention = homeState.threadAttention,
                searchTerm = homeState.searchTerm,
                showArchived = homeState.showArchived,
                isLoading = homeState.isLoading,
                isLoadingMore = homeState.isLoadingMore,
                hasMore = homeState.nextCursor != null,
                activeThreadId = activeThreadId,
                newTaskSelected = newTaskSelected,
                isPermanent = isPermanent,
                onNewConversation = {
                    closeDrawer()
                    openNewTask()
                },
                onOpenConversation = { threadId ->
                    closeDrawer()
                    homeState.activeServerId?.let { openConversation(it, threadId) }
                },
                onOpenProjectSettings = { cwd ->
                    closeDrawer()
                    homeState.activeServerId?.let { navController.navigate(ServerHubRoute(it, cwd)) }
                },
                onSearch = homeViewModel::search,
                onShowArchived = homeViewModel::setArchived,
                onShowRecent = homeViewModel::showRecent,
                onLoadMore = homeViewModel::loadMore,
                onRenameConversation = homeViewModel::renameConversation,
                onForkConversation = homeViewModel::forkConversation,
                onArchiveConversation = homeViewModel::archiveConversation,
                onDeleteConversation = homeViewModel::deleteConversation,
                onSelectServer = { serverId ->
                    homeViewModel.selectServer(serverId)
                    closeDrawer()
                    openNewTask()
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
                onOpenSettings = {
                    closeDrawer()
                    homeState.activeServerId?.let { navController.navigate(ServerHubRoute(it)) }
                },
            )
        }
        val content: @Composable () -> Unit = {
            NavHost(navController = navController, startDestination = NewTaskRoute) {
                composable<NewTaskRoute> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        showNavigationIcon = !isPermanent,
                        onOpenNavigation = { scope.launch { drawerState.open() } },
                        onAddServer = { navController.navigate(ServerEditorRoute()) },
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
                        factory = ChatViewModel.factory(repository, route.serverId, route.threadId),
                    )
                    ChatScreen(
                        viewModel = viewModel,
                        showNavigationIcon = !isPermanent,
                        onOpenNavigation = { scope.launch { drawerState.open() } },
                        onOpenConversation = { openConversation(route.serverId, it) },
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
    }
}
