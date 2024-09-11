package io.horizontalsystems.tonkit.core

import android.content.Context
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.api.TonApi
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.storage.KitDatabase

class TonKit(
    private val address: Address,
    private val accountManager: AccountManager,
    private val jettonManager: JettonManager,
) {
    val receiveAddress get() = address

    val syncStateFlow by accountManager::syncStateFlow
    val accountFlow by accountManager::accountFlow

    suspend fun sync() {
        accountManager.sync()
        jettonManager.sync()
    }

    sealed class WalletType {
        data object Full : WalletType()
        data class Watch(val address: String) : WalletType()
    }

//    enum WalletVersion {
//        case v3
//        case v4
//        case v5
//    }

    sealed class SyncError: Error() {
        data object NotStarted : SyncError() {
            override val message = "Not Started"
        }
    }

//    enum WalletError: Error {
//        case watchOnly
//    }

//    enum SendAmount {
//        case amount(value: BigUInt)
//        case max
//    }

    companion object {
        fun getInstance(type: WalletType, network: Network, context: Context, walletId: String): TonKit {

            val address: Address

            when (type) {
                WalletType.Full -> TODO()
                is WalletType.Watch -> {
                    address = Address.parse(type.address)
                }
            }

            val database = KitDatabase.getInstance(context, "${walletId}-${network.name}")

            val api = TonApi(network)

            val accountManager = AccountManager(address, api, database.accountDao())
            val jettonManager = JettonManager(address, api, database.jettonDao())

            return TonKit(
                address,
                accountManager,
                jettonManager
            )
        }
    }
}