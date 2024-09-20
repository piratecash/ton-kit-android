package io.horizontalsystems.tonkit.sample

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.models.Account
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.JettonBalance
import io.horizontalsystems.tonkit.models.SyncState
import io.horizontalsystems.tonkit.models.TagQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val tonKit = App.tonKit

    val address = tonKit.receiveAddress.toUserFriendly(false)

    private var syncState = tonKit.syncStateFlow.value
    private var account = tonKit.account
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
