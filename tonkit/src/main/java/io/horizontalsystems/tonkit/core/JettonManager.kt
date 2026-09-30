package io.horizontalsystems.tonkit.core

import co.touchlab.kermit.Logger
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
    jettonBalances: List<JettonBalance>,
    private val logger: Logger,
) {
    private val _jettonBalanceMapFlow = MutableStateFlow(jettonBalances.associateBy { it.jettonAddress })
    val jettonBalanceMapFlow = _jettonBalanceMapFlow.asStateFlow()

    private val _syncStateFlow =
        MutableStateFlow<SyncState>(SyncState.NotSynced(TonKit.SyncError.NotStarted))
    val syncStateFlow = _syncStateFlow.asStateFlow()

    suspend fun sync() {
        logger.d { "Syncing jetton balances..." }

        if (_syncStateFlow.value is SyncState.Syncing) {
            logger.d { "Syncing jetton balances is in progress" }
            return
        }

        _syncStateFlow.update {
            SyncState.Syncing
        }

        try {
            val jettonBalances = api.getAccountJettonBalances(address)
            logger.d { "Got jetton balances: ${jettonBalances.size}" }

            _jettonBalanceMapFlow.update {
                jettonBalances.associateBy { it.jettonAddress }
            }

            dao.replaceAll(jettonBalances)

            _syncStateFlow.update {
                SyncState.Synced
            }
        } catch (e: Throwable) {
            logger.e(e) { "Jetton balances sync error" }
            _syncStateFlow.update {
                SyncState.NotSynced(e)
            }
        }
    }

}