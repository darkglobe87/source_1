package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.audio.MusicManager
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.ui.screens.GameScreen
import com.example.ui.screens.MainMenuScreen
import com.example.ui.screens.StoreScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.GameViewModel
import com.example.viewmodel.GameViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        MusicManager.init(this)

        val database = AppDatabase.getDatabase(this)
        val repository = AppRepository(database.appDao(), this.applicationContext)
        val factory = GameViewModelFactory(repository)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "menu") {
                        composable("menu") {
                            MainMenuScreen(
                                repository = repository,
                                onPlay = { navController.navigate("game") },
                                onNavigateToStore = { navController.navigate("store") }
                            )
                        }
                        composable("game") {
                            val gameViewModel: GameViewModel = viewModel(factory = factory)
                            GameScreen(
                                viewModel = gameViewModel,
                                onNavigateToStore = { navController.navigate("store") },
                                onNavigateHome = { navController.popBackStack("menu", inclusive = false) }
                            )
                        }
                        composable("store") {
                            StoreScreen(
                                repository = repository,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        MusicManager.start()
    }

    override fun onStop() {
        super.onStop()
        MusicManager.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            MusicManager.release()
        }
    }
}
