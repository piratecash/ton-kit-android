package io.horizontalsystems.tonkit

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonKit.WalletType
import io.horizontalsystems.tonkit.models.Account
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

    private var syncState = tonKit.syncStateFlow.value
    private var account = tonKit.accountFlow.value

    var uiState by mutableStateOf(
        MainUiState(
            syncState = syncState,
            account = account
        )
    )
        private set

    var fee: String? by mutableStateOf(null)
        private set

    init {
        viewModelScope.launch(Dispatchers.Default) {
            tonKit.syncStateFlow.collect(::updateSyncState)
        }
        viewModelScope.launch(Dispatchers.Default) {
            tonKit.accountFlow.collect(::updateAccount)
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

    private fun updateSyncState(syncState: SyncState) {
        this.syncState = syncState

        emitState()
    }

    private fun updateAccount(account: Account?) {
        this.account = account

        emitState()
    }

    override fun onCleared() {
//        tonKit.stop()
    }

    private fun emitState() {
        viewModelScope.launch {
            uiState = MainUiState(
                syncState = syncState,
                account = account,
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
        viewModelScope.launch(Dispatchers.Default) {
            tonKit.sync()
        }
    }

    fun stop() {
//        tonKit.stop()
    }
}

data class MainUiState(
    val syncState: SyncState,
    val account: Account?,
)

fun SyncState.toStr() = when (this) {
    is SyncState.NotSynced -> "NotSynced ${error.javaClass.simpleName} - message: ${error.message}"
    is SyncState.Synced -> "Synced"
    is SyncState.Syncing -> "Syncing"
}