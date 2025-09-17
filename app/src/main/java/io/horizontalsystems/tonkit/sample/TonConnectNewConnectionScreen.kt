package io.horizontalsystems.tonkit.sample

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay

@Composable
fun TonConnectNewConnectionScreen(navController: NavHostController) {
    val context = LocalContext.current

    val viewModel = viewModel<TonConnectNewConnectionViewModel>()
    val uiState = viewModel.uiState

    LaunchedEffect(uiState.connected) {
        if (uiState.connected) {
            Toast.makeText(context, "New Connection", Toast.LENGTH_SHORT).show()
            delay(300)
            navController.popBackStack()
        }
    }

    Column {
        val clipboard = LocalClipboardManager.current

        Button(
            onClick = {
                val text = clipboard.getText()
                if (text != null) {
                    viewModel.resolveUrl(text.text)
                } else {
                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            Text("Resolve URL from clipboard")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                navController.navigate(QRCodeScanner)
            }
        ) {
            Text("Scan by camera")
        }

        uiState.dAppRequestEntity?.let { dAppRequestEntity ->
            Spacer(modifier = Modifier.height(12.dp))

            Text("Request: $dAppRequestEntity")
            Button(onClick = viewModel::connect) {
                Text("Connect")
            }
        }

        uiState.error?.let { error ->
            Text("Error: $error", color = MaterialTheme.colorScheme.error)
        }
    }
}