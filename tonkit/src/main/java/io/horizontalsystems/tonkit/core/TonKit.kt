package io.horizontalsystems.tonkit.core

import co.touchlab.kermit.Logger
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.FriendlyAddress
import io.horizontalsystems.tonkit.PlatformContext
import io.horizontalsystems.tonkit.api.AnonymousRateLimitInterceptor
import io.horizontalsystems.tonkit.api.ApiKeyProvider
import io.horizontalsystems.tonkit.api.IApiListener
import io.horizontalsystems.tonkit.api.RateLimitInterceptor
import io.horizontalsystems.tonkit.api.TonApi
import io.horizontalsystems.tonkit.api.TonApiListener
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.EventInfo
import io.horizontalsystems.tonkit.models.Jetton
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.models.RawMessageBroadcastMetadata
import io.horizontalsystems.tonkit.models.RawMessageBroadcastResult
import io.horizontalsystems.tonkit.models.SignedRawTonTransaction
import io.horizontalsystems.tonkit.models.TagQuery
import io.horizontalsystems.tonkit.models.TagToken
import io.horizontalsystems.sqlcipher.room.DatabaseKeyMismatchException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationConflictException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationInProgressException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationRequiredException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationResult
import io.horizontalsystems.sqlcipher.room.InsufficientDatabaseMigrationSpaceException
import io.horizontalsystems.tonkit.storage.KitDatabase
import io.horizontalsystems.tonkit.storage.databaseFile
import io.horizontalsystems.tonkit.storage.kitDatabaseName
import io.horizontalsystems.tonkit.storage.requireValidDatabaseKey
import io.horizontalsystems.tonkit.storage.requireValidKitDatabase
import io.horizontalsystems.tonkit.storage.tonKitNamespace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level
import java.math.BigInteger

class TonKit internal constructor(
    private val address: Address,
    private val apiListener: IApiListener,
    private val accountManager: AccountManager,
    private val jettonManager: JettonManager,
    private val eventManager: EventManager,
    private val transactionSender: TransactionSender?,
    private val rawMessageBroadcaster: RawMessageBroadcaster,
    val network: Network,
    private val transactionSigner: TransactionSigner,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    val receiveAddress get() = address

    val syncStateFlow by accountManager::syncStateFlow
    val accountFlow by accountManager::accountFlow
    val jettonSyncStateFlow by jettonManager::syncStateFlow
    val jettonBalanceMapFlow by jettonManager::jettonBalanceMapFlow
    val eventSyncStateFlow by eventManager::syncStateFlow

    val account get() = accountFlow.value
    val jettonBalanceMap get() = jettonBalanceMapFlow.value

    private val coroutineScope = CoroutineScope(dispatcher)
    private val listenerMutex = Mutex()
    private var eventCollector: Job? = null

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

    /** Stops the listener and waits for the event handling it started, so no database access follows. */
    suspend fun stop() = listenerMutex.withLock {
        stopListener()
        // Released only once completed: a stop cancelled mid-join must leave the collector to the next stop/start.
        eventCollector?.cancelAndJoin()
        eventCollector = null
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

    suspend fun events(tagQuery: TagQuery, beforeLt: Long? = null, limit: Int? = null): List<Event> {
        return eventManager.events(tagQuery, beforeLt, limit)
    }

    fun eventFlow(tagQuery: TagQuery): Flow<EventInfo> {
        return eventManager.eventFlow(tagQuery)
    }

    suspend fun tagTokens(): List<TagToken> {
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

    suspend fun signedTonTransaction(
        recipient: FriendlyAddress,
        amount: SendAmount,
        comment: String?,
    ): SignedRawTonTransaction {
        return transactionSender?.signedTonTransaction(recipient, amount, comment)
            ?: throw WalletError.WatchOnly
    }

    suspend fun signedJettonTransaction(
        jettonWallet: Address,
        recipient: FriendlyAddress,
        amount: BigInteger,
        comment: String?,
    ): SignedRawTonTransaction {
        return transactionSender?.signedJettonTransaction(jettonWallet, recipient, amount, comment)
            ?: throw WalletError.WatchOnly
    }

    /** Offline overload — see [TransactionSender.signedTonTransaction]. */
    suspend fun signedTonTransaction(
        recipient: FriendlyAddress,
        amount: SendAmount,
        comment: String?,
        seqno: Int,
        validUntil: Long,
        fee: BigInteger,
    ): SignedRawTonTransaction {
        return transactionSender?.signedTonTransaction(recipient, amount, comment, seqno, validUntil, fee)
            ?: throw WalletError.WatchOnly
    }

    /** Offline overload — see [TransactionSender.signedJettonTransaction]. */
    suspend fun signedJettonTransaction(
        jettonWallet: Address,
        recipient: FriendlyAddress,
        amount: BigInteger,
        comment: String?,
        seqno: Int,
        validUntil: Long,
        fee: BigInteger,
    ): SignedRawTonTransaction {
        return transactionSender
            ?.signedJettonTransaction(jettonWallet, recipient, amount, comment, seqno, validUntil, fee)
            ?: throw WalletError.WatchOnly
    }

    suspend fun getAccountSeqno(): Int =
        transactionSender?.getAccountSeqno() ?: throw WalletError.WatchOnly

    suspend fun getRawTime(): Int =
        transactionSender?.getRawTime() ?: throw WalletError.WatchOnly

    suspend fun broadcastRawTransaction(
        rawMessage: ByteArray,
        metadata: RawMessageBroadcastMetadata? = null,
    ): RawMessageBroadcastResult {
        return rawMessageBroadcaster.broadcast(rawMessage, metadata)
    }

    suspend fun transactionExistsByMessageHash(messageHash: String): Boolean {
        return rawMessageBroadcaster.transactionExists(messageHash)
    }

    suspend fun startListener() = listenerMutex.withLock {
        // A collector left by a cancelled stop may still be handling an event; never run two at once.
        eventCollector?.takeIf { it.isCancelled }?.join()
        if (eventCollector?.isActive != true) {
            eventCollector = coroutineScope.launch {
                apiListener.transactionFlow.collect {
                    handleEvent(it)
                }
            }
        }
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
        rawMessageBroadcaster.retryQueued()
    }

    suspend fun sign(request: SendRequestEntity, tonWallet: TonWallet): String {
        check(tonWallet is TonWallet.FullAccess)

        return transactionSigner.sign(request, tonWallet)
    }

    suspend fun getDetails(request: SendRequestEntity, tonWallet: TonWallet): Event {
        check(tonWallet is TonWallet.FullAccess)

        return transactionSigner.getDetails(request, tonWallet)
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
        private fun kitLogger(network: Network) = Logger.withTag("TonKit:${network.name}")

        internal fun buildOkHttpClient(
            logger: Logger,
            apiKeys: List<String>,
            eventListenerFactory: EventListener.Factory? = null,
        ): OkHttpClient {
            val builder = OkHttpClient.Builder()
            if (apiKeys.isNotEmpty()) {
                builder.addInterceptor(RateLimitInterceptor(ApiKeyProvider(apiKeys), logger))
            } else {
                builder.addInterceptor(AnonymousRateLimitInterceptor(logger))
            }
            val logging = HttpLoggingInterceptor()
            logging.level = Level.NONE
            // Passive per-call network observer (default null -> behavior unchanged). Covers all
            // tonapi.io REST calls (TonApi). The SSE stream (TonApiListener) is NOT observed: okhttp-sse
            // rebuilds the client with eventListener(EventListener.NONE) at connect time.
            eventListenerFactory?.let { builder.eventListenerFactory(it) }
            return builder
                .addInterceptor(logging)
                .build()
        }

        /**
         * Opens the wallet's database, which [migrateDatabase] must have encrypted with the same
         * [databaseKey] first, and loads the stored account and jetton balances before any network client
         * is created. [databaseKey] must be exactly 32 bytes and [walletId] non-blank, without a path
         * separator or a reserved migration name, otherwise [IllegalArgumentException] is thrown before
         * any I/O.
         *
         * Recovery: [DatabaseMigrationRequiredException] or [DatabaseMigrationInProgressException] mean
         * [migrateDatabase] has to run; [DatabaseKeyMismatchException] keeps the database and is only
         * recoverable through [clear] plus a new key, which loses the stored wallet data.
         */
        suspend fun getInstance(
            tonWallet: TonWallet,
            network: Network,
            context: PlatformContext,
            walletId: String,
            databaseKey: ByteArray,
            apiKeys: List<String> = emptyList(),
            eventListenerFactory: EventListener.Factory? = null,
        ): TonKit {
            requireValidKitDatabase(walletId, network)
            requireValidDatabaseKey(databaseKey)
            val address = tonWallet.address

            val database = tonKitNamespace.open {
                KitDatabase.getInstance(context, kitDatabaseName(walletId, network), databaseKey)
            }
            val accountDao = database.accountDao()
            val jettonDao = database.jettonDao()
            val account = accountDao.getAccount(address)
            val jettonBalances = jettonDao.getJettonBalances()

            val logger = kitLogger(network)
            val okHttpClient = buildOkHttpClient(logger, apiKeys, eventListenerFactory)
            val api = TonApi(network, okHttpClient)
            val transactionSigner = getTransactionSigner(api)

            val accountManager = AccountManager(address, api, accountDao, account, logger)
            val jettonManager = JettonManager(address, api, jettonDao, jettonBalances, logger)
            val eventManager = EventManager(address, api, database.eventDao(), logger)
            val rawMessageBroadcaster = RawMessageBroadcaster(api, database.rawMessageBroadcastDao())

            val transactionSender = when (tonWallet) {
                is TonWallet.FullAccess -> {
                    TransactionSender(
                        api,
                        address,
                        tonWallet.hashSigner,
                        tonWallet.publicKeyEd25519
                    )
                }

                is TonWallet.WatchOnly -> null
            }

            val apiListener = TonApiListener(network, okHttpClient, logger)

            return TonKit(
                address,
                apiListener,
                accountManager,
                jettonManager,
                eventManager,
                transactionSender,
                rawMessageBroadcaster,
                network,
                transactionSigner
            )
        }

        /**
         * Encrypts the wallet's existing plaintext database with [databaseKey] (exactly 32 bytes), keeping
         * its data, and recovers an interrupted migration. Call it before [getInstance] for this [walletId]
         * and [network], with the same key; it is idempotent and checks its arguments like [getInstance],
         * before any I/O.
         *
         * Failures:
         * - [DatabaseKeyMismatchException]: the database was encrypted with another key and is kept
         *   unchanged; only [clear] plus a new key recovers, losing the stored wallet data;
         * - [DatabaseMigrationConflictException]: another process migrates or clears it, so retry later;
         * - [InsufficientDatabaseMigrationSpaceException]: free some space and retry, the plaintext
         *   database is kept unchanged.
         */
        suspend fun migrateDatabase(
            context: PlatformContext,
            network: Network,
            walletId: String,
            databaseKey: ByteArray,
        ): DatabaseMigrationResult {
            requireValidKitDatabase(walletId, network)
            requireValidDatabaseKey(databaseKey)
            return tonKitNamespace.migrate(databaseFile(context, kitDatabaseName(walletId, network)), databaseKey)
        }

        /**
         * Deletes the database of [walletId] on [network] together with any leftovers of an interrupted
         * migration. Throws [IllegalArgumentException] for an invalid [walletId], before any I/O, and
         * [DatabaseMigrationConflictException] while another process migrates or clears it; retry later.
         * Stop the kit first.
         */
        suspend fun clear(context: PlatformContext, network: Network, walletId: String) {
            requireValidKitDatabase(walletId, network)
            tonKitNamespace.clear(databaseFile(context, kitDatabaseName(walletId, network)))
        }

        fun getTonApi(network: Network, apiKeys: List<String> = emptyList()) =
            TonApi(network, buildOkHttpClient(kitLogger(network), apiKeys))

        fun getTransactionSigner(api: TonApi) = TransactionSigner(api)

        suspend fun getJetton(
            network: Network,
            address: Address,
            apiKeys: List<String> = emptyList()
        ): Jetton {
            return getTonApi(network, apiKeys).getJettonInfo(address)
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
