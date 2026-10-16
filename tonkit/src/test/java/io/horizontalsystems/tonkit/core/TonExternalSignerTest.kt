package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.TonTransferHelper
import com.tonapps.blockchain.ton.contract.HashSigner
import com.tonapps.blockchain.ton.contract.PrivateKeyHashSigner
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.icu.Coins as UiCoins
import com.tonapps.ledger.ton.TonPayloadFormat
import com.tonapps.ledger.ton.Transaction
import com.tonapps.ledger.ton.recognizePayload
import com.tonapps.tonkeeper.core.entities.TransferEntity
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.FriendlyAddress
import io.horizontalsystems.tonkit.api.TonApi
import io.horizontalsystems.tonkit.models.Network
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.bigint.BigInt
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.Message
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.buildCell
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.kotlin.crypto.PublicKeyEd25519
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.loadTlb
import org.ton.tlb.storeTlb
import java.math.BigInteger

/**
 * Vectors: LedgerHQ/app-ton test client (tonsdk) requests, hashes and signatures checked on speculos; key
 * m/44'/607'/0'/0'/0'/0' of the speculos seed.
 */
class TonExternalSignerTest {
    private val publicKey = PublicKeyEd25519(ByteString(*hex("9aff66dcc01b79787f6038c8070b8f7b9f27e381297c846a59f743bb075ed61c")))
    private val ownRaw = "0:d67b2fc518f85dde98667ab7555e2fb3508ba37f23b8fb6f72fd54f5c83d2679"
    private val own = AddrStd.parse(ownRaw)
    private val destination = AddrStd.parse("0:" + "2f".repeat(32))
    private val jettonWallet = AddrStd.parse("0:" + "3c".repeat(32))
    private val timeout = 1_893_456_000
    private val deviceWallet = walletEntity(DeviceOnlyHashSigner, publicKey)
    private val mnemonicKey = PrivateKeyEd25519(ByteArray(32) { 7 })
    private val mnemonicWallet = walletEntity(PrivateKeyHashSigner(mnemonicKey), mnemonicKey.publicKey())
    private val contract = deviceWallet.contract
    private val forwardHi = buildCell { storeUInt(0, 32); storeBytes("hi".toByteArray()) }

    private val commentVector = Vector(
        transaction = Transaction(
            destination = destination, sendMode = 3, seqno = 7, timeout = timeout, bounceable = false,
            coins = Coins.ofNano(1_500_000_000), payload = TonPayloadFormat.Comment("ledger-kit test"),
            body = buildCell { storeUInt(0, 32); storeBytes("ledger-kit test".toByteArray()) },
        ),
        hash = "e8bf086eb8c8c828a91d74212f557d9382ea856a8f1185c7f2ff2046c76139d1",
        signature = "e7dc285a2ea99219dc03d987bd19dc829dc6974ccb1ba4238fb7529b8fead98931bd9f7d28adc962210a11d91d8b9cc9eaa492d8d197d65abfa24ee8b1294703",
    )
    private val jettonVector = Vector(
        transaction = Transaction(
            destination = jettonWallet, sendMode = 3, seqno = 8, timeout = timeout, bounceable = true,
            coins = Coins.ofNano(50_000_000),
            body = jettonBody(BigInteger.valueOf(12345), Coins.ofNano(1_000_000), null, Coins.ofNano(1), forwardHi),
        ),
        hash = "3e464d63823def109a8ee42259394be0193f50330b3377411412dee821ef632d",
    )
    private val unsafeVector = Vector(
        transaction = Transaction(
            destination = destination, sendMode = 1, seqno = 9, timeout = timeout, bounceable = true,
            coins = Coins.ofNano(10_000_000), body = buildCell { storeUInt(0x12345678, 32); storeUInt(42, 64) },
        ),
        hash = "69aa5034d09bf146faea169d2e937e90e1e3714db04bcb8dd894f96d4afba5ad",
    )
    private val plainVector = Vector(
        transaction = Transaction(
            destination = destination, sendMode = 1, seqno = 0, timeout = timeout, bounceable = true,
            coins = Coins.ofNano(100_000_000),
        ),
        hash = "ca1ec07b28981479f5174aa321d5b64d67cc284c6db5d2371dbe69c1072027ba",
    )
    private val jettonNoForwardVector = Vector(
        transaction = Transaction(
            destination = jettonWallet, sendMode = 3, seqno = 10, timeout = timeout, bounceable = true,
            coins = Coins.ofNano(50_000_000),
            body = jettonBody(BigInteger.ZERO, Coins.ofNano(5_000_000), null, Coins.ofNano(0), null),
        ),
        hash = "062624af3adcd1177e6ab5217cb43d1413fb5624ca6039c518f913ca9db883ee",
    )
    private val max120 = Coins(BigInt(BigInteger.ONE.shiftLeft(120).subtract(BigInteger.ONE).toString()))
    private val jettonStateInitVector = Vector(
        transaction = Transaction(
            destination = jettonWallet, sendMode = 1, seqno = 11, timeout = timeout, bounceable = true,
            coins = max120,
            stateInit = StateInit(buildCell { storeUInt(0xC0DE, 16) }, buildCell { storeUInt(0xDA7A, 16) }),
            body = jettonBody(
                BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE), max120,
                buildCell { storeUInt(0xC057, 16) }, max120, forwardHi,
            ),
        ),
        hash = "3190fae27e2d2fabf21e10a8f3380ee6975146cb83262bbac289c01d83a70ee2",
    )
    private val vectors = listOf(
        commentVector, jettonVector, unsafeVector, plainVector, jettonNoForwardVector, jettonStateInitVector,
    )

    @Test
    fun deviceTransferHash_appTonVectors_matches() {
        vectors.forEach { assertEquals(it.hash, hex(deviceTransferHash(it.transaction))) }
    }

    @Test
    fun address_externalSigner_isV4R2AddressOfDeviceKey() {
        assertEquals(Address.parse(ownRaw), TonWallet.ExternalSigner(publicKey, FakeDevice()).address)
    }

    @Test
    fun signedMessage_appTonVectors_sendsTheCellTheDeviceSigned() = runBlocking {
        // A seqno 0 transfer is built by ton-kit with an all-ones timeout, as the plain vector is not.
        (vectors - plainVector).forEach { vector ->
            val device = FakeDevice(signedHash = hex(vector.hash))

            val message = device.signedMessage(contract, vector.transaction)

            assertArrayEquals(device.signature, message.signature())
        }
    }

    @Test
    fun signedTonTransaction_commentVector_sendsDeviceSignature() = runBlocking {
        val device = FakeDevice(signedHash = hex(commentVector.hash), signature = hex(commentVector.signature!!))

        val signed = sender(device).signedTonTransaction(
            recipient = FriendlyAddress(destination, isBounceable = false),
            amount = TonKit.SendAmount.Amount(BigInteger.valueOf(1_500_000_000)),
            comment = "ledger-kit test",
            seqno = 7,
            validUntil = timeout.toLong(),
            fee = BigInteger.ONE,
        )

        assertEquals(commentVector.transaction, device.transactions.single())
        assertArrayEquals(hex(commentVector.signature), BagOfCells(signed.raw).first().signature())
    }

    @Test
    fun signedJettonTransaction_externalSigner_hintAndSentBodyShareQueryId() = runBlocking {
        val device = FakeDevice()

        val signed = sender(device).signedJettonTransaction(
            jettonWallet = Address(jettonWallet),
            recipient = FriendlyAddress(destination, isBounceable = true),
            amount = BigInteger.valueOf(1_000_000),
            comment = "hi",
            seqno = 8,
            validUntil = timeout.toLong(),
            fee = BigInteger.ONE,
        )

        val transaction = device.transactions.single()
        val hint = transaction.payload as TonPayloadFormat.JettonTransfer
        val body = requireNotNull(transaction.body)
        assertEquals(hint, recognizePayload(body))
        assertEquals(hint.queryId, sentJettonQueryId(signed.raw))
        assertEquals(forwardHi, hint.forwardPayload)
    }

    @Test
    fun ledgerTransaction_jetton_bodyIsTheMnemonicSentBody() {
        val transfer = jettonTransfer(seqno = 7, wallet = deviceWallet)

        val body = requireNotNull(transfer.ledgerTransaction.body)

        assertEquals(transfer.queryId, (transfer.ledgerTransaction.payload as TonPayloadFormat.JettonTransfer).queryId)
        assertTrue(transfer.unsignedBody.refs.single().refs.contains(body))
    }

    @Test
    fun send_undeployedWallet_signsDeployTransferWithStateInit() = runBlocking {
        val api = FakeApi(currentSeqno = listOf(0))
        val device = FakeDevice()

        TransactionSender(api, Address(own), publicKey, device)
            .send(FriendlyAddress(destination, false), TonKit.SendAmount.Amount(BigInteger.TEN), null)

        val transaction = device.transactions.single()
        assertEquals(-1, transaction.timeout)
        assertEquals(stateInitCell(contract.stateInit), transaction.stateInit?.let(::stateInitCell))
        assertNull(transaction.body)
        assertEquals(1, api.sendCalls)
    }

    @Test
    fun broadcastBoc_everyExternalSignerPath_carriesTheTransferTheDeviceSigned() = runBlocking {
        val recipient = FriendlyAddress(destination, isBounceable = false)
        val jetton = Address(jettonWallet)
        val tenNano = TonKit.SendAmount.Amount(BigInteger.TEN)
        val paths = listOf<suspend (FakeDevice) -> ByteArray>(
            { device -> sentBoc(device, seqno = 3) { send(recipient, tenNano, "memo") } },
            { device -> sentBoc(device, seqno = 0) { send(recipient, tenNano, "memo") } },
            { device -> sentBoc(device, seqno = 0) { send(jetton, recipient, BigInteger.TEN, "hi") } },
            { device -> onlineSender(device, seqno = 0).signedTonTransaction(recipient, tenNano, "memo").raw },
            { device -> onlineSender(device, seqno = 4).signedJettonTransaction(jetton, recipient, BigInteger.TEN, null).raw },
            { device -> sender(device).signedTonTransaction(recipient, tenNano, "memo", 7, timeout.toLong(), BigInteger.ONE).raw },
            { device -> sender(device).signedJettonTransaction(jetton, recipient, BigInteger.TEN, "hi", 8, timeout.toLong(), BigInteger.ONE).raw },
            { device ->
                val payload = jettonBody(BigInteger.valueOf(7), Coins.ofNano(5), null, Coins.ofNano(1), forwardHi)
                base64(signer().sign(sendRequest(message(payload)), TonWallet.ExternalSigner(publicKey, device)))
            },
        )

        paths.forEachIndexed { index, path ->
            val device = FakeDevice()

            val boc = path(device)

            assertEquals("path $index", hex(device.signedHashes.single()), hex(broadcastTransferHash(boc)))
            assertArrayEquals("path $index", device.signature, BagOfCells(boc).first().signature())
        }
    }

    @Test
    fun send_deviceSignedAnotherTransfer_throwsAndDoesNotBroadcast() {
        val api = FakeApi(currentSeqno = listOf(3))
        val device = FakeDevice(signedHash = ByteArray(32))

        assertThrows(TonExternalSignerException.SignedHashMismatch::class.java) {
            runBlocking { TransactionSender(api, Address(own), publicKey, device).send(FriendlyAddress(destination, false), TonKit.SendAmount.Max, null) }
        }
        assertEquals(0, api.sendCalls)
    }

    @Test
    fun ledgerTransaction_commentNotShowable_bodyWithoutHint() {
        listOf("привет", "a".repeat(121)).forEach { comment ->
            val transaction = tonTransfer(comment).ledgerTransaction

            assertNull(transaction.payload)
            assertEquals(TonTransferHelper.text(comment), transaction.body)
        }
    }

    @Test
    fun ledgerTransaction_asciiComment120_commentHint() {
        val comment = "~".repeat(120)

        assertEquals(TonPayloadFormat.Comment(comment), tonTransfer(comment).ledgerTransaction.payload)
    }

    @Test
    fun sign_externalSignerOneJettonMessage_signsWithHint() = runBlocking {
        val payload = jettonBody(BigInteger.valueOf(7), Coins.ofNano(5), null, Coins.ofNano(1), forwardHi)
        val device = FakeDevice()

        val boc = signer().sign(sendRequest(message(payload)), TonWallet.ExternalSigner(publicKey, device))

        val transaction = device.transactions.single()
        assertEquals(SEQNO, transaction.seqno)
        assertEquals(RAW_TIME + 300, transaction.timeout)
        assertEquals(payload, transaction.body)
        assertEquals(BigInteger.valueOf(7), (transaction.payload as TonPayloadFormat.JettonTransfer).queryId)
        assertArrayEquals(device.signature, BagOfCells(base64(boc)).first().signature())
    }

    @Test
    fun sign_externalSignerUnknownPayload_signsBlind() = runBlocking {
        val payload = buildCell { storeUInt(0x25938561, 32); storeUInt(1, 64) }
        val device = FakeDevice()

        signer().sign(sendRequest(message(payload)), TonWallet.ExternalSigner(publicKey, device))

        assertNull(device.transactions.single().payload)
        assertEquals(payload, device.transactions.single().body)
    }

    @Test
    fun sign_externalSignerTwoMessages_throwsWithoutSigning() {
        val device = FakeDevice()
        val request = sendRequest(message(forwardHi), message(forwardHi))

        val error = assertThrows(TonExternalSignerException.UnsupportedMessageCount::class.java) {
            runBlocking { signer().sign(request, TonWallet.ExternalSigner(publicKey, device)) }
        }
        assertEquals(2, error.count)
        assertTrue(device.transactions.isEmpty())
    }

    @Test
    fun recognizePayload_jettonTransferWithCustomPayload_returnsNull() {
        val body = jettonBody(BigInteger.ONE, Coins.ofNano(5), forwardHi, Coins.ofNano(1), null)

        assertNull(recognizePayload(body))
    }

    @Test
    fun recognizePayload_jettonForwardPayloadInline_returnsNull() {
        val body = CellBuilder.createCell {
            storeBits(jettonBody(BigInteger.ONE, Coins.ofNano(5), null, Coins.ofNano(1), null).bits)
            storeBytes("hi".toByteArray())
        }

        assertNull(recognizePayload(body))
    }

    @Test
    fun toSignedMessage_mnemonicFixedQueryId_unchanged() {
        val wallet = mnemonicWallet

        assertEquals(GOLDEN_JETTON_SEQNO_7, jettonTransfer(seqno = 7, wallet = wallet).toSignedMessage(false).base64())
        assertEquals(GOLDEN_JETTON_SEQNO_0, jettonTransfer(seqno = 0, wallet = wallet).toSignedMessage(false).base64())
        assertEquals(GOLDEN_TON_SEQNO_7, tonTransfer("memo", seqno = 7, wallet = wallet).toSignedMessage(false).base64())
        assertEquals(GOLDEN_TON_SEQNO_0, tonTransfer(null, seqno = 0, wallet = wallet).toSignedMessage(false).base64())
    }

    private fun sender(device: FakeDevice) =
        TransactionSender(FakeApi(throwOnAnyCall = IllegalStateException("offline")), Address(own), publicKey, device)

    private fun onlineSender(device: FakeDevice, seqno: Int, api: FakeApi = FakeApi(currentSeqno = listOf(seqno))) =
        TransactionSender(api, Address(own), publicKey, device)

    private suspend fun sentBoc(device: FakeDevice, seqno: Int, send: suspend TransactionSender.() -> Unit): ByteArray {
        val api = FakeApi(currentSeqno = listOf(seqno))
        onlineSender(device, seqno, api).send()
        return base64(api.sentBocs.single())
    }

    /** Parses the external message as the network would and hashes the body after the signature, as v4R2 checks it. */
    private fun broadcastTransferHash(boc: ByteArray): ByteArray {
        val message = Message.tlbCodec(AnyTlbConstructor).loadTlb(BagOfCells(boc).first())
        val body = requireNotNull(message.body.x ?: message.body.y?.value)
        val bits = body.beginParse()
        bits.skipBits(512)
        // Bit by bit: ton-kotlin 0.5.0 bulk loadBits after skipBits overruns its buffer.
        val unsigned = buildCell {
            repeat(bits.remainingBits) { storeBit(bits.loadBit()) }
            storeRefs(body.refs)
        }
        return unsigned.hash().toByteArray()
    }

    private fun walletEntity(signer: HashSigner, key: PublicKeyEd25519) =
        WalletEntity("id", key, Wallet.Type.Default, WalletVersion.V4R2, Wallet.Label("", "", 0), signer)

    private fun tonTransfer(
        comment: String?,
        seqno: Int = 7,
        wallet: WalletEntity = deviceWallet,
    ): TransferEntity {
        return TransferEntity(
            wallet, BalanceEntity(true, ownRaw), destination, UiCoins.of(BigInteger.valueOf(1_500_000_000).toBigDecimal(9)),
            false, seqno, timeout.toLong(), false, comment,
        )
    }

    private fun jettonTransfer(seqno: Int, wallet: WalletEntity): TransferEntity {
        return TransferEntity(
            wallet, BalanceEntity(false, jettonWallet.toString(userFriendly = false)), destination,
            UiCoins.of(BigInteger.valueOf(1_000_000).toBigDecimal(9)), false, seqno, timeout.toLong(), true, "hi",
            queryId = BigInteger.valueOf(12345),
        )
    }

    private fun signer(): TransactionSigner {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            val json = when {
                path.endsWith("/seqno") -> """{"seqno":$SEQNO}"""
                path.endsWith("/get_time") -> """{"time":$RAW_TIME}"""
                else -> error("Unexpected request $path")
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return TransactionSigner(TonApi(Network.MainNet, client))
    }

    private fun message(payload: Cell) = JSONObject()
        .put("address", jettonWallet.toString(userFriendly = true, bounceable = true))
        .put("amount", "100000000")
        .put("payload", payload.base64())

    private fun sendRequest(vararg messages: JSONObject) =
        SendRequestEntity(JSONObject().put("messages", JSONArray(messages.toList())), "id", "dapp")

    private fun sentJettonQueryId(raw: ByteArray): BigInteger {
        val transferBody = BagOfCells(raw).first().refs.last()
        val internalMessage = transferBody.refs.single()
        val body = internalMessage.refs.single()
        return (recognizePayload(body) as TonPayloadFormat.JettonTransfer).queryId ?: BigInteger.ZERO
    }

    private fun stateInitCell(stateInit: StateInit): Cell = buildCell { storeTlb(StateInit, stateInit) }

    private fun Cell.signature(): ByteArray = refs.last().beginParse().loadByteArray(64)

    private class Vector(val transaction: Transaction, val hash: String, val signature: String? = null)

    /** Records what it was asked to sign; by default answers with the hash the TON app would compute. */
    private class FakeDevice(
        private val signedHash: ByteArray? = null,
        val signature: ByteArray = ByteArray(64) { 5 },
    ) : TonTransactionDeviceSigner {
        val transactions = mutableListOf<Transaction>()
        val signedHashes = mutableListOf<ByteArray>()

        override suspend fun sign(transaction: Transaction): TonDeviceSignature {
            transactions += transaction
            val hash = signedHash ?: deviceTransferHash(transaction)
            signedHashes += hash
            return TonDeviceSignature(signature, hash)
        }
    }

    private companion object {
        const val SEQNO = 5
        const val RAW_TIME = 1_893_455_000
        const val WALLET_ID = 698983191L

        const val GOLDEN_TON_SEQNO_7 = "te6ccgEBAwEAtQABRYgAVN3Nb/6Df9lfxwZwxkuPUSvp/JnGjWMdc4KUAqWsxTgMAQGc9rKiMJlfiFpd/wHoKmkA7B4cVYLs9T+LVtTh+5sXHHLtuJfBruJSxu1NJp2Fg2VHep5uB8CkY8C3wKPMoAC9ASmpoxdw29iAAAAABwADAgB4QgAXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl6LLQXgAAAAAAAAAAAAAAAAAAAAAAABtZW1v"
        const val GOLDEN_TON_SEQNO_0 = "te6ccgECGQEAA7UAAkWIAFTdzW/+g3/ZX8cGcMZLj1Er6fyZxo1jHXOClAKlrMU4HgECAgE0BAUBnABpyHbMxRZh14YwC2FvkWZG9GLlSYo0bAMt2nc2wZg4MeyxEfVkKtXLpSzGVbYG+xeVIO5eTmee75cFrbcDUQkpqaMX/////wAAAAAAAwMCaUIAF5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eiy0F4AAAAAAAAAAAAAAAAAAIyBAUBFP8A9KQT9LzyyAsGAFEAAAAAKamjF+pKbGPinFIKvvVQexMuxfmVR3auvr57kkIe6mkURtIsQAIBIAcIAgFICQoE+PKDCNcYINMf0x/THwL4I7vyZO1E0NMf0x/T//QE0VFDuvKhUVG68qIF+QFUEGT5EPKj+AAkpMjLH1JAyx9SMMv/UhD0AMntVPgPAdMHIcAAn2xRkyDXSpbTB9QC+wDoMOAhwAHjACHAAuMAAcADkTDjDQOkyMsfEssfy/8VFhcYAubQAdDTAyFxsJJfBOAi10nBIJJfBOAC0x8hghBwbHVnvSKCEGRzdHK9sJJfBeAD+kAwIPpEAcjKB8v/ydDtRNCBAUDXIfQEMFyBAQj0Cm+hMbOSXwfgBdM/yCWCEHBsdWe6kjgw4w0DghBkc3RyupJfBuMNCwwCASANDgB4AfoA9AQw+CdvIjBQCqEhvvLgUIIQcGx1Z4MesXCAGFAEywUmzxZY+gIZ9ADLaRfLH1Jgyz8gyYBA+wAGAIpQBIEBCPRZMO1E0IEBQNcgyAHPFvQAye1UAXKwjiOCEGRzdHKDHrFwgBhQBcsFUAPPFiP6AhPLassfyz/JgED7AJJfA+ICASAPEABZvSQrb2omhAgKBrkPoCGEcNQICEekk30pkQzmkD6f+YN4EoAbeBAUiYcVnzGEAgFYERIAEbjJftRNDXCx+AA9sp37UTQgQFA1yH0BDACyMoHy//J0AGBAQj0Cm+hMYAIBIBMUABmtznaiaEAga5Drhf/AABmvHfaiaEAQa5DrhY/AAG7SB/oA1NQi+QAFyMoHFcv/ydB3dIAYyMsFywIizxZQBfoCFMtrEszMyXP7AMhAFIEBCPRR8qcCAHCBAQjXGPoA0z/IVCBHgQEI9FHyp4IQbm90ZXB0gBjIywXLAlAGzxZQBPoCFMtqEssfyz/Jc/sAAgBsgQEI1xj6ANM/MFIkgQEI9Fnyp4IQZHN0cnB0gBjIywXLAlAFzxZQA/oCE8tqyx8Syz/Jc/sAAAr0AMntVA=="
        const val GOLDEN_JETTON_SEQNO_7 = "te6ccgECBQEAAQ0AAUWIAFTdzW/+g3/ZX8cGcMZLj1Er6fyZxo1jHXOClAKlrMU4DAEBnA97VPmiANQHivikRCe9Ah50iXh+0UDtilka8qw9oEQ4OnozgYtXc20HMtOQMGV6vCArZJ17sRG8YEeTrRtPEggpqaMXcNvYgAAAAAcAAwIBaGIAHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4gHoSAAAAAAAAAAAAAAAAAAAEDAagPin6lAAAAAAAAMDkw9CQIAF5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5fAAqbua3/0G/7K/jgzhjJceolfT+TONGsY65wUoBUtZinAgMEAAwAAAAAaGk="
        const val GOLDEN_JETTON_SEQNO_0 = "te6ccgECGwEABBUAAkWIAFTdzW/+g3/ZX8cGcMZLj1Er6fyZxo1jHXOClAKlrMU4HgECAgE0BAUBnMaVqseNtGSdCKUIuxVfUdcKVZ+YJbxMLO8viEr0W6k2K81GT4z+JWgZlBN5Ozz02FAKA2lqOeuB2wS20wryBAopqaMX/////wAAAAAAAwMDaWIAHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4eHh4gHoSAAAAAAAAAAAAAAAAAAAI2BAUGART/APSkE/S88sgLBwBRAAAAACmpoxfqSmxj4pxSCr71UHsTLsX5lUd2rr6+e5JCHuppFEbSLEABqA+KfqUAAAAAAAAwOTD0JAgAXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl5eXl8ACpu5rf/Qb/sr+ODOGMlx6iV9P5M40axjrnBSgFS1mKcCAxoCASAICQIBSAoLBPjygwjXGCDTH9Mf0x8C+CO78mTtRNDTH9Mf0//0BNFRQ7ryoVFRuvKiBfkBVBBk+RDyo/gAJKTIyx9SQMsfUjDL/1IQ9ADJ7VT4DwHTByHAAJ9sUZMg10qW0wfUAvsA6DDgIcAB4wAhwALjAAHAA5Ew4w0DpMjLHxLLH8v/FhcYGQLm0AHQ0wMhcbCSXwTgItdJwSCSXwTgAtMfIYIQcGx1Z70ighBkc3RyvbCSXwXgA/pAMCD6RAHIygfL/8nQ7UTQgQFA1yH0BDBcgQEI9ApvoTGzkl8H4AXTP8glghBwbHVnupI4MOMNA4IQZHN0crqSXwbjDQwNAgEgDg8AeAH6APQEMPgnbyIwUAqhIb7y4FCCEHBsdWeDHrFwgBhQBMsFJs8WWPoCGfQAy2kXyx9SYMs/IMmAQPsABgCKUASBAQj0WTDtRNCBAUDXIMgBzxb0AMntVAFysI4jghBkc3Rygx6xcIAYUAXLBVADzxYj+gITy2rLH8s/yYBA+wCSXwPiAgEgEBEAWb0kK29qJoQICga5D6AhhHDUCAhHpJN9KZEM5pA+n/mDeBKAG3gQFImHFZ8xhAIBWBITABG4yX7UTQ1wsfgAPbKd+1E0IEBQNch9AQwAsjKB8v/ydABgQEI9ApvoTGACASAUFQAZrc52omhAIGuQ64X/wAAZrx32omhAEGuQ64WPwABu0gf6ANTUIvkABcjKBxXL/8nQd3SAGMjLBcsCIs8WUAX6AhTLaxLMzMlz+wDIQBSBAQj0UfKnAgBwgQEI1xj6ANM/yFQgR4EBCPRR8qeCEG5vdGVwdIAYyMsFywJQBs8WUAT6AhTLahLLH8s/yXP7AAIAbIEBCNcY+gDTPzBSJIEBCPRZ8qeCEGRzdHJwdIAYyMsFywJQBc8WUAP6AhPLassfEss/yXP7AAAK9ADJ7VQADAAAAABoaQ=="

        fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        fun hex(value: ByteArray): String = value.joinToString("") { "%02x".format(it) }

        /** TEP-74 transfer with both payloads in references, built field by field. */
        fun jettonBody(queryId: BigInteger, amount: Coins, custom: Cell?, forwardAmount: Coins, forward: Cell?): Cell =
            CellBuilder.createCell {
                storeUInt(0x0f8a7ea5, 32)
                storeBytes(queryId.toByteArray().takeLast(8).toByteArray().let { ByteArray(8 - it.size) + it })
                storeTlb(Coins, amount)
                storeTlb(MsgAddressInt, AddrStd.parse("0:" + "2f".repeat(32)))
                storeTlb(MsgAddressInt, AddrStd.parse("0:d67b2fc518f85dde98667ab7555e2fb3508ba37f23b8fb6f72fd54f5c83d2679"))
                storeMaybeRef(custom)
                storeTlb(Coins, forwardAmount)
                storeMaybeRef(forward)
            }

        /** The v4R2 transfer cell as app-ton `hash_tx` builds it from the request fields. */
        fun deviceTransferHash(transaction: Transaction): ByteArray {
            val message = CellBuilder.createCell {
                storeBit(false)
                storeBit(true)
                storeBit(transaction.bounceable)
                storeBit(false)
                storeUInt(0, 2)
                storeTlb(MsgAddressInt, transaction.destination)
                storeTlb(Coins, transaction.coins)
                storeBit(false)
                storeTlb(Coins, Coins())
                storeTlb(Coins, Coins())
                storeUInt(0, 64)
                storeUInt(0, 32)
                val stateInit = transaction.stateInit
                if (stateInit == null) {
                    storeBit(false)
                } else {
                    storeBit(true)
                    storeBit(true)
                    storeRef(buildCell { storeTlb(StateInit, stateInit) })
                }
                storeMaybeRef(transaction.body)
            }
            return CellBuilder.createCell {
                storeUInt(WALLET_ID, 32)
                storeUInt(transaction.timeout.toUInt().toLong(), 32)
                storeUInt(transaction.seqno, 32)
                storeUInt(0, 8)
                storeUInt(transaction.sendMode, 8)
                storeRef(message)
            }.hash().toByteArray()
        }

        private fun CellBuilder.storeMaybeRef(cell: Cell?) {
            storeBit(cell != null)
            cell?.let { storeRef(it) }
        }
    }
}
