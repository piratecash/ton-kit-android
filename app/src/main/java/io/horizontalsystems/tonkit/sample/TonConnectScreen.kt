package io.horizontalsystems.tonkit.sample

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun TonConnectScreen(navController: NavHostController, viewModel: TonConnectViewModel) {
    val uiState = viewModel.uiState
    val sendRequest = uiState.pendingSendRequest
    LaunchedEffect(sendRequest?.first?.id) {
        Log.e("AAA", "TonConnectScreen: sendRequest: ${sendRequest?.first?.id}")
        sendRequest?.let {
            viewModel.onRequestHandled(sendRequest.first.id)
            Log.e("AAA", "navController.navigate(TonConnectSendTransaction)")
            navController.navigate(TonConnectSendTransaction)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(TonConnectNewConnection) }) {
                Text("+")
            }
        }
    ) {
        Column(modifier = Modifier.padding(it)) {
            if (uiState.dApps.isEmpty()) {
                Text("No connected DApps")
            } else {
                Text("DApps:")
                HorizontalDivider()

                LazyColumn {
                    items(uiState.dApps) { dApp ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GlideImage(
                                    modifier = Modifier.size(80.dp),
                                    model = dApp.manifest.iconUrl,
                                    contentDescription = "",
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(dApp.manifest.name)
                                Spacer(Modifier.weight(1f))

                                Button(onClick = { viewModel.disconnect(dApp) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "",
                                        tint = Color.Red
                                    )
                                }


                            }
                        }
                    }
                }
            }
        }
    }
}
