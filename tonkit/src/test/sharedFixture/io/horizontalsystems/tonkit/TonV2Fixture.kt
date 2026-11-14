package io.horizontalsystems.tonkit

import androidx.room.useReaderConnection
import com.tonapps.security.CryptoBox
import com.tonapps.security.hex
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppManifestEntity
import io.horizontalsystems.tonkit.models.Account
import io.horizontalsystems.tonkit.models.AccountAddress
import io.horizontalsystems.tonkit.models.AccountStatus
import io.horizontalsystems.tonkit.models.Action
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.EventSyncState
import io.horizontalsystems.tonkit.models.Jetton
import io.horizontalsystems.tonkit.models.JettonBalance
import io.horizontalsystems.tonkit.models.JettonTransfer
import io.horizontalsystems.tonkit.models.JettonVerificationType
import io.horizontalsystems.tonkit.models.Tag
import io.horizontalsystems.tonkit.models.TonTransfer
import io.horizontalsystems.tonkit.models.TagQuery
import io.horizontalsystems.tonkit.storage.KitDatabase
import io.horizontalsystems.tonkit.storage.RawMessageBroadcastRecord
import io.horizontalsystems.tonkit.tonconnect.TonConnectKitDatabase
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.assertEquals
import java.io.File
import java.math.BigInteger

// Fixtures generated once by a temporary Robolectric test through the real Room 2.6.1 databases
// (KitDatabase / TonConnectKitDatabase, version = 2; v1 via a version = 1 twin), then deleted.
// All addresses and ids are synthetic.
internal object TonV2Fixture {

    const val KIT_V2_RESOURCE = "fixtures/ton-kit-v2-room-2.6.1.db"
    const val KIT_V1_RESOURCE = "fixtures/ton-kit-v1-room-2.6.1.db"
    const val TON_CONNECT_V2_RESOURCE = "fixtures/ton-connect-v2-room-2.6.1.db"

    fun copyTo(resource: String, target: File): File {
        target.parentFile?.mkdirs()
        val classLoader = requireNotNull(javaClass.classLoader) { "No class loader for test class" }
        val input = requireNotNull(classLoader.getResourceAsStream(resource)) { "Fixture $resource not found on classpath" }
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        return target
    }

    // KitDatabase

    val ownerAddress: Address = Address.parse("0:" + "11".repeat(32))
    private val counterpartyAddress = Address.parse("0:" + "22".repeat(32))

    val account = Account(ownerAddress, balance = 1_500_000_000L, status = AccountStatus.ACTIVE)

    private val fixtureUsdJetton = Jetton(
        address = Address.parse("0:" + "33".repeat(32)),
        name = "Fixture USD",
        symbol = "FUSD",
        decimals = 6,
        image = "https://example.com/fusd.png",
        verification = JettonVerificationType.WHITELIST,
    )
    private val plainJetton = Jetton(
        address = Address.parse("0:" + "44".repeat(32)),
        name = "Fixture Plain",
        symbol = "FPLN",
        decimals = 9,
        image = null,
        verification = JettonVerificationType.NONE,
    )

    val jettonBalances = listOf(
        JettonBalance(fixtureUsdJetton, BigInteger("123456789012345678901234"), Address.parse("0:" + "55".repeat(32))),
        JettonBalance(plainJetton, BigInteger.ZERO, Address.parse("0:" + "66".repeat(32))),
    )

    private val owner = AccountAddress(ownerAddress, name = null, isScam = false, isWallet = true)
    private val counterparty = AccountAddress(counterpartyAddress, name = "fixture.ton", isScam = false, isWallet = true)

    val event = Event(
        id = "fixture-event-" + "ab".repeat(26),
        lt = 47_000_000_000_001L,
        timestamp = 1_700_000_000L,
        scam = false,
        inProgress = false,
        extra = -3_000_000L,
        actions = listOf(
            Action(
                type = Action.Type.TonTransfer,
                status = Action.Status.OK,
                tonTransfer = TonTransfer(owner, counterparty, BigInteger("250000000"), comment = "fixture comment"),
                jettonTransfer = null,
                jettonBurn = null,
                jettonMint = null,
                contractDeploy = null,
                jettonSwap = null,
                smartContractExec = null,
            ),
            Action(
                type = Action.Type.JettonTransfer,
                status = Action.Status.FAILED,
                tonTransfer = null,
                jettonTransfer = JettonTransfer(
                    sender = counterparty,
                    recipient = owner,
                    sendersWallet = Address.parse("0:" + "77".repeat(32)),
                    recipientsWallet = Address.parse("0:" + "55".repeat(32)),
                    amount = BigInteger("1000000"),
                    comment = null,
                    jetton = fixtureUsdJetton,
                ),
                jettonBurn = null,
                jettonMint = null,
                contractDeploy = null,
                jettonSwap = null,
                smartContractExec = null,
            ),
        ),
    )

    val tag = Tag(
        eventId = event.id,
        type = Tag.Type.Outgoing,
        platform = Tag.Platform.Native,
        jettonAddress = null,
        addresses = listOf(counterpartyAddress),
        id = 1,
    )

    val eventSyncState = EventSyncState(allSynced = true)

    val rawMessageBroadcastRecords = listOf(
        RawMessageBroadcastRecord(
            messageHash = "aa".repeat(32),
            bocBase64 = "Zml4dHVyZS1ib2MtMQ==",
            validUntil = 1_700_000_600L,
            senderAddress = ownerAddress.toRaw(),
            seqno = 7,
            firstSendTime = 1_700_000_000L,
            lastSendTime = 1_700_000_060L,
            retriesCount = 2,
        ),
        RawMessageBroadcastRecord(
            messageHash = "bb".repeat(32),
            bocBase64 = "Zml4dHVyZS1ib2MtMg==",
            validUntil = 1_700_001_200L,
            senderAddress = null,
            seqno = null,
            firstSendTime = 1_700_000_100L,
            lastSendTime = 1_700_000_100L,
            retriesCount = 0,
        ),
    )

    // TonConnectKitDatabase

    const val WALLET_ID_1 = "fixture-wallet-1"
    const val WALLET_ID_2 = "fixture-wallet-2"
    const val LAST_SSE_EVENT_ID_KEY = "LastSSEventId"
    const val LAST_SSE_EVENT_ID = "1700000000000001"
    const val SEND_REQUEST_ID = "fixture-request-1"
    const val SEND_REQUEST_DATA_JSON =
        """{"valid_until":1700000600,"from":"0:1111111111111111111111111111111111111111111111111111111111111111","messages":[{"address":"0:2222222222222222222222222222222222222222222222222222222222222222","amount":"100000000"}]}"""

    // NaCl crypto_box test vectors (alice, bob): publicKey == X25519(privateKey), so a later
    // implementation can prove it reads libsodium-shaped key pairs.
    val aliceKeyPair = CryptoBox.KeyPair(
        publicKey = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a".hex(),
        privateKey = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a".hex(),
    )
    val bobKeyPair = CryptoBox.KeyPair(
        publicKey = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f".hex(),
        privateKey = "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb".hex(),
    )

    // Functions, not vals: DAppEntity/SendRequestEntity touch android.net.Uri / org.json on construction.
    fun dApps() = listOf(
        DAppEntity(
            url = "https://dapp-one.example.com",
            walletId = WALLET_ID_1,
            accountId = "0:" + "11".repeat(32),
            testnet = false,
            clientId = "c1".repeat(32),
            keyPair = aliceKeyPair,
            enablePush = false,
            manifest = DAppManifestEntity(
                url = "https://dapp-one.example.com",
                name = "Fixture DApp One",
                iconUrl = "https://dapp-one.example.com/icon.png",
                termsOfUseUrl = "https://dapp-one.example.com/terms",
                privacyPolicyUrl = null,
            ),
        ),
        DAppEntity(
            url = "https://dapp-two.example.com",
            walletId = WALLET_ID_2,
            accountId = "0:" + "88".repeat(32),
            testnet = false,
            clientId = "c2".repeat(32),
            keyPair = bobKeyPair,
            enablePush = true,
            manifest = DAppManifestEntity(
                url = "https://dapp-two.example.com",
                name = "Fixture DApp Two",
                iconUrl = "https://dapp-two.example.com/icon.png",
                termsOfUseUrl = null,
                privacyPolicyUrl = "https://dapp-two.example.com/privacy",
            ),
        ),
    )

    fun sendRequest() = SendRequestEntity(
        data = JSONObject(SEND_REQUEST_DATA_JSON),
        tonConnectRequestId = SEND_REQUEST_ID,
        dAppId = "$WALLET_ID_1:https://dapp-one.example.com",
        id = 1,
    )

    // The v1 fixture holds the same rows, without the RawMessageBroadcastRecord table.
    suspend fun assertKitContents(
        database: KitDatabase,
        expectedRecords: List<RawMessageBroadcastRecord> = rawMessageBroadcastRecords,
    ) {
        assertEquals(account, database.accountDao().getAccount(ownerAddress))
        assertEquals(jettonBalances.toSet(), database.jettonDao().getJettonBalances().toSet())
        assertEquals(listOf(event), database.eventDao().events(listOf(event.id)))
        assertEquals(eventSyncState, database.eventDao().eventSyncState())
        val tagQuery = TagQuery(tag.type, tag.platform, tag.jettonAddress, tag.addresses.single())
        assertEquals(listOf(event), database.eventDao().events(tagQuery, null, 10))
        assertEquals(expectedRecords, database.rawMessageBroadcastDao().records().sortedBy { it.messageHash })
    }

    suspend fun assertTonConnectContents(database: TonConnectKitDatabase) {
        assertEquals(dApps(), database.dAppDao().getAllFlow().first().sortedBy { it.url })
        assertEquals(LAST_SSE_EVENT_ID, database.keyValueDao().get(LAST_SSE_EVENT_ID_KEY))
        val expectedRequest = sendRequest().run { listOf(SEND_REQUEST_DATA_JSON, tonConnectRequestId, dAppId, id.toString()) }
        assertEquals(listOf(expectedRequest), sendRequestRows(database))
    }

    // The kit has no query for pending send requests, so they are read column by column.
    private suspend fun sendRequestRows(database: TonConnectKitDatabase): List<List<String>> =
        database.useReaderConnection { connection ->
            val sql = "SELECT data, tonConnectRequestId, dAppId, id FROM SendRequestEntity ORDER BY id"
            connection.usePrepared(sql) { statement ->
                buildList {
                    while (statement.step()) add(List(4) { column -> statement.getText(column) })
                }
            }
        }
}

internal fun hasPlaintextSqliteHeader(file: File): Boolean {
    if (!file.isFile || file.length() < SQLITE_HEADER.size) return false
    val header = file.inputStream().use { input -> ByteArray(SQLITE_HEADER.size).also { input.read(it) } }
    return header.contentEquals(SQLITE_HEADER)
}

private val SQLITE_HEADER = "SQLite format 3\u0000".encodeToByteArray()
