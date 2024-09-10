package io.horizontalsystems.tonkit.core

import android.util.Log
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.api.IApi
import io.horizontalsystems.tonkit.models.SyncState
import io.horizontalsystems.tonkit.storage.AccountDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AccountManager(
    private val address: Address,
    private val api: IApi,
    private val dao: AccountDao,
) {
    private val _accountFlow = MutableStateFlow(dao.getAccount(address))
    private val accountFlow = _accountFlow.asStateFlow()

    private val _syncStateFlow = MutableStateFlow<SyncState>(SyncState.NotSynced(TonKit.SyncError.NotStarted))
    private val syncStateFlow = _syncStateFlow.asStateFlow()

    suspend fun sync() {
        Log.d("AAA", "Syncing account...")

        if (_syncStateFlow.value is SyncState.Syncing) {
            Log.d("AAA","Syncing account is in progress")
            return
        }

        _syncStateFlow.update {
            SyncState.Syncing
        }

        try {
            val account = api.getAccount(address)
            Log.d("AAA", "Got account: $account")

            _accountFlow.update {
                account
            }

            dao.save(account)

            _syncStateFlow.update {
                SyncState.Synced
            }
        } catch (e: Throwable) {
            _syncStateFlow.update {
                SyncState.NotSynced(e)
            }
        }
    }
}
