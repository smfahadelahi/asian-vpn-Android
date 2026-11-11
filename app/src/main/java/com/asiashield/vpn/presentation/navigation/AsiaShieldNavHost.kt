package com.asiashield.vpn.presentation.navigation

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.asiashield.vpn.presentation.VpnEvent
import com.asiashield.vpn.presentation.VpnViewModel
import com.asiashield.vpn.presentation.home.HomeScreen
import com.asiashield.vpn.presentation.servers.ServerListScreen

object Routes {
    const val HOME = "home"
    const val SERVERS = "servers"
}

@Composable
fun AsiaShieldNavHost() {
    // Activity-scoped so both screens share one connection state.
    val viewModel: VpnViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    // Runtime VpnService.prepare() contract: show the system consent dialog and report back.
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onVpnPermissionResult(granted = result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is VpnEvent.RequestVpnPermission -> vpnPermissionLauncher.launch(event.intent)
                is VpnEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        uiState = uiState,
                        onPowerClick = viewModel::onPowerClick,
                        onSmartConnect = viewModel::onSmartConnect,
                        onOpenServers = {
                            navController.navigate(Routes.SERVERS) { launchSingleTop = true }
                        },
                    )
                }
                composable(Routes.SERVERS) {
                    ServerListScreen(
                        uiState = uiState,
                        onQueryChange = viewModel::onQueryChange,
                        onRegionSelected = viewModel::onRegionSelected,
                        onServerSelected = { server ->
                            viewModel.onServerSelected(server)
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        },
                        onRefresh = viewModel::refreshLatencies,
                        onBack = { navController.popBackStack(Routes.HOME, inclusive = false) },
                    )
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }
    }
}
