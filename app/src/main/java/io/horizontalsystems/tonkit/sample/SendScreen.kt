package io.horizontalsystems.tonkit.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SendScreen() {
    val viewModel = viewModel<SendViewModel>()

    val uiState = viewModel.uiState

    Column {
        var amountStr by remember { mutableStateOf("") }
        var recipientStr by remember { mutableStateOf("") }

        val feeStr = if (uiState.feeError != null) {
            "error ${uiState.feeError}"
        } else if (uiState.feeEstimateInProgress) {
            "estimating..."
        } else {
            uiState.fee?.toPlainString()
        }

        Text(text = "Fee: $feeStr")

        Text(
            modifier = Modifier.clickable {
                uiState.balance?.let {
                    amountStr = it.toString()
                    viewModel.setAmount(amountStr)
                }
            },
            text = "Balance: ${uiState.balance}",
        )

        TextField(
            value = recipientStr,
            onValueChange = {
                recipientStr = it
                viewModel.setRecipient(it)
            },
            label = { Text("Recipient") },
        )

        TextField(
            value = amountStr,
            onValueChange = {
                amountStr = it
                viewModel.setAmount(it)
            },
            label = { Text("Amount") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
        )

        Button(
            onClick = {
                viewModel.send()
            },
            enabled = uiState.sendEnabled
        ) {
            val text = when {
                uiState.sendInProgress -> "Sending..."
                else -> "Send"
            }
            Text(text = text)
        }

        HorizontalDivider()

        Text(text = "Send Result: ${uiState.sendResult}")
    }
}