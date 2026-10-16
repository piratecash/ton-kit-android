package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.contract.HashSigner
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.MessageBodyEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import io.horizontalsystems.tonkit.api.TonApi
import io.horizontalsystems.tonkit.models.Event
import org.ton.kotlin.crypto.PublicKeyEd25519
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import kotlin.math.min

class TransactionSigner(private val api: TonApi) {

    suspend fun getDetails(request: SendRequestEntity, tonWallet: TonWallet.FullAccess): Event {
        return emulate(request, walletEntity(tonWallet.publicKeyEd25519, tonWallet.hashSigner))
    }

    suspend fun getDetails(request: SendRequestEntity, tonWallet: TonWallet.ExternalSigner): Event {
        return emulate(request, walletEntity(tonWallet.publicKeyEd25519, DeviceOnlyHashSigner))
    }

    suspend fun sign(request: SendRequestEntity, tonWallet: TonWallet.FullAccess): String {
        val walletEntity = walletEntity(tonWallet.publicKeyEd25519, tonWallet.hashSigner)

        val seqno = api.getAccountSeqno(walletEntity.accountId)
        val message = createSignedMessage(
            walletEntity,
            seqno,
            getSafeValidUntil(request.validUntil),
            request.transfers,
            false
        )
        return message.base64()
    }

    /** Swaps send one message; the device signs no more than that. */
    suspend fun sign(request: SendRequestEntity, tonWallet: TonWallet.ExternalSigner): String {
        val message = request.messages.singleOrNull()
            ?: throw TonExternalSignerException.UnsupportedMessageCount(request.messages.size)
        val walletEntity = walletEntity(tonWallet.publicKeyEd25519, DeviceOnlyHashSigner)
        val seqno = api.getAccountSeqno(walletEntity.accountId)
        val transaction = message.toDeviceTransaction(seqno, getSafeValidUntil(request.validUntil))
        return tonWallet.deviceSigner.signedMessage(walletEntity.contract, transaction).base64()
    }

    private fun walletEntity(publicKey: PublicKeyEd25519, hashSigner: HashSigner) = WalletEntity(
        id = "id",
        publicKey = publicKey,
        type = Wallet.Type.Default,
        version = WalletVersion.V4R2,
        hashSigner = hashSigner,
        label = Wallet.Label("", "", 0)
    )

    private suspend fun emulate(request: SendRequestEntity, wallet: WalletEntity): Event {
        val seqno = api.getAccountSeqno(wallet.accountId)
        val cell = createSignedMessage(
            wallet,
            seqno,
            getSafeValidUntil(request.validUntil),
            request.transfers,
            true
        )

        val emulated = api.emulate(cell, wallet.testnet)
        return Event.fromApi(emulated.event)
    }

    private fun createSignedMessage(
        wallet: WalletEntity,
        seqno: Int,
        validUntil: Long,
        transfers: List<WalletTransfer>,
        useEmptySigner: Boolean
    ): Cell {
        val data = messageBody(wallet, seqno, validUntil, transfers)
        return wallet.sign(data.seqno, data.body, useEmptySigner)
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
        if (validUntil == 0L) {
            return safeTimeout()
        }
        return min(safeTimeout(), validUntil)
    }

    private suspend fun safeTimeout(ttl: Long = 5 * 60) = try {
        val rawTime = api.getRawTime()
        rawTime + ttl
    } catch(e: Throwable) {
        System.currentTimeMillis() / 1000 + ttl
    }

}
