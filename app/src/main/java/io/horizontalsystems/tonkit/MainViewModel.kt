package io.horizontalsystems.tonkit

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonKit.WalletType
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.models.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalStdlibApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val words = "used ugly meat glad balance divorce inner artwork hire invest already piano".split(" ")
    private val passphrase = ""
    private val watchAddress = "UQBpAeJL-VSLCigCsrgGQHCLeiEBdAuZBlbrrUGI4BVQJoPM"

    //    private val tonKit = tonKitFactory.createWatch(watchAddress, "watch")
//    private val tonKit = tonKitFactory.create(words, passphrase, words.first())
//    val tonKitFactory = TonKitFactory(DriverFactory(getApplication()), ConnectionManager(getApplication()))
    private val tonKit = TonKit.getInstance(
        WalletType.Watch(watchAddress),
        Network.MainNet,
        getApplication(),
        "watch"
    )

    val address = tonKit.receiveAddress.toRaw()

    private var balance = "tonKit.balance"

    var uiState by mutableStateOf(
        MainUiState(
            balance = balance,
        )
    )
        private set

    var fee: String? by mutableStateOf(null)
        private set

    init {
        viewModelScope.launch(Dispatchers.Default) {
            tonKit.sync()
        }

        refreshFee()

//        viewModelScope.launch(Dispatchers.IO) {
//            tonKit.newTransactionsFlow.collect {
//                transactionList = null
//                loadNextTransactionsPage()
//                refreshFee()
//            }
//        }
    }

    private fun refreshFee() {
//        viewModelScope.launch(Dispatchers.IO) {
//            val estimateFee = try {
//                tonKit.estimateFee()
//            } catch (e: Throwable) {
//                e.message
//            }
//
//            viewModelScope.launch {
//                fee = estimateFee
//            }
//        }
    }

//    fun loadNextTransactionsPage() {
//        viewModelScope.launch(Dispatchers.IO) {
//            var list = transactionList ?: listOf()
//            list += tonKit.transactions(transactionList?.lastOrNull()?.hash, null, null, 10)
//
//            transactionList = list
//
//            emitState()
//        }
//    }

//    private fun updateSyncState(syncState: SyncState) {
//        this.syncState = syncState
//
//        emitState()
//    }

    override fun onCleared() {
//        tonKit.stop()
    }

    private fun updateBalance(balance: String) {
        this.balance = balance

        emitState()
    }

    private fun emitState() {
        viewModelScope.launch {
            uiState = MainUiState(
                balance = balance,
            )
        }
    }

    private var sendRecipient: String? = null
    private var sendAmount: BigDecimal? = null

    fun setAmount(amount: String) {
        sendAmount = amount.toBigDecimal()
    }

    fun setRecipient(recipient: String) {
        sendRecipient = recipient
    }

    var sendResult by mutableStateOf("")
        private set

    fun send() {
        viewModelScope.launch(Dispatchers.Default) {
            sendResult = ""
            try {
                val sendRecipient = sendRecipient
                val sendAmount = sendAmount?.movePointRight(9)?.toBigInteger()
                checkNotNull(sendRecipient)
                checkNotNull(sendAmount)

                sendResult = "Sending..."

                TODO()

//                tonKit.send(sendRecipient, sendAmount.toString(), "Test transaction")

                sendResult = "Send success"
            } catch (t: Throwable) {
                sendResult = "Send error: $t"
            }
        }
    }

    fun start() {
//        tonKit.start()
    }

    fun stop() {
//        tonKit.stop()
    }
}

data class MainUiState(
    val balance: String?,
)

fun SyncState.toStr() = when (this) {
    is SyncState.NotSynced -> "NotSynced ${error.javaClass.simpleName} - message: ${error.message}"
    is SyncState.Synced -> "Synced"
    is SyncState.Syncing -> "Syncing"
}