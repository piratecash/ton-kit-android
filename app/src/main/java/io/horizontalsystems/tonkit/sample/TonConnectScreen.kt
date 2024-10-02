package io.horizontalsystems.tonkit.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TonConnectScreen() {
    val viewModel = viewModel<TonConnectViewModel>()
    val uiState = viewModel.uiState

    Column {
        var str by remember { mutableStateOf("") }

        TextField(
            value = str,
            onValueChange = {
                str = it
                viewModel.setUrl(it)
            },
            label = { Text("URL") },
        )

        Button(onClick = viewModel::readData) {
            Text("Read Data")
        }

        uiState.dAppRequestEntity?.let { dAppRequestEntity ->
            Spacer(modifier = Modifier.height(12.dp))

            Text("Request: $dAppRequestEntity")
            Button(onClick = viewModel::connect) {
                Text("Connect")
            }
        }
    }
}