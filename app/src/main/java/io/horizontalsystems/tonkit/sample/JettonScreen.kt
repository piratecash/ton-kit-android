package io.horizontalsystems.tonkit.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun JettonScreen(jettonAddress: String) {
    val viewModel = viewModel<JettonViewModel>(initializer = {
        JettonViewModel(jettonAddress)
    })

    val uiState = viewModel.uiState

    uiState.jettonBalance?.let { jettonBalance ->
        val jetton = jettonBalance.jetton

        Column {
            GlideImage(
                modifier = Modifier.size(100.dp),
                model = jetton.image,
                contentDescription = "",
            )
            Column {
                Text(jetton.symbol)
                Text(jetton.name)
            }

            Text("Balance: ${uiState.balance.toPlainString()}")

            SendScreen(SendViewModel.SendType.Jetton(jettonBalance.walletAddress, jetton.decimals, uiState.balance))
        }

    }
}