package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.contract.HashSigner
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.FriendlyAddress
import io.horizontalsystems.tonkit.api.TonApi
import io.horizontalsystems.tonkit.models.SignedRawTonTransaction
import org.ton.kotlin.crypto.PublicKeyEd25519
import java.math.BigInteger

class TransactionSender(
    private val api: TonApi,
    private val sender: Address,
    hashSigner: HashSigner,
    publicKeyEd25519: PublicKeyEd25519
) {
    private val rawTransactionBuilder = TonRawTransactionBuilder(api, sender, hashSigner, publicKeyEd25519)

    suspend fun estimateFee(
        recipient: FriendlyAddress,
        amount: TonKit.SendAmount,
        comment: String?
    ): BigInteger {
        return rawTransactionBuilder.estimateFee(
            rawTransactionBuilder.tonTransfer(recipient, amount, comment)
        )
    }

    suspend fun estimateFee(
        jettonWallet: Address,
        recipient: FriendlyAddress,
        amount: BigInteger,
        comment: String?
    ): BigInteger {
        return rawTransactionBuilder.estimateFee(
            rawTransactionBuilder.jettonTransfer(jettonWallet, recipient, amount, comment)
        )
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

    suspend fun signedTonTransaction(
        recipient: FriendlyAddress,
        amount: TonKit.SendAmount,
        comment: String?,
    ): SignedRawTonTransaction {
        val transfer = rawTransactionBuilder.tonTransfer(recipient, amount, comment)
        return rawTransactionBuilder.signedTransaction(
            transfer = transfer,
            fee = rawTransactionBuilder.estimateFee(transfer),
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
            fee = rawTransactionBuilder.estimateFee(transfer),
        )
    }

    suspend fun send(boc: String) {
        api.send(boc)
    }
}
