package io.horizontalsystems.tonkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.horizontalsystems.tonkit.ui.theme.TonkitadnroidTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TonkitadnroidTheme {
                MainScreen()
            }
        }
    }
}

enum class Page {
    Balance, Transactions, Send
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val viewModel = viewModel<MainViewModel>()
    val uiState = viewModel.uiState
    Scaffold {
        Column(
            modifier = Modifier.padding(it)
        ) {
            var currentPage by remember { mutableStateOf(Page.Balance) }

            TabRow(selectedTabIndex = currentPage.ordinal) {
                Page.entries.forEach { page ->
                    Tab(
                        selected = currentPage == page,
                        onClick = { currentPage = page },
                        text = {
                            Text(
                                text = page.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            Crossfade(targetState = currentPage, label = "") {
                when (it) {
                    Page.Balance -> {
                        BalanceScreen(viewModel, uiState)
                    }

                    Page.Transactions -> {
//                        uiState.transactionList?.let { transactionList ->
//                            Transactions(transactionList) {
//                                viewModel.loadNextTransactionsPage()
//                            }
//                        }
                    }

                    Page.Send -> {
                        SendScreen(viewModel, uiState)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(viewModel: MainViewModel, uiState: MainUiState) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Column {
        var amountStr by remember { mutableStateOf("") }
        var recipientStr by remember { mutableStateOf("") }

        Text(text = "Fee: ${viewModel.fee}")

        Text(
            modifier = Modifier.clickable {
                uiState.account?.balance?.let {
                    amountStr = it.toString()
                    viewModel.setAmount(amountStr)
                }
            },
            text = "Balance: ${uiState.account?.balance}",
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
                coroutineScope.launch {
                    viewModel.send()
                }
            }
        ) {
            Text(text = "Send")
        }

        HorizontalDivider()

        Text(text = "Send Result: ${viewModel.sendResult}")
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun BalanceScreen(viewModel: MainViewModel, uiState: MainUiState) {
    val address = viewModel.address

    Column {
        Text(text = "Address: $address")
        Text(text = "Balance: ${uiState.account?.balance}")
        Text(text = "Account Status: ${uiState.account?.status}")
        Text(text = "Sync State: ${uiState.syncState.description}")
        Text(text = "Jetton Sync State: ${uiState.jettonSyncState.description}")

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
        LazyColumn {
            items(uiState.jettonBalanceMap.values.toList()) {
                Card(modifier = Modifier.padding(bottom = 12.dp)) {
                    Row(modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)) {
                        GlideImage(
                            modifier = Modifier.size(50.dp),
                            model = it.jetton.image,
                            contentDescription = "",
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(it.jetton.symbol)
                            Text(it.jetton.name)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        val balance = it.balance.toBigDecimal(it.jetton.decimals).stripTrailingZeros()
                        Text(balance.toPlainString())
                    }
                }
            }
        }
    }
}

//@Composable
//fun Transactions(transactionList: List<TonTransactionWithTransfers>, onBottomReach: () -> Unit) {
//    if (transactionList.isEmpty()) {
//        Text(text = "No transactions")
//    }
//    LazyColumn {
//        itemsIndexed(transactionList) { i, it ->
//            LaunchedEffect(transactionList) {
//                if (i == transactionList.size - 1) {
//                    onBottomReach.invoke()
//                }
//            }
//
//            Column(modifier = Modifier.padding(vertical = 8.dp)) {
//                val date = DateFormat.format("yyyy-MM-dd hh:mm:ss a", it.timestamp * 1000)
//                val decimals = 9
//                val value_ = it.amount?.let {
//                    BigDecimal(it.toBigInteger(), decimals)
//                }
//                val fee = it.fee?.let {
//                    BigDecimal(it.toBigInteger(), decimals)
//                }
//
//                Text(text = "# $i")
//                Text(text = "Hash: ${it.hash}")
//                Text(text = "Type: ${it.type}")
//                Text(text = "Date: $date")
//                Text(text = "LT: ${it.lt}")
//                Text(text = "Fee: ${fee?.toPlainString()}")
//
//                Text(text = "Value: ${value_?.toPlainString()}")
//                Text(text = "Memo: ${it.memo}")
//                Text(text = "TRANSFERS")
//                it.transfers.forEach {
//                    val value = BigDecimal(it.amount.toBigInteger(), decimals)
//
//                    Column(modifier = Modifier.padding(start = 8.dp)) {
//                        Text(text = "From: ${it.src.getNonBounceable()}")
//                        Text(text = "To: ${it.dest.getNonBounceable()}")
//                        Text(text = "Value: ${value.toPlainString()}")
//                    }
//                }
//
//            }
//            Divider()
//        }
//    }
//}
