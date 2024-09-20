package io.horizontalsystems.tonkit.core

import android.content.Context
import com.tonapps.blockchain.ton.contract.WalletV4R2Contract
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.FriendlyAddress
import io.horizontalsystems.tonkit.api.TonApi
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.EventInfo
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.models.TagQuery
import io.horizontalsystems.tonkit.models.TagToken
import io.horizontalsystems.tonkit.storage.KitDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.mnemonic.Mnemonic
import java.math.BigInteger

class TonKit(
    private val address: Address,
    private val accountManager: AccountManager,
    private val jettonManager: JettonManager,
    private val eventManager: EventManager,
    private val transactionSender: TransactionSender?,
) {
    val receiveAddress get() = address

    val syncStateFlow by accountManager::syncStateFlow
    val accountFlow by accountManager::accountFlow
    val jettonSyncStateFlow by jettonManager::syncStateFlow
    val jettonBalanceMapFlow by jettonManager::jettonBalanceMapFlow
    val eventSyncStateFlow by eventManager::syncStateFlow

    val account get() = accountFlow.value

    fun events(tagQuery: TagQuery, beforeLt: Long? = null, limit: Int? = null): List<Event> {
        return eventManager.events(tagQuery, beforeLt, limit)
    }

    fun eventFlow(tagQuery: TagQuery): Flow<EventInfo> {
        return eventManager.eventFlow(tagQuery)
    }

    fun tagTokens(): List<TagToken> {
        return eventManager.tagTokens()
    }

    suspend fun estimateFee(recipient: FriendlyAddress, amount: SendAmount, comment: String?) : BigInteger {
        return transactionSender?.estimateFee(recipient, amount, comment) ?: throw WalletError.WatchOnly
    }

    suspend fun estimateFee(jettonWallet: Address, recipient: FriendlyAddress, amount: BigInteger, comment: String?): BigInteger {
        return transactionSender?.estimateFee(jettonWallet, recipient, amount, comment) ?: throw WalletError.WatchOnly
    }

    suspend fun send(recipient: FriendlyAddress, amount: SendAmount, comment: String?) {
        transactionSender?.send(recipient, amount, comment) ?: throw WalletError.WatchOnly
    }

    suspend fun sync() = coroutineScope {
        listOf(
            async {
                accountManager.sync()
            },
            async {
                jettonManager.sync()
            },
            async {
                eventManager.sync()
            },
        ).awaitAll()
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

    sealed class WalletError : Error() {
        data object WatchOnly : WalletError()
    }

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

                    val walletV4R2Contract = WalletV4R2Contract(publicKey = privateKey.publicKey())

                    address = Address(walletV4R2Contract.address)
                }

                is WalletType.Seed -> {
                    privateKey = PrivateKeyEd25519(type.seed)

                    val walletV4R2Contract = WalletV4R2Contract(publicKey = privateKey.publicKey())

                    address = Address(walletV4R2Contract.address)
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

            val transactionSender = privateKey?.let {
                TransactionSender(api, address, privateKey)
            }

            return TonKit(
                address,
                accountManager,
                jettonManager,
                eventManager,
                transactionSender
            )
        }

        fun validateAddress(address: String) {
            Address.parse(address)
        }
    }

    sealed class SendAmount {
        data class Amount(val value: BigInteger) : SendAmount()
        data object Max : SendAmount()
    }

}