package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.contract.HashSigner
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.toByteArray
import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.core.entities.TransferEntity
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.FriendlyAddress
import io.horizontalsystems.tonkit.api.IApi
import io.horizontalsystems.tonkit.models.SignedRawTonTransaction
import io.tonapi.models.EmulateMessageToWalletRequestParamsInner
import org.ton.kotlin.crypto.PublicKeyEd25519
import java.math.BigInteger

internal class TonRawTransactionBuilder(
    private val api: IApi,
    private val sender: Address,
    private val hashSigner: HashSigner,
    private val publicKeyEd25519: PublicKeyEd25519,
) {
    suspend fun tonTransfer(
        recipient: FriendlyAddress,
        amount: TonKit.SendAmount,
        comment: String?,
        seqno: Int? = null,
        validUntil: Long? = null,
    ): TransferEntity {
        val amountData = amount.data()

        return transfer(
            value = amountData.value,
            isMax = amountData.isMax,
            recipient = recipient,
            comment = comment,
            walletAddress = sender.toRaw(),
            isTon = true,
            seqno = seqno,
            validUntil = validUntil,
        )
    }

    suspend fun jettonTransfer(
        jettonWallet: Address,
        recipient: FriendlyAddress,
        amount: BigInteger,
        comment: String?,
        seqno: Int? = null,
        validUntil: Long? = null,
    ): TransferEntity {
        return transfer(
            value = amount,
            isMax = false,
            recipient = recipient,
            comment = comment,
            walletAddress = jettonWallet.toRaw(),
            isTon = false,
            seqno = seqno,
            validUntil = validUntil,
        )
    }

    suspend fun estimateFee(transfer: TransferEntity): BigInteger {
        val message = transfer.toSignedMessage(true)
        val params = listOf(EmulateMessageToWalletRequestParamsInner(sender.toRaw(), EMULATED_BALANCE))

        return api.estimateFee(message.base64(), params)
    }

    fun signedTransaction(transfer: TransferEntity, fee: BigInteger): SignedRawTonTransaction {
        val signed = signedTransaction(transfer)
        return SignedRawTonTransaction(
            raw = signed.raw,
            bocBase64 = signed.bocBase64,
            messageHash = signed.messageHash,
            fee = fee,
            validUntil = signed.validUntil,
            senderAddress = signed.senderAddress,
            seqno = signed.seqno,
        )
    }

    fun signedTransaction(transfer: TransferEntity): BuiltRawTonTransaction {
        val message = transfer.toSignedMessage(false)
        val decoded = RawMessageUtils.decode(message.toByteArray())

        return BuiltRawTonTransaction(
            raw = decoded.raw,
            bocBase64 = decoded.bocBase64,
            messageHash = decoded.messageHash,
            validUntil = transfer.validUntil,
            senderAddress = sender.toRaw(),
            seqno = transfer.seqno,
        )
    }

    private suspend fun transfer(
        value: BigInteger,
        isMax: Boolean,
        recipient: FriendlyAddress,
        comment: String?,
        walletAddress: String,
        isTon: Boolean,
        seqno: Int? = null,
        validUntil: Long? = null,
    ): TransferEntity {
        // Explicit seqno/validUntil enable offline building: with both provided
        // no network call is made on this path.
        val resolvedSeqno = seqno ?: api.getAccountSeqno(sender)
        val resolvedValidUntil = validUntil ?: safeTimeout()
        val walletEntity = WalletEntity(
            id = "id",
            publicKey = publicKeyEd25519,
            type = Wallet.Type.Default,
            version = WalletVersion.V4R2,
            label = Wallet.Label("", "", 0),
            hashSigner = hashSigner,
            ledger = null,
        )

        return TransferEntity.Builder(walletEntity)
            .setSeqno(resolvedSeqno)
            .setAmount(Coins.of(value.toBigDecimal(Coins.DEFAULT_DECIMALS)))
            .setMax(isMax)
            .setDestination(recipient.addrStd)
            .setBounceable(recipient.isBounceable)
            .setComment(comment)
            .setValidUntil(resolvedValidUntil)
            .setToken(BalanceEntity(isTon, walletAddress))
            .build()
    }

    private suspend fun safeTimeout(ttl: Long = DEFAULT_TTL_SECONDS): Long {
        return try {
            api.getRawTime() + ttl
        } catch (error: Throwable) {
            System.currentTimeMillis() / MILLIS_IN_SECOND + ttl
        }
    }

    private fun TonKit.SendAmount.data(): AmountData {
        return when (this) {
            is TonKit.SendAmount.Amount -> AmountData(value, false)
            TonKit.SendAmount.Max -> AmountData(BigInteger.ZERO, true)
        }
    }

    private data class AmountData(
        val value: BigInteger,
        val isMax: Boolean,
    )

    companion object {
        private const val DEFAULT_TTL_SECONDS = 5 * 60L
        private const val MILLIS_IN_SECOND = 1000L
        private const val EMULATED_BALANCE = 1_000_000_000L
    }
}

internal data class BuiltRawTonTransaction(
    val raw: ByteArray,
    val bocBase64: String,
    val messageHash: String,
    val validUntil: Long,
    val senderAddress: String,
    val seqno: Int,
)
