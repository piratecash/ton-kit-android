package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.contract.HashSigner
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.FriendlyAddress
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.models.TonFeeEstimate
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.bitstring.BitString
import org.ton.block.AddrStd
import org.ton.kotlin.crypto.PublicKeyEd25519
import java.math.BigInteger

class TonFeeEstimateTest {
    // Any real signing fails the test: estimates must use the empty signer.
    private val hashSigner = object : HashSigner {
        override fun sign(hash: BitString): BitString = error("estimate must not sign")
    }
    private val publicKey = PublicKeyEd25519(ByteString(*ByteArray(32) { (it + 1).toByte() }))
    private val wallet = TonWallet.FullAccess(publicKey, hashSigner, TonWalletVersion.V4R2)
    private val recipient = FriendlyAddress(AddrStd.parse("0:" + "2f".repeat(32)), isBounceable = false)
    private val jettonWallet = Address.parse("0:" + "3c".repeat(32))
    private val nft = Address.parse("0:" + "4d".repeat(32))
    private val emulateRequests = mutableListOf<String>()
    private val account = """{"address":"0:${"11".repeat(32)}","is_scam":false,"is_wallet":true}"""

    @Test
    fun estimateFee_jettonTransfer_debitsGrossFeePlusTransferPrice() = runBlocking {
        val estimate = sender(trace(actionPhase())).estimateFee(jettonWallet, recipient, BigInteger.TEN, "hi")

        assertEquals(TonFeeEstimate(fee = TOTAL_FEES.big(), totalDebit = (GROSS_FEE + JETTON_TRANSFER_PRICE).big()), estimate)
        assertEquals(JETTON_TRANSFER_PRICE + EMULATED_BALANCE, emulatedBalance())
    }

    @Test
    fun estimateFee_tonMax_debitsGrossFeeOnly() = runBlocking {
        val estimate = sender(trace(actionPhase())).estimateFee(recipient, TonKit.SendAmount.Max, null)

        assertEquals(TonFeeEstimate(fee = TOTAL_FEES.big(), totalDebit = GROSS_FEE.big()), estimate)
        assertEquals(EMULATED_BALANCE, emulatedBalance())
    }

    @Test
    fun estimateFee_missingActionPhase_throws() {
        assertActionPhaseRejected(trace(actionPhase = null))
    }

    @Test
    fun estimateFee_failedActionPhase_throws() {
        assertActionPhaseRejected(trace(actionPhase(success = false, resultCode = 37)))
    }

    @Test
    fun estimateFee_skippedAction_throws() {
        assertActionPhaseRejected(trace(actionPhase(skippedActions = 1)))
    }

    @Test
    fun estimateFee_request_emulatesEmptySignedMessageWithAttachedHeadroom() = runBlocking {
        val request = SendRequestEntity(
            JSONObject().put("messages", JSONArray(listOf(message(100_000_000), message(250_000_000)))),
            "id",
            "dapp",
        )

        val estimate = sender(trace(actionPhase())).estimateFee(request)

        assertEquals(TonFeeEstimate(fee = TOTAL_FEES.big(), totalDebit = (GROSS_FEE + 350_000_000).big()), estimate)
        assertEquals(350_000_000 + EMULATED_BALANCE, emulatedBalance())
        assertTrue(JSONObject(emulateRequests.single()).getString("boc").isNotEmpty())
    }

    @Test
    fun estimateFee_nftTransfer_reportsWholeTraceFeeAndDebitsGrossFeePlusAttached() = runBlocking {
        val estimate = sender(trace(actionPhase(), children = nestedTrace())).estimateFee(nft, recipient, "hi")

        assertEquals(
            TonFeeEstimate(fee = (TOTAL_FEES + NESTED_FEES).big(), totalDebit = (GROSS_FEE + NFT_ATTACHED).big()),
            estimate,
        )
        assertEquals(NFT_ATTACHED + EMULATED_BALANCE, emulatedBalance())
    }

    @Test
    fun estimateFee_coinAndJettonNestedTrace_reportOnlyWalletTransactionFee() = runBlocking {
        val nested = trace(actionPhase(), children = nestedTrace())

        val coin = sender(nested).estimateFee(recipient, TonKit.SendAmount.Max, null)
        val jetton = sender(nested).estimateFee(jettonWallet, recipient, BigInteger.TEN, "hi")

        assertEquals(TOTAL_FEES.big(), coin.fee)
        assertEquals(TOTAL_FEES.big(), jetton.fee)
    }

    private fun assertActionPhaseRejected(trace: String) {
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { sender(trace).estimateFee(jettonWallet, recipient, BigInteger.TEN, null) }
        }
        assertTrue(error.message, error.message.orEmpty().contains("action phase"))
    }

    private fun sender(trace: String) =
        TransactionSender(fakeTonApi(SEQNO, RAW_TIME, trace, emulateRequests), wallet, Network.MainNet)

    private fun emulatedBalance(): Long =
        JSONObject(emulateRequests.single()).getJSONArray("params").getJSONObject(0).getLong("balance")

    private fun message(amount: Long) = JSONObject()
        .put("address", recipient.addrStd.toString(userFriendly = true, bounceable = true))
        .put("amount", amount.toString())

    private fun actionPhase(success: Boolean = true, resultCode: Int = 0, skippedActions: Int = 0) =
        """{"success":$success,"result_code":$resultCode,"total_actions":1,"skipped_actions":$skippedActions,""" +
            """"fwd_fees":$ACTION_FWD_FEES,"total_fees":$ACTION_TOTAL_FEES}"""

    // tonapi leaves end_balance at 0 in emulations, so the debit cannot come from it.
    private fun trace(actionPhase: String?, children: List<String> = emptyList()): String =
        """{"trace":${traceNode(TOTAL_FEES, actionPhase, children)},""" +
            """"risk":{"transfer_all_remaining_balance":false,"ton":0,"jettons":[],"nfts":[]},""" +
            """"event":{"event_id":"e","account":$account,"timestamp":1,"actions":[],"is_scam":false,"lt":1,""" +
            """"in_progress":false,"extra":0}}"""

    private fun traceNode(fees: Long, actionPhase: String? = null, children: List<String> = emptyList()): String {
        val phase = actionPhase?.let { ""","action_phase":$it""" }.orEmpty()
        val nested = if (children.isEmpty()) "" else ""","children":[${children.joinToString(",")}]"""
        return """{"transaction":{"hash":"h","lt":1,"account":$account,"success":true,"utime":1,""" +
            """"orig_status":"active","end_status":"active","total_fees":$fees,"end_balance":0,""" +
            """"transaction_type":"TransOrd","state_update_old":"","state_update_new":"","out_msgs":[],""" +
            """"block":"","aborted":false,"destroyed":false,"raw":""$phase},"interfaces":[]$nested}"""
    }

    private fun nestedTrace() = listOf(
        traceNode(NFT_CONTRACT_FEES, children = listOf(traceNode(NOTIFICATION_FEES))),
        traceNode(EXCESS_FEES),
    )

    private fun Long.big(): BigInteger = BigInteger.valueOf(this)

    private companion object {
        const val SEQNO = 5
        const val RAW_TIME = 1_893_455_000
        const val TOTAL_FEES = 3_000_000L
        const val ACTION_FWD_FEES = 1_200_000L
        const val ACTION_TOTAL_FEES = 400_000L
        const val GROSS_FEE = TOTAL_FEES + ACTION_FWD_FEES - ACTION_TOTAL_FEES
        const val JETTON_TRANSFER_PRICE = 64_000_000L
        const val EMULATED_BALANCE = 1_000_000_000L
        const val NFT_ATTACHED = 50_000_000L
        const val NFT_CONTRACT_FEES = 700_000L
        const val NOTIFICATION_FEES = 90_000L
        const val EXCESS_FEES = 5_000L
        const val NESTED_FEES = NFT_CONTRACT_FEES + NOTIFICATION_FEES + EXCESS_FEES
    }
}
