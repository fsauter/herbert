package de.fsauter.herbert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.fsauter.herbert.ui.detail.DeviceDetailScreen
import de.fsauter.herbert.ui.detail.DeviceDetailViewModel
import de.fsauter.herbert.ui.home.HomeScreen
import de.fsauter.herbert.ui.home.HomeViewModel
import de.fsauter.herbert.ui.navigation.DeviceDetailRoute
import de.fsauter.herbert.ui.navigation.HomeRoute
import de.fsauter.herbert.ui.theme.HerbertTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HerbertTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                ) {
                    composable<HomeRoute> {
                        val homeViewModel: HomeViewModel = viewModel()
                        HomeScreen(
                            viewModel = homeViewModel,
                            onDeviceClick = { macAddress ->
                                navController.navigate(DeviceDetailRoute(macAddress)) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }

                    composable<DeviceDetailRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<DeviceDetailRoute>()
                        val detailViewModel: DeviceDetailViewModel = viewModel(
                            factory = DeviceDetailViewModel.Factory(
                                application = application,
                                macAddress = route.macAddress,
                            ),
                        )
                        DeviceDetailScreen(
                            viewModel = detailViewModel,
                            onBack = { navController.navigateUp() },
                        )
                    }
                }
            }
        }
    }
}
