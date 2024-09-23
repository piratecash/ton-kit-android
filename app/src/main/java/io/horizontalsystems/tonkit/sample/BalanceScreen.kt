package io.horizontalsystems.tonkit.sample

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun BalanceScreen(viewModel: MainViewModel, uiState: MainUiState, navController: NavHostController) {
    val address = viewModel.address

    Column {
        Text(text = "Address: $address")
        Text(text = "Balance: ${uiState.balance}")
        Text(text = "Account Status: ${uiState.account?.status}")
        Text(text = "Sync State: ${uiState.syncState.description}")
        Text(text = "Jetton Sync State: ${uiState.jettonSyncState.description}")
        Text(text = "Event Sync State: ${uiState.eventSyncState.description}")

        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = viewModel::start) {
                Text(text = "start")
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = viewModel::stop) {
                Text(text = "Stop")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("JETTONS")
        Spacer(modifier = Modifier.height(10.dp))

        Crossfade(uiState.jettonBalanceMap.isNotEmpty(), label = "") { isNotEmpty ->
            if (isNotEmpty) {
                LazyColumn {
                    items(uiState.jettonBalanceMap.values.toList()) { jettonBalance ->
                        val jetton = jettonBalance.jetton
                        Card(
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .clickable {
                                    navController.navigate(JettonPage(jetton.address.toRaw()))
                                }
                        ) {
                            Row(modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)) {
                                GlideImage(
                                    modifier = Modifier.size(50.dp),
                                    model = jetton.image,
                                    contentDescription = "",
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(jetton.symbol)
                                    Text(jetton.name)
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                val balance =
                                    jettonBalance.balance.toBigDecimal(jetton.decimals).stripTrailingZeros()
                                Text(balance.toPlainString())
                            }
                        }
                    }
                }
            } else {
                Text("No jettons")
            }
        }
    }
}