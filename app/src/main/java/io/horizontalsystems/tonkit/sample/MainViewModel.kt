package io.horizontalsystems.tonkit.sample

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.FriendlyAddress
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonKit.WalletType
import io.horizontalsystems.tonkit.models.Account
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.JettonBalance
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.models.SyncState
import io.horizontalsystems.tonkit.models.TagQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.math.BigDecimal

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val words = "used ugly meat glad balance divorce inner artwork hire invest already piano".split(" ")

    private val walletType = WalletType.Watch("UQBpAeJL-VSLCigCsrgGQHCLeiEBdAuZBlbrrUGI4BVQJoPM")
//    private val walletType = WalletType.Mnemonic(words, "")
    private val tonKit = TonKit.getInstance(
        walletType,
        Network.MainNet,
        getApplication(),
        "wallet-${walletType.javaClass.simpleName}"
    )

    val address = tonKit.receiveAddress.toUserFriendly(false)

    private var syncState = tonKit.syncStateFlow.value
    private var account = tonKit.accountFlow.value
    private var jettonSyncState = tonKit.jettonSyncStateFlow.value
    private var jettonBalanceMap = tonKit.jettonBalanceMapFlow.value
    private var eventSyncState = tonKit.eventSyncStateFlow.value
    private var events: List<Event>? = null

    private val balance: BigDecimal?
        get() = account?.balance?.toBigDecimal()?.movePointLeft(9)

    var uiState by mutableStateOf(
        MainUiState(
            syncState = syncState,
            jettonSyncState = jettonSyncState,
            eventSyncState = eventSyncState,
            account = account,
            jettonBalanceMap = jettonBalanceMap,
            events = events,
            balance = balance
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
        viewModelScope.launch(Dispatchers.Default) {
            tonKit.jettonSyncStateFlow.collect(::updateJettonSyncState)
        }
        viewModelScope.launch(Dispatchers.Default) {
            tonKit.jettonBalanceMapFlow.collect(::updateJettonBalanceMap)
        }
        viewModelScope.launch(Dispatchers.Default) {
            tonKit.eventSyncStateFlow.collect(::updateEventSyncState)
        }
        val tagQuery = TagQuery(null, null, null, null)
        viewModelScope.launch(Dispatchers.Default) {
            val eventFlow = tonKit.eventFlow(tagQuery)
            eventFlow.collect {
                it.events
            }
        }

        events = tonKit.events(tagQuery, limit = 10)
        emitState()


//        viewModelScope.launch(Dispatchers.IO) {
//            tonKit.newTransactionsFlow.collect {
//                transactionList = null
//                loadNextTransactionsPage()
//                refreshFee()
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

    private fun updateJettonSyncState(syncState: SyncState) {
        this.jettonSyncState = syncState

        emitState()
    }

    private fun updateEventSyncState(syncState: SyncState) {
        this.eventSyncState = syncState

        emitState()
    }

    private fun updateJettonBalanceMap(jettonBalanceMap: Map<Address, JettonBalance>) {
        this.jettonBalanceMap = jettonBalanceMap

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
                jettonSyncState = jettonSyncState,
                eventSyncState = eventSyncState,
                account = account,
                jettonBalanceMap = jettonBalanceMap,
                events = events,
                balance = balance,
            )
        }
    }

    private var sendRecipient: String? = null
    private var sendAmount: BigDecimal? = null

    fun setAmount(amount: String) {
        sendAmount = amount.toBigDecimalOrNull()

        refreshFee()
    }

    fun setRecipient(recipient: String) {
        sendRecipient = recipient

        refreshFee()
    }

    private var refreshFeeJob: Job? = null

    private fun refreshFee() {
        val sendRecipientForKit1 = sendRecipientForKit
        val sendAmountForKit1 = sendAmountForKit

        refreshFeeJob?.cancel()
        refreshFeeJob = viewModelScope.launch(Dispatchers.Default) {
            fee = "estimation in progress..."
            if (sendRecipientForKit1 != null && sendAmountForKit1 != null) {
                val estimateFee = tonKit.estimateFee(sendRecipientForKit1, sendAmountForKit1, null)

                ensureActive()
                fee = estimateFee.toBigDecimal(9).toPlainString()
            } else {
                ensureActive()
                fee = null
            }
        }
    }

    private val sendAmountForKit: TonKit.SendAmount?
        get() = sendAmount?.let {
            if (it.compareTo(balance) == 0) {
                TonKit.SendAmount.Max
            } else {
                TonKit.SendAmount.Amount(it.movePointRight(9).toBigInteger())
            }
        }
    private val sendRecipientForKit: FriendlyAddress?
        get() = sendRecipient?.let { FriendlyAddress.parse(it) }


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
    val jettonSyncState: SyncState,
    val eventSyncState: SyncState,
    val account: Account?,
    val jettonBalanceMap: Map<Address, JettonBalance>,
    val events: List<Event>?,
    val balance: BigDecimal?,
)

fun SyncState.toStr() = when (this) {
    is SyncState.NotSynced -> "NotSynced ${error.javaClass.simpleName} - message: ${error.message}"
    is SyncState.Synced -> "Synced"
    is SyncState.Syncing -> "Syncing"
}