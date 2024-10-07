package io.horizontalsystems.tonkit.core

import android.content.Context
import android.util.Log
import com.tonapps.blockchain.ton.contract.WalletV4R2Contract
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.MessageBodyEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.FriendlyAddress
import io.horizontalsystems.tonkit.api.TonApi
import io.horizontalsystems.tonkit.api.TonApiListener
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.EventInfo
import io.horizontalsystems.tonkit.models.Jetton
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.models.TagQuery
import io.horizontalsystems.tonkit.models.TagToken
import io.horizontalsystems.tonkit.storage.KitDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import java.math.BigInteger

class TonKit(
    private val address: Address,
    private val apiListener: TonApiListener,
    private val accountManager: AccountManager,
    private val jettonManager: JettonManager,
    private val eventManager: EventManager,
    private val transactionSender: TransactionSender?,
    val network: Network,
    private val api: TonApi,
) {
    val receiveAddress get() = address

    val syncStateFlow by accountManager::syncStateFlow
    val accountFlow by accountManager::accountFlow
    val jettonSyncStateFlow by jettonManager::syncStateFlow
    val jettonBalanceMapFlow by jettonManager::jettonBalanceMapFlow
    val eventSyncStateFlow by eventManager::syncStateFlow

    val account get() = accountFlow.value
    val jettonBalanceMap get() = jettonBalanceMapFlow.value

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    init {
        coroutineScope.launch {
            apiListener.transactionFlow.collect {
                handleEvent(it)
            }
        }
    }

    suspend fun refresh() {
        sync()
    }

    suspend fun start() = coroutineScope {
        listOf(
            async {
                sync()
            },
            async {
                startListener()
            }
        ).awaitAll()
    }

    fun stop() {
        this.stopListener()
    }

    private suspend fun handleEvent(eventId: String) {
        repeat(3) {
            delay(5000)
            if (eventManager.isEventCompleted(eventId)) {
                return
            }

            sync()
        }
    }

    fun events(tagQuery: TagQuery, beforeLt: Long? = null, limit: Int? = null): List<Event> {
        return eventManager.events(tagQuery, beforeLt, limit)
    }

    fun eventFlow(tagQuery: TagQuery): Flow<EventInfo> {
        return eventManager.eventFlow(tagQuery)
    }

    fun tagTokens(): List<TagToken> {
        return eventManager.tagTokens()
    }

    suspend fun estimateFee(
        recipient: FriendlyAddress,
        amount: SendAmount,
        comment: String?,
    ): BigInteger {
        return transactionSender?.estimateFee(recipient, amount, comment)
            ?: throw WalletError.WatchOnly
    }

    suspend fun estimateFee(
        jettonWallet: Address,
        recipient: FriendlyAddress,
        amount: BigInteger,
        comment: String?,
    ): BigInteger {
        return transactionSender?.estimateFee(jettonWallet, recipient, amount, comment)
            ?: throw WalletError.WatchOnly
    }

    suspend fun send(recipient: FriendlyAddress, amount: SendAmount, comment: String?) {
        transactionSender?.send(recipient, amount, comment) ?: throw WalletError.WatchOnly
    }

    suspend fun send(
        jettonWallet: Address,
        recipient: FriendlyAddress,
        amount: BigInteger,
        comment: String?,
    ) {
        transactionSender?.send(jettonWallet, recipient, amount, comment)
            ?: throw WalletError.WatchOnly
    }

    suspend fun send(boc: String) {
        transactionSender?.send(boc) ?: throw WalletError.WatchOnly
    }

    fun startListener() {
        apiListener.start(address = address)
    }

    fun stopListener() {
        apiListener.stop()
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

    suspend fun sign(request: SendRequestEntity, walletType: WalletType): String {
        val privateKey = walletType.privateKey!!
        val walletEntity = WalletEntity(
            id = "id",
            publicKey = privateKey.publicKey(),
            type = Wallet.Type.Default,
            version = WalletVersion.V4R2,
            label = Wallet.Label("", "", 0)
        )

        val seqno = api.getAccountSeqno(walletEntity.accountId)
        val message = createSignedMessage(
            walletEntity,
            seqno,
            privateKey,
            request.validUntil,
            request.transfers
        )
        return message.base64()
    }

    suspend fun getDetails(request: SendRequestEntity, walletType: WalletType): Event {
        val publicKey = walletType.privateKey!!.publicKey()
        val walletEntity = WalletEntity(
            id = "id",
            publicKey = publicKey,
            type = Wallet.Type.Default,
            version = WalletVersion.V4R2,
            label = Wallet.Label("", "", 0)
        )

        return emulate(request, walletEntity)
    }

    private suspend fun emulate(
        request: SendRequestEntity,
        wallet: WalletEntity,
    ): Event {

        val seqno = api.getAccountSeqno(wallet.accountId)
        val cell = createSignedMessage(wallet, seqno, EmptyPrivateKeyEd25519, request.validUntil, request.transfers)

        val emulated = api.emulate(cell, wallet.testnet)
        val event = Event.fromApi(emulated.event)

        Log.e("AAA", "event: $event")

        return event
    }

    fun messageBody(
        wallet: WalletEntity,
        seqno: Int,
        validUntil: Long,
        transfers: List<WalletTransfer>
    ): MessageBodyEntity {
        val body = wallet.createBody(seqno, validUntil, transfers)
        return MessageBodyEntity(seqno, body, validUntil)
    }

    private fun createSignedMessage(
        wallet: WalletEntity,
        seqno: Int,
        privateKeyEd25519: PrivateKeyEd25519,
        validUntil: Long,
        transfers: List<WalletTransfer>
    ): Cell {
        val data = messageBody(wallet, seqno, validUntil, transfers)
        return wallet.sign(privateKeyEd25519, data.seqno, data.body)
    }


    sealed class WalletType {
        val address get() = addressPrivateKeyPair.first
        val privateKey get() = addressPrivateKeyPair.second

        data class Watch(val addressStr: String) : WalletType()
        data class Seed(val seed: ByteArray) : WalletType()
        data class Mnemonic(val words: List<String>, val passphrase: String = "") : WalletType()

        private val addressPrivateKeyPair: Pair<Address, PrivateKeyEd25519?> by lazy {
            val address: Address
            val privateKey: PrivateKeyEd25519?

            when (this) {
                is Mnemonic -> {
                    val seed = org.ton.mnemonic.Mnemonic.toSeed(words, passphrase)
                    privateKey = PrivateKeyEd25519(seed)

                    val walletV4R2Contract =
                        WalletV4R2Contract(publicKey = privateKey.publicKey())

                    address = Address(walletV4R2Contract.address)
                }

                is Seed -> {
                    privateKey = PrivateKeyEd25519(seed)

                    val walletV4R2Contract =
                        WalletV4R2Contract(publicKey = privateKey.publicKey())

                    address = Address(walletV4R2Contract.address)
                }

                is Watch -> {
                    privateKey = null
                    address = Address.parse(this.addressStr)
                }

            }

            Pair(address, privateKey)

        }
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
        private val okHttpClient: OkHttpClient by lazy {
            val logging = HttpLoggingInterceptor()
            logging.level = Level.NONE
            OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()
        }

        fun getInstance(
            type: WalletType,
            network: Network,
            context: Context,
            walletId: String,
        ): TonKit {
            val address = type.address
            val privateKey = type.privateKey

            val database = KitDatabase.getInstance(context, "${walletId}-${network.name}")

            val api = getTonApi(network)

            val accountManager = AccountManager(address, api, database.accountDao())
            val jettonManager = JettonManager(address, api, database.jettonDao())
            val eventManager = EventManager(address, api, database.eventDao())

            val transactionSender = privateKey?.let {
                TransactionSender(api, address, it)
            }

            val apiListener = TonApiListener(network, okHttpClient)

            return TonKit(
                address,
                apiListener,
                accountManager,
                jettonManager,
                eventManager,
                transactionSender,
                network,
                api
            )
        }

        fun getTonApi(network: Network) = TonApi(network, okHttpClient)

        suspend fun getJetton(network: Network, address: Address): Jetton {
            return getTonApi(network).getJettonInfo(address)
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