package io.horizontalsystems.tonkit.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.horizontalsystems.tonkit.models.Action
import kotlinx.coroutines.delay

@Composable
fun TonConnectSendTransactionScreen(
    navController: NavHostController,
    viewModel: TonConnectViewModel
) {
    val uiState = viewModel.uiState
    val pendingRequest = uiState.pendingSendRequest

    val close = uiState.close
    LaunchedEffect(close) {
        if (close) {
            delay(300)
            navController.popBackStack()
            viewModel.onClose()
        }
    }

    if (pendingRequest != null) {
        Column {
            val event = pendingRequest.second
            event.actions.forEach {
                Action(it)
            }

            Spacer(Modifier.height(48.dp))
            HorizontalDivider()

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::approve
            ) {
                Text("Approve")
            }

            Spacer(Modifier.height(12.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::reject
            ) {
                Text("Reject")
            }
        }
    }

}

@Composable
fun Action(action: Action) {
    Card {
        Text(action.type.toString())

        action.tonTransfer?.let {

        }
        action.jettonTransfer?.let {

        }
        action.jettonBurn?.let {

        }
        action.jettonMint?.let {

        }
        action.contractDeploy?.let {

        }
        action.jettonSwap?.let {
            Text("Dex: ${it.dex}")
        }
        action.smartContractExec?.let {

        }
    }
}
