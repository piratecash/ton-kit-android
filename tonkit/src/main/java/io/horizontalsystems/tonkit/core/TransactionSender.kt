package io.horizontalsystems.tonkit.core

import com.tonapps.wallet.data.core.entity.SendRequestEntity
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.FriendlyAddress
import io.horizontalsystems.tonkit.api.IApi
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.models.SignedRawTonTransaction
import io.horizontalsystems.tonkit.models.TonFeeEstimate
import java.math.BigInteger

class TransactionSender private constructor(
    private val api: IApi,
    private val rawTransactionBuilder: TonRawTransactionBuilder,
) {
    constructor(api: IApi, tonWallet: TonWallet.FullAccess, network: Network) :
        this(api, TonRawTransactionBuilder(api, tonWallet.walletEntity(network)))

    internal constructor(api: IApi, tonWallet: TonWallet.ExternalSigner, network: Network) :
        this(api, TonRawTransactionBuilder(api, tonWallet.walletEntity(network), tonWallet.deviceSigner))

    suspend fun estimateFee(
        recipient: FriendlyAddress,
        amount: TonKit.SendAmount,
        comment: String?
    ): TonFeeEstimate {
        return rawTransactionBuilder.estimateFee(
            rawTransactionBuilder.tonTransfer(recipient, amount, comment)
        )
    }

    suspend fun estimateFee(
        jettonWallet: Address,
        recipient: FriendlyAddress,
        amount: BigInteger,
        comment: String?
    ): TonFeeEstimate {
        return rawTransactionBuilder.estimateFee(
            rawTransactionBuilder.jettonTransfer(jettonWallet, recipient, amount, comment)
        )
    }

    suspend fun estimateFee(
        nft: Address,
        recipient: FriendlyAddress,
        comment: String?,
    ): TonFeeEstimate {
        return rawTransactionBuilder.estimateFee(
            rawTransactionBuilder.nftTransfer(nft, recipient, comment)
        )
    }

    suspend fun estimateFee(request: SendRequestEntity): TonFeeEstimate {
        return rawTransactionBuilder.estimateFee(request.transfers)
    }

    suspend fun send(recipient: FriendlyAddress, amount: TonKit.SendAmount, comment: String?) {
        val transfer = rawTransactionBuilder.tonTransfer(recipient, amount, comment)
        api.send(rawTransactionBuilder.signedTransaction(transfer).bocBase64)
    }

    suspend fun send(
        jettonWallet: Address,
        recipient: FriendlyAddress,
        amount: BigInteger,
        comment: String?
    ) {
        val transfer = rawTransactionBuilder.jettonTransfer(jettonWallet, recipient, amount, comment)
        api.send(rawTransactionBuilder.signedTransaction(transfer).bocBase64)
    }

    suspend fun send(
        nft: Address,
        recipient: FriendlyAddress,
        comment: String?,
    ) {
        val transfer = rawTransactionBuilder.nftTransfer(nft, recipient, comment)
        api.send(rawTransactionBuilder.signedTransaction(transfer).bocBase64)
    }

    suspend fun signedTonTransaction(
        recipient: FriendlyAddress,
        amount: TonKit.SendAmount,
        comment: String?,
    ): SignedRawTonTransaction {
        val transfer = rawTransactionBuilder.tonTransfer(recipient, amount, comment)
        return rawTransactionBuilder.signedTransaction(
            transfer = transfer,
            fee = rawTransactionBuilder.estimateFee(transfer).fee,
        )
    }

    suspend fun signedJettonTransaction(
        jettonWallet: Address,
        recipient: FriendlyAddress,
        amount: BigInteger,
        comment: String?,
    ): SignedRawTonTransaction {
        val transfer = rawTransactionBuilder.jettonTransfer(jettonWallet, recipient, amount, comment)
        return rawTransactionBuilder.signedTransaction(
            transfer = transfer,
            fee = rawTransactionBuilder.estimateFee(transfer).fee,
        )
    }

    /**
     * Builds and signs a TON transfer fully offline: no network call is made.
     *
     * [seqno] and [validUntil] must come from an anchor captured while online.
     * [fee] is display-only — it is echoed into the result and is not part of
     * the signed message.
     *
     * Offline signing is forbidden for an undeployed wallet (seqno == 0): the
     * v4R2 and W5 deploy transactions carry 32 one-bits instead of validUntil,
     * so the signed message would never expire while the result reported the
     * requested validUntil. Send the first transaction online.
     */
    suspend fun signedTonTransaction(
        recipient: FriendlyAddress,
        amount: TonKit.SendAmount,
        comment: String?,
        seqno: Int,
        validUntil: Long,
        fee: BigInteger,
    ): SignedRawTonTransaction {
        require(seqno > 0) { "Offline signing requires a deployed wallet (seqno > 0)" }
        val transfer = rawTransactionBuilder.tonTransfer(recipient, amount, comment, seqno, validUntil)
        return rawTransactionBuilder.signedTransaction(transfer, fee)
    }

    /**
     * Builds and signs a jetton transfer fully offline: no network call is made.
     * See the TON overload above for the [seqno]/[validUntil]/[fee] contract.
     */
    suspend fun signedJettonTransaction(
        jettonWallet: Address,
        recipient: FriendlyAddress,
        amount: BigInteger,
        comment: String?,
        seqno: Int,
        validUntil: Long,
        fee: BigInteger,
    ): SignedRawTonTransaction {
        require(seqno > 0) { "Offline signing requires a deployed wallet (seqno > 0)" }
        val transfer =
            rawTransactionBuilder.jettonTransfer(jettonWallet, recipient, amount, comment, seqno, validUntil)
        return rawTransactionBuilder.signedTransaction(transfer, fee)
    }

    suspend fun getAccountSeqno(): Int = api.getAccountSeqno(rawTransactionBuilder.sender)

    suspend fun getRawTime(): Int = api.getRawTime()

    suspend fun send(boc: String) {
        api.send(boc)
    }
}
