package com.videocollect.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.videocollect.app.api.RetrofitClient
import com.videocollect.app.data.SettingsRepository
import com.videocollect.app.ui.add.AddVideoScreen
import com.videocollect.app.ui.detail.DetailScreen
import com.videocollect.app.ui.list.ListScreen
import com.videocollect.app.ui.list.ListViewModel
import com.videocollect.app.ui.player.PlayerActivity
import com.videocollect.app.ui.server.ServerConfigScreen
import com.videocollect.app.ui.settings.SettingsScreen
import com.videocollect.app.ui.theme.VideoCollectTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsRepo = SettingsRepository(applicationContext)
        val isConfigured = runBlocking { settingsRepo.isConfigured.first() }
        if (isConfigured) {
            val host = runBlocking { settingsRepo.serverHost.first() }
            val port = runBlocking { settingsRepo.serverPort.first() }
            RetrofitClient.updateBaseUrl(host, port)
        }

        setContent {
            VideoCollectTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(isConfigured = isConfigured)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(isConfigured: Boolean) {
    val navController = rememberNavController()

    // Shared ListViewModel at navigation level
    val listViewModel: ListViewModel = viewModel()

    val startDestination = if (isConfigured) "list" else "server_config"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("server_config") {
            ServerConfigScreen(
                onConfigured = {
                    navController.navigate("list") {
                        popUpTo("server_config") { inclusive = true }
                    }
                }
            )
        }

        composable("list") {
            ListScreen(
                viewModel = listViewModel,
                onAddClick = { navController.navigate("add") },
                onDetailClick = { id -> navController.navigate("detail/$id") },
                onSettingsClick = { navController.navigate("settings") }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("add") {
            AddVideoScreen(
                onBack = { navController.popBackStack() },
                onSuccess = {
                    listViewModel.refresh()
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: return@composable
            val context = androidx.compose.ui.platform.LocalContext.current

            val playerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == PlayerActivity.RESULT_GO_TO_LIST) {
                    navController.popBackStack("list", false)
                }
            }

            DetailScreen(
                videoId = id,
                onBack = { navController.popBackStack() },
                onPlay = { record ->
                    val baseUrl = RetrofitClient.getBaseUrl()
                    val items = listViewModel.uiState.value.items
                    val currentIndex = items.indexOfFirst { it.id == record.id }
                    val gson = com.google.gson.Gson()
                    val itemsJson = gson.toJson(items)
                    val intent = PlayerActivity.intent(
                        context = context,
                        videoUrl = record.videoUrl ?: "",
                        title = record.title,
                        isM3u8 = record.isM3u8,
                        baseUrl = baseUrl,
                        itemsJson = itemsJson,
                        currentIndex = if (currentIndex >= 0) currentIndex else 0
                    )
                    playerLauncher.launch(intent)
                },
                onDeleted = {
                    listViewModel.refresh()
                    navController.popBackStack()
                }
            )
        }
    }
}
