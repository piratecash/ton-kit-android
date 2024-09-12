package io.horizontalsystems.tonkit.core

import android.content.Context
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.api.TonApi
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.EventInfo
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.models.TagQuery
import io.horizontalsystems.tonkit.models.TagToken
import io.horizontalsystems.tonkit.storage.KitDatabase
import kotlinx.coroutines.flow.Flow
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.contract.wallet.WalletV4R2Contract
import org.ton.mnemonic.Mnemonic

class TonKit(
    private val address: Address,
    private val accountManager: AccountManager,
    private val jettonManager: JettonManager,
    private val eventManager: EventManager,
) {
    val receiveAddress get() = address

    val syncStateFlow by accountManager::syncStateFlow
    val accountFlow by accountManager::accountFlow
    val jettonSyncStateFlow by jettonManager::syncStateFlow
    val jettonBalanceMapFlow by jettonManager::jettonBalanceMapFlow
    val eventSyncStateFlow by eventManager::syncStateFlow

    fun events(tagQuery: TagQuery, beforeLt: Long? = null, limit: Int? = null): List<Event> {
        return eventManager.events(tagQuery, beforeLt, limit)
    }

    fun eventFlow(tagQuery: TagQuery): Flow<EventInfo> {
        return eventManager.eventFlow(tagQuery)
    }

    fun tagTokens(): List<TagToken> {
        return eventManager.tagTokens()
    }

    suspend fun sync() {
        accountManager.sync()
        jettonManager.sync()
        eventManager.sync()
    }

    sealed class WalletType {
        data class Watch(val address: String) : WalletType()
        data class Seed(val seed: ByteArray) : WalletType()
        data class Mnemonic(val words: List<String>, val passphrase: String = "") : WalletType()
    }

//    enum WalletVersion {
//        case v3
//        case v4
//        case v5
//    }

    sealed class SyncError : Error() {
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
        fun getInstance(
            type: WalletType,
            network: Network,
            context: Context,
            walletId: String,
        ): TonKit {
            val address: Address
            val privateKey: PrivateKeyEd25519?

            when (type) {
                is WalletType.Mnemonic -> {
                    val seed = Mnemonic.toSeed(type.words, type.passphrase)
                    privateKey = PrivateKeyEd25519(seed)
                    address = Address(WalletV4R2Contract.address(privateKey, 0))
                }

                is WalletType.Seed -> {
                    privateKey = PrivateKeyEd25519(type.seed)
                    address = Address(WalletV4R2Contract.address(privateKey, 0))
                }

                is WalletType.Watch -> {
                    privateKey = null
                    address = Address.parse(type.address)
                }
            }

            val database = KitDatabase.getInstance(context, "${walletId}-${network.name}")

            val api = TonApi(network)

            val accountManager = AccountManager(address, api, database.accountDao())
            val jettonManager = JettonManager(address, api, database.jettonDao())
            val eventManager = EventManager(address, api, database.eventDao())

            return TonKit(
                address,
                accountManager,
                jettonManager,
                eventManager
            )
        }

        fun validateAddress(address: String) {
            Address.parse(address)
        }
    }
}