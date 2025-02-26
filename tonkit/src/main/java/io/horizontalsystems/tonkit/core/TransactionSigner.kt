package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.MessageBodyEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import io.horizontalsystems.tonkit.api.TonApi
import io.horizontalsystems.tonkit.models.Event
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import kotlin.math.min

class TransactionSigner(private val api: TonApi) {

    suspend fun getDetails(request: SendRequestEntity, tonWallet: TonWallet.FullAccess): Event {
        val publicKey = tonWallet.privateKey.publicKey()
        val walletEntity = WalletEntity(
            id = "id",
            publicKey = publicKey,
            type = Wallet.Type.Default,
            version = WalletVersion.V4R2,
            label = Wallet.Label("", "", 0)
        )

        return emulate(request, walletEntity)
    }

    suspend fun sign(request: SendRequestEntity, tonWallet: TonWallet.FullAccess): String {
        val privateKey = tonWallet.privateKey
        val walletEntity = WalletEntity(
            id = "id",
            publicKey = privateKey.publicKey(),
            type = Wallet.Type.Default,
            version = WalletVersion.V4R2,
            label = Wallet.Label("", "", 0)
        )

        val seqno = api.getAccountSeqno(walletEntity.accountId)
        val message = createSignedMessage(
            walletEntity,
            seqno,
            privateKey,
            getSafeValidUntil(request.validUntil),
            request.transfers
        )
        return message.base64()
    }

    private suspend fun emulate(request: SendRequestEntity, wallet: WalletEntity): Event {
        val seqno = api.getAccountSeqno(wallet.accountId)
        val cell = createSignedMessage(
            wallet,
            seqno,
            EmptyPrivateKeyEd25519,
            getSafeValidUntil(request.validUntil),
            request.transfers
        )

        val emulated = api.emulate(cell, wallet.testnet)
        return Event.fromApi(emulated.event)
    }

    private fun createSignedMessage(
        wallet: WalletEntity,
        seqno: Int,
        privateKeyEd25519: PrivateKeyEd25519,
        validUntil: Long,
        transfers: List<WalletTransfer>,
    ): Cell {
        val data = messageBody(wallet, seqno, validUntil, transfers)
        return wallet.sign(privateKeyEd25519, data.seqno, data.body)
    }

    private fun messageBody(
        wallet: WalletEntity,
        seqno: Int,
        validUntil: Long,
        transfers: List<WalletTransfer>,
    ): MessageBodyEntity {
        val body = wallet.createBody(seqno, validUntil, transfers)
        return MessageBodyEntity(seqno, body, validUntil)
    }

    private suspend fun TransactionSigner.getSafeValidUntil(validUntil: Long): Long {
        return min(safeTimeout(), validUntil)
    }

    private suspend fun safeTimeout(ttl: Long = 5 * 60) = try {
        val rawTime = api.getRawTime()
        rawTime + ttl
    } catch(e: Throwable) {
        System.currentTimeMillis() / 1000 + ttl
    }

}
