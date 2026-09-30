package io.horizontalsystems.tonkit.tonconnect

import co.touchlab.kermit.Logger
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.HashSigner
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.network.get
import com.tonapps.security.CryptoBox
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.WalletProof
import com.tonapps.wallet.data.account.entities.ProofDomainEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppItemEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppManifestEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppAddressItemEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppEventSuccessEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppProofItemReplySuccess
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppReply
import io.horizontalsystems.tonkit.PlatformContext
import io.horizontalsystems.sqlcipher.room.DatabaseKeyMismatchException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationConflictException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationInProgressException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationRequiredException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationResult
import io.horizontalsystems.sqlcipher.room.InsufficientDatabaseMigrationSpaceException
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tonkit.storage.TON_CONNECT_DATABASE_NAME
import io.horizontalsystems.tonkit.storage.databaseFile
import io.horizontalsystems.tonkit.storage.requireValidDatabaseKey
import io.horizontalsystems.tonkit.storage.tonConnectNamespace
import io.horizontalsystems.tonkit.tonconnect.event.EventHandlerSendTransaction
import io.horizontalsystems.tonkit.tonconnect.event.TonConnectEventManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.ton.block.AddrStd
import org.ton.block.StateInit
import org.ton.kotlin.crypto.PublicKeyEd25519

class TonConnectKit(
    private val logger: Logger,
    private val dAppManager: DAppManager,
    private val tonConnectEventManager: TonConnectEventManager,
    private val api: API,
    private val eventHandlerSendTransaction: EventHandlerSendTransaction,
    private val appName: String,
    private val appVersion: String,
) {
    val sendRequestFlow by eventHandlerSendTransaction::sendRequestFlow

    suspend fun reject(request: SendRequestEntity) {
        eventHandlerSendTransaction.reject(request)
    }

    suspend fun badRequest(request: SendRequestEntity) {
        eventHandlerSendTransaction.badRequest(request)
    }

    suspend fun approve(request: SendRequestEntity, boc: String) {
        eventHandlerSendTransaction.approve(request, boc)
    }

    suspend fun disconnect(dAppEntity: DAppEntity) {
        val disconnect = object : DAppReply() {
            override fun toJSON(): JSONObject {
                val json = JSONObject()
                json.put("event", "disconnect")
                json.put("id", System.currentTimeMillis())
                json.put("payload", "{ }")
                return json
            }
        }
        // Older versions stored keys the bridge cannot encrypt to; such a session is only removed locally.
        if (DAppRequestEntity.isValidClientId(dAppEntity.clientId)) {
            tonConnectEventManager.responseToDApp(dAppEntity, disconnect)
        }
        dAppManager.remove(dAppEntity)
    }

    suspend fun connect(
        dAppRequestEntity: DAppRequestEntity,
        manifest: DAppManifestEntity,
        walletId: String,
        tonWallet: TonWallet.FullAccess
    ): DAppEventSuccessEntity {
        val walletEntity = WalletEntity(
            id = walletId,
            publicKey = tonWallet.publicKeyEd25519,
            type = Wallet.Type.Default,
            version = WalletVersion.V4R2,
            hashSigner = tonWallet.hashSigner,
            label = Wallet.Label("", "", 0)
        )
        return connect(
            walletEntity,
            tonWallet.hashSigner,
            manifest,
            dAppRequestEntity.id,
            dAppRequestEntity.payload.items
        )
    }

    private suspend fun connect(
        wallet: WalletEntity,
        hashSigner: HashSigner,
        manifest: DAppManifestEntity,
        clientId: String,
        requestItems: List<DAppItemEntity>,
//        firebaseToken: String?,
    ): DAppEventSuccessEntity = withContext(Dispatchers.IO) {
//        val enablePush = firebaseToken != null
        val app = newApp(manifest, wallet.accountId, wallet.testnet, clientId, wallet.id, false)

        dAppManager.addApp(app)

        // Wait for SSE connection to be established before sending response
        // This fixes race condition where response is sent before SSE listener is ready
        val sseReady = tonConnectEventManager.awaitSseReady()
        if (!sseReady) {
            logger.w { "SSE connection timeout - proceeding with send anyway. Connection may fail." }
        }

        val items = createItems(app, wallet, hashSigner, requestItems)
        val res = DAppEventSuccessEntity(items, appName, appVersion, wallet.maxMessages)
        send(app, res.toJSON())
//        firebaseToken?.let {
//            subscribePush(wallet, app, it)
//        }
        res.copy()
    }

    suspend fun send(
        app: DAppEntity,
        body: JSONObject,
    ) = send(app, body.toString())

    suspend fun send(
        app: DAppEntity,
        body: String,
    ) {
        withContext(Dispatchers.IO) {
            val encrypted = app.encrypt(body)
            if (!api.tonconnectSend(app.publicKeyHex, app.clientId, base64(encrypted))) {
                throw IllegalStateException("Failed sending TonConnect event")
            }
        }
    }

    private fun createItems(
        app: DAppEntity,
        wallet: WalletEntity,
        hashSigner: HashSigner,
        items: List<DAppItemEntity>
    ): List<DAppReply> {
        val result = mutableListOf<DAppReply>()
        for (requestItem in items) {
            if (requestItem.name == DAppItemEntity.TON_ADDR) {
                result.add(
                    createAddressItem(
                        accountId = wallet.accountId,
                        testnet = wallet.testnet,
                        publicKey = wallet.publicKey,
                        stateInit = wallet.contract.stateInit
                    )
                )
            } else if (requestItem.name == DAppItemEntity.TON_PROOF) {
                result.add(
                    createProofItem(
                        payload = requestItem.payload ?: "",
                        domain = app.domain,
                        address = wallet.contract.address,
                        hashSigner = hashSigner,
                    )
                )
            }
        }
        return result
    }

    private fun createProofItem(
        payload: String,
        domain: ProofDomainEntity,
        address: AddrStd,
        hashSigner: HashSigner
    ): DAppProofItemReplySuccess {
        val proof = WalletProof.sign(
            address,
            hashSigner,
            payload,
            domain,
        )
        return DAppProofItemReplySuccess(proof = proof)
    }

    private fun createAddressItem(
        accountId: String,
        testnet: Boolean,
        publicKey: PublicKeyEd25519,
        stateInit: StateInit
    ): DAppAddressItemEntity {
        return DAppAddressItemEntity(
            address = accountId,
            network = if (testnet) TonNetwork.TESTNET else TonNetwork.MAINNET,
            walletStateInit = stateInit,
            publicKey = publicKey
        )
    }

    suspend fun newApp(
        manifest: DAppManifestEntity,
        accountId: String,
        testnet: Boolean,
        clientId: String,
        walletId: String,
        enablePush: Boolean,
    ): DAppEntity = withContext(Dispatchers.IO) {
        val keyPair = CryptoBox.keyPair()
        val app = DAppEntity(
            url = manifest.url,
            accountId = accountId,
            testnet = testnet,
            clientId = clientId,
            keyPair = keyPair,
            walletId = walletId,
            enablePush = enablePush,
            manifest = manifest,
        )
//        localDataSource.addApp(app)
//        val oldValue = _appsFlow.value ?: emptyList()
//        _appsFlow.value = oldValue.plus(app)
        app
    }

    suspend fun getManifest(manifestUrl: String): DAppManifestEntity {
        return withTimeout(MANIFEST_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                loadManifest(manifestUrl)
            }
        }
    }

    private fun loadManifest(url: String): DAppManifestEntity {
        val response = api.defaultHttpClient.get(url)
        return DAppManifestEntity(JSONObject(response))
    }

    fun getDApps(): Flow<List<DAppEntity>> {
        return dAppManager.getAllFlow()
    }

    /**
     * Deletes the dApps of every wallet not in [walletIds], with their pending send requests, in one
     * transaction; an empty [walletIds] deletes all dApps. The bridge resubscribes to the remaining ones.
     */
    suspend fun removeDAppsExcept(walletIds: Collection<String>) {
        dAppManager.removeAllExcept(walletIds)
    }

    fun start() {
        tonConnectEventManager.start()
    }

    fun stop() {
        tonConnectEventManager.stop()
    }

    companion object {
        private const val MANIFEST_TIMEOUT_MS = 5000L

        fun readData(uriString: String): DAppRequestEntity = DAppRequestEntity.parse(uriString)

        /**
         * Encrypts the existing plaintext TON Connect database with [databaseKey] (exactly 32 bytes),
         * keeping its data, and recovers an interrupted migration. Call it before [getInstance] with the
         * same key; it is idempotent and checks the key before any I/O.
         *
         * Failures:
         * - [DatabaseKeyMismatchException]: the database was encrypted with another key and is kept
         *   unchanged; only [clear] plus a new key recovers, losing every stored connection;
         * - [DatabaseMigrationConflictException]: another process migrates or clears it, so retry later;
         * - [InsufficientDatabaseMigrationSpaceException]: free some space and retry, the plaintext
         *   database is kept unchanged.
         */
        suspend fun migrateDatabase(context: PlatformContext, databaseKey: ByteArray): DatabaseMigrationResult {
            requireValidDatabaseKey(databaseKey)
            return tonConnectNamespace.migrate(databaseFile(context, TON_CONNECT_DATABASE_NAME), databaseKey)
        }

        /**
         * Deletes the TON Connect database together with any leftovers of an interrupted migration.
         * Throws [DatabaseMigrationConflictException] while another process migrates or clears it; retry
         * later. Stop the kit first.
         */
        suspend fun clear(context: PlatformContext) {
            tonConnectNamespace.clear(databaseFile(context, TON_CONNECT_DATABASE_NAME))
        }

        /**
         * Opens the TON Connect database, which [migrateDatabase] must have encrypted with the same
         * [databaseKey] first. [databaseKey] must be exactly 32 bytes, otherwise [IllegalArgumentException]
         * is thrown before any I/O.
         *
         * Recovery: [DatabaseMigrationRequiredException] or [DatabaseMigrationInProgressException] mean
         * [migrateDatabase] has to run; [DatabaseKeyMismatchException] keeps the database and is only
         * recoverable through [clear] plus a new key, which loses every stored connection.
         */
        suspend fun getInstance(
            context: PlatformContext,
            databaseKey: ByteArray,
            appName: String,
            appVersion: String,
        ): TonConnectKit {
            requireValidDatabaseKey(databaseKey)
            val database = tonConnectNamespace.open {
                TonConnectKitDatabase.getInstance(context, TON_CONNECT_DATABASE_NAME, databaseKey)
            }
            val logger = Logger.withTag("TonConnectKit:MainNet")
            val api = API(logger)
            val dAppManager = DAppManager(database.dAppDao())
            val localStorage = LocalStorage(database.keyValueDao())
            val tonConnectEventManager = TonConnectEventManager(dAppManager, api, localStorage, logger)

            val handler = EventHandlerDisconnect(dAppManager, tonConnectEventManager)
            tonConnectEventManager.registerHandler(handler)

            val eventHandlerSendTransaction = EventHandlerSendTransaction(
                tonConnectEventManager,
                database.sendRequestDao()
            )
            tonConnectEventManager.registerHandler(eventHandlerSendTransaction)

            return TonConnectKit(
                logger,
                dAppManager,
                tonConnectEventManager,
                api,
                eventHandlerSendTransaction,
                appName,
                appVersion
            )
        }
    }
}

sealed class TonConnectError : Error()

class UriError(override val message: String) : TonConnectError()
class ManifestLoadError(override val message: String) : TonConnectError()
