package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.core.entities.TransferEntity
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.TokenEntity
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
        val seqno = api.getAccountSeqno(sender)
        val timeout = safeTimeout()

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

        val walletEntity = WalletEntity(
            id = "id",
            publicKey = privateKey.publicKey(),
            type = Wallet.Type.Default,
            version = WalletVersion.V4R2,
            label = Wallet.Label("", "", 0),
            ledger = null
        )
        val transfer = TransferEntity.Builder(walletEntity)
            .setSeqno(seqno)
            .setAmount(Coins.of(value.toBigDecimal(Coins.DEFAULT_DECIMALS)))
            .setMax(isMax)
            .setDestination(recipient.addrStd)
            .setBounceable(recipient.isBounceable)
            .setComment(comment)
            .setValidUntil(timeout)
            .setToken(
                BalanceEntity(
                    TokenEntity.TON,
                    Coins.ZERO,
                    sender.toRaw()
                )
            )
            .build()
        val message = transfer.toSignedMessage(EmptyPrivateKeyEd25519)


        return api.estimateFee(message.base64())
//    }
//
//    func estimateFee(jettonWallet: Address, recipient: FriendlyAddress, amount: BigUInt, comment: String?) async throws -> BigUInt {
//        let seqno = try await api.getAccountSeqno(address: sender)
//        let timeout = await safeTimeout()
//
//        let data = TransferData(
//            contract: contract,
//            sender: sender,
//            seqno: UInt64(seqno),
//            amount: amount,
//            isMax: false,
//            recipient: recipient.address,
//            isBounceable: recipient.isBounceable,
//            comment: comment,
//            timeout: timeout
//        ) { transfer in
//            try transfer.signMessage(signer: WalletTransferEmptyKeySigner())
//        }
//
//        let boc = try JettonTransferBoc(jetton: jettonWallet, transferData: data).create()
//
//        return try await api.estimateFee(boc: boc)
    }

//    func send(recipient: FriendlyAddress, amount: Kit.SendAmount, comment: String?) async throws {
//        let seqno = try await api.getAccountSeqno(address: sender)
//        let timeout = await safeTimeout()
//        let secretKey = secretKey
//
//        let value: BigUInt
//        let isMax: Bool
//
//        switch amount {
//        case let .amount(_value):
//            value = _value
//            isMax = false
//        case .max:
//            value = 0
//            isMax = true
//        }
//
//        let data = TransferData(
//            contract: contract,
//            sender: sender,
//            seqno: UInt64(seqno),
//            amount: value,
//            isMax: isMax,
//            recipient: recipient.address,
//            isBounceable: recipient.isBounceable,
//            comment: comment,
//            timeout: timeout
//        ) { transfer in
//            try transfer.signMessage(signer: WalletTransferSecretKeySigner(secretKey: secretKey))
//        }
//
//        let boc = try TonTransferBoc(transferData: data).create()
//
//        return try await api.send(boc: boc)
//    }
//
//    func send(jettonWallet: Address, recipient: FriendlyAddress, amount: BigUInt, comment: String?) async throws {
//        let seqno = try await api.getAccountSeqno(address: sender)
//        let timeout = await safeTimeout()
//        let secretKey = secretKey
//
//        let data = TransferData(
//            contract: contract,
//            sender: sender,
//            seqno: UInt64(seqno),
//            amount: amount,
//            isMax: false,
//            recipient: recipient.address,
//            isBounceable: recipient.isBounceable,
//            comment: comment,
//            timeout: timeout
//        ) { transfer in
//            try transfer.signMessage(signer: WalletTransferSecretKeySigner(secretKey: secretKey))
//        }
//
//        let boc = try JettonTransferBoc(jetton: jettonWallet, transferData: data).create()
//
//        return try await api.send(boc: boc)
//    }

}
