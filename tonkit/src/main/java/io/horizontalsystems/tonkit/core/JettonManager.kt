package io.horizontalsystems.tonkit.core

import android.util.Log
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.api.IApi
import io.horizontalsystems.tonkit.models.JettonBalance
import io.horizontalsystems.tonkit.models.SyncState
import io.horizontalsystems.tonkit.storage.JettonDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class JettonManager(
    private val address: Address,
    private val api: IApi,
    private val dao: JettonDao,
) {
    private val _jettonBalanceMapFlow = MutableStateFlow(getInitialJettonBalanceMap())
    val jettonBalanceMapFlow = _jettonBalanceMapFlow.asStateFlow()

    private val _syncStateFlow =
        MutableStateFlow<SyncState>(SyncState.NotSynced(TonKit.SyncError.NotStarted))
    private val syncStateFlow = _syncStateFlow.asStateFlow()

    private fun getInitialJettonBalanceMap(): Map<Address, JettonBalance> {
        val jettonBalances = dao.getJettonBalances()
        return jettonBalances.associateBy { it.jettonAddress }
    }

    suspend fun sync() {
        Log.d("AAA", "Syncing jetton balances...")

        if (_syncStateFlow.value is SyncState.Syncing) {
            Log.d("AAA", "Syncing jetton balances is in progress")
            return
        }

        _syncStateFlow.update {
            SyncState.Syncing
        }

        try {
            val jettonBalances = api.getAccountJettonBalances(address)
            Log.d("AAA", "Got jetton balances: ${jettonBalances.size}")

            _jettonBalanceMapFlow.update {
                jettonBalances.associateBy { it.jettonAddress }
            }

            dao.insertAll(jettonBalances)

            _syncStateFlow.update {
                SyncState.Synced
            }
        } catch (e: Throwable) {
            Log.e("AAA", "Jetton balances sync error: $e", e)
            _syncStateFlow.update {
                SyncState.NotSynced(e)
            }
        }
    }

}