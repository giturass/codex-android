package com.hebo.codex.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.ui.screens.chat.ChatScreen
import com.hebo.codex.ui.screens.chat.ChatViewModel
import com.hebo.codex.ui.screens.home.HomeScreen
import com.hebo.codex.ui.screens.home.HomeViewModel
import com.hebo.codex.ui.screens.hub.ServerHubScreen
import com.hebo.codex.ui.screens.hub.ServerHubViewModel
import com.hebo.codex.ui.screens.project.ProjectScreen
import com.hebo.codex.ui.screens.project.ProjectViewModel
import com.hebo.codex.ui.screens.servers.AddServerScreen
import com.hebo.codex.ui.screens.servers.AddServerViewModel
import com.hebo.codex.ui.components.CodexNavigationDrawer
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
private data object HomeRoute

@Serializable
private data object AddServerRoute

@Serializable
private data class ProjectRoute(val serverId: String, val cwd: String)

@Serializable
private data class ChatRoute(val serverId: String, val threadId: String)

@Serializable
private data class ServerHubRoute(val serverId: String, val cwd: String? = null)

@Composable
fun CodexApp(
    repository: CodexRepository,
    navController: NavHostController = rememberNavController(),
) {
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(repository))
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var newConversationRequest by remember { mutableIntStateOf(0) }

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    fun openHome() {
        navController.navigate(HomeRoute) {
            popUpTo<HomeRoute> { inclusive = false }
            launchSingleTop = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            CodexNavigationDrawer(
                servers = homeState.servers,
                activeServer = homeState.activeServer,
                connectionStatuses = homeState.connectionStatuses,
                projects = homeState.projects,
                threads = homeState.threads,
                searchTerm = homeState.searchTerm,
                isLoading = homeState.isLoading,
                onOpenHome = {
                    closeDrawer()
                    openHome()
                },
                onNewConversation = {
                    closeDrawer()
                    newConversationRequest += 1
                    openHome()
                },
                onOpenProject = { cwd ->
                    closeDrawer()
                    homeState.activeServerId?.let { serverId ->
                        navController.navigate(ProjectRoute(serverId, cwd)) {
                            popUpTo<HomeRoute> { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                onOpenConversation = { threadId ->
                    closeDrawer()
                    homeState.activeServerId?.let { serverId ->
                        navController.navigate(ChatRoute(serverId, threadId)) {
                            popUpTo<HomeRoute> { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                onSearch = homeViewModel::search,
                onSelectServer = { serverId ->
                    homeViewModel.selectServer(serverId)
                    closeDrawer()
                    openHome()
                },
                onReconnectServer = homeViewModel::reconnectServer,
                onRemoveServer = { serverId ->
                    homeViewModel.removeServer(serverId)
                    closeDrawer()
                    openHome()
                },
                onAddServer = {
                    closeDrawer()
                    navController.navigate(AddServerRoute)
                },
                onOpenSettings = {
                    closeDrawer()
                    homeState.activeServerId?.let { navController.navigate(ServerHubRoute(it)) }
                },
            )
        },
    ) {
        NavHost(navController = navController, startDestination = HomeRoute) {
            composable<HomeRoute> {
                HomeScreen(
                    viewModel = homeViewModel,
                    newConversationRequest = newConversationRequest,
                    onNewConversationRequestHandled = { newConversationRequest = 0 },
                    onOpenNavigation = { scope.launch { drawerState.open() } },
                    onAddServer = { navController.navigate(AddServerRoute) },
                    onOpenProject = { serverId, cwd ->
                        navController.navigate(ProjectRoute(serverId, cwd))
                    },
                    onOpenConversation = { serverId, threadId ->
                        navController.navigate(ChatRoute(serverId, threadId))
                    },
                )
            }
            composable<AddServerRoute> {
                val viewModel: AddServerViewModel = viewModel(factory = AddServerViewModel.factory(repository))
                AddServerScreen(viewModel = viewModel, onBack = navController::popBackStack)
            }
            composable<ProjectRoute> { entry ->
                val route = entry.toRoute<ProjectRoute>()
                val viewModel: ProjectViewModel = viewModel(
                    key = "project:${route.serverId}:${route.cwd}",
                    factory = ProjectViewModel.factory(repository, route.serverId, route.cwd),
                )
                ProjectScreen(
                    viewModel = viewModel,
                    onBack = navController::popBackStack,
                    onOpenConversation = { threadId ->
                        navController.navigate(ChatRoute(route.serverId, threadId))
                    },
                    onOpenServerHub = {
                        navController.navigate(ServerHubRoute(route.serverId, route.cwd))
                    },
                )
            }
            composable<ChatRoute> { entry ->
                val route = entry.toRoute<ChatRoute>()
                val viewModel: ChatViewModel = viewModel(
                    key = "chat:${route.serverId}:${route.threadId}",
                    factory = ChatViewModel.factory(repository, route.serverId, route.threadId),
                )
                ChatScreen(
                    viewModel = viewModel,
                    onOpenNavigation = { scope.launch { drawerState.open() } },
                    onNewConversation = {
                        newConversationRequest += 1
                        openHome()
                    },
                    onOpenConversation = { threadId ->
                        navController.navigate(ChatRoute(route.serverId, threadId))
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
}
