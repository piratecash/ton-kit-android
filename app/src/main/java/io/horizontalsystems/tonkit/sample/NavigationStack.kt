package io.horizontalsystems.tonkit.sample

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

// Define a home route that doesn't take any arguments
@Serializable
object Home

@Serializable
data class JettonPage(val address: String)

@Serializable
object TonConnect

@Serializable
object TonConnectNewConnection

@Serializable
object TonConnectSendTransaction

@Composable
fun NavigationStack() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Home,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp)
    ) {
        composable<Home> {
            MainScreen(navController)
        }
        composable<JettonPage> { backStackEntry ->
            val jettonPage: JettonPage = backStackEntry.toRoute()

            JettonScreen(jettonPage.address)
        }
        composable<TonConnect> {
            val viewModel = viewModel<TonConnectViewModel>()
            TonConnectScreen(navController, viewModel)
        }
        composable<TonConnectSendTransaction> { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(TonConnect)
            }
            val viewModel = viewModel<TonConnectViewModel>(parentEntry)

            TonConnectSendTransactionScreen(navController, viewModel)
        }
        composable<TonConnectNewConnection> {
            TonConnectNewConnectionScreen(navController)
        }
    }
}
