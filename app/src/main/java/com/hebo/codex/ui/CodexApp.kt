package com.hebo.codex.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
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
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(repository))
            HomeScreen(
                viewModel = viewModel,
                onAddServer = { navController.navigate(AddServerRoute) },
                onOpenProject = { serverId, cwd ->
                    navController.navigate(ProjectRoute(serverId, cwd))
                },
                onOpenConversation = { serverId, threadId ->
                    navController.navigate(ChatRoute(serverId, threadId))
                },
                onOpenServerHub = { serverId ->
                    navController.navigate(ServerHubRoute(serverId))
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
                onBack = navController::popBackStack,
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
