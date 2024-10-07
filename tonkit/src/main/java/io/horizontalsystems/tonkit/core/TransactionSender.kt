package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.core.entities.TransferEntity
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.FriendlyAddress
import io.horizontalsystems.tonkit.api.TonApi
import org.ton.api.pk.PrivateKeyEd25519
import java.math.BigInteger

class TransactionSender(
    private val api: TonApi,
    private val sender: Address,
    private val privateKey: PrivateKeyEd25519,
) {
    private suspend fun safeTimeout(ttl: Long = 5 * 60) = try {
        val rawTime = api.getRawTime()
        rawTime + ttl
    } catch(e: Throwable) {
        System.currentTimeMillis() / 1000 + ttl
    }

    suspend fun estimateFee(recipient: FriendlyAddress, amount: TonKit.SendAmount, comment: String?): BigInteger {
        val value: BigInteger
        val isMax: Boolean

        when (amount) {
            is TonKit.SendAmount.Amount -> {
                value = amount.value
                isMax = false
            }
            TonKit.SendAmount.Max -> {
                value = BigInteger.ZERO
                isMax = true
            }
        }

        val transfer = getTonTransferEntity(
            value,
            isMax,
            recipient,
            comment,
            sender.toRaw(),
            true
        )
        val message = transfer.toSignedMessage(EmptyPrivateKeyEd25519)

        return api.estimateFee(message.base64())
    }

    private suspend fun getTonTransferEntity(
        value: BigInteger,
        isMax: Boolean,
        recipient: FriendlyAddress,
        comment: String?,
        walletAddress: String,
        isTon: Boolean,
    ): TransferEntity {
        val seqno = api.getAccountSeqno(sender)
        val timeout = safeTimeout()

        val walletEntity = WalletEntity(
            id = "id",
            publicKey = privateKey.publicKey(),
            type = Wallet.Type.Default,
            version = WalletVersion.V4R2,
            label = Wallet.Label("", "", 0),
            ledger = null
        )

        // Using Coins.DEFAULT_DECIMALS for jetton instead of its own decimals is ok.
        // At the end amount will be converted to long using the same Coins.DEFAULT_DECIMALS
        val transfer = TransferEntity.Builder(walletEntity)
            .setSeqno(seqno)
            .setAmount(Coins.of(value.toBigDecimal(Coins.DEFAULT_DECIMALS)))
            .setMax(isMax)
            .setDestination(recipient.addrStd)
            .setBounceable(recipient.isBounceable)
            .setComment(comment)
            .setValidUntil(timeout)
            .setToken(BalanceEntity(isTon, walletAddress))
            .build()
        return transfer
    }

    suspend fun estimateFee(jettonWallet: Address, recipient: FriendlyAddress, amount: BigInteger, comment: String?): BigInteger {
        val transfer = getTonTransferEntity(
            amount,
            false,
            recipient,
            comment,
            jettonWallet.toRaw(),
            false
        )
        val message = transfer.toSignedMessage(EmptyPrivateKeyEd25519)

        return api.estimateFee(message.base64())

    }

    suspend fun send(recipient: FriendlyAddress, amount: TonKit.SendAmount, comment: String?) {
        val value: BigInteger
        val isMax: Boolean

        when (amount) {
            is TonKit.SendAmount.Amount -> {
                value = amount.value
                isMax = false
            }
            TonKit.SendAmount.Max -> {
                value = BigInteger.ZERO
                isMax = true
            }
        }

        val transfer = getTonTransferEntity(
            value,
            isMax,
            recipient,
            comment,
            sender.toRaw(),
            true
        )
        val message = transfer.toSignedMessage(privateKey)

        api.send(message.base64())
    }

    suspend fun send(jettonWallet: Address, recipient: FriendlyAddress, amount: BigInteger, comment: String?) {
        val transfer = getTonTransferEntity(
            amount,
            false,
            recipient,
            comment,
            jettonWallet.toRaw(),
            false
        )
        val message = transfer.toSignedMessage(privateKey)

        api.send(message.base64())
    }

    suspend fun send(boc: String) {
        api.send(boc)

    }
}
