package io.horizontalsystems.tonkit.sample

import androidx.compose.runtime.Composable
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

@Composable
fun NavigationStack() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            MainScreen(navController)
        }
        composable<JettonPage> { backStackEntry ->
            val jettonPage: JettonPage = backStackEntry.toRoute()

            JettonScreen(jettonPage.address)
        }
    }
}
