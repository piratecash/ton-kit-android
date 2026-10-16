package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.contract.HashSigner
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.toByteArray
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import kotlinx.io.bytestring.ByteString
import org.ton.block.Message
import org.ton.bitstring.BitString
import org.ton.cell.Cell
import org.ton.kotlin.crypto.PublicKeyEd25519

internal object RawMessageTestHelper {
    private val hashSigner = object : HashSigner {
        override fun sign(hash: BitString): BitString {
            return BitString(ByteArray(64) { 1 })
        }
    }
    private val publicKey = PublicKeyEd25519(ByteString(*ByteArray(32) { (it + 1).toByte() }))
    private val wallet = WalletEntity(
        id = "id",
        publicKey = publicKey,
        type = Wallet.Type.Default,
        version = WalletVersion.V4R2,
        label = Wallet.Label("", "", 0),
        hashSigner = hashSigner,
        ledger = null,
    )
    private val contract = wallet.contract

    val senderAddress: String = contract.address.toString(userFriendly = false).lowercase()

    fun messageObject(seqno: Int = 1): Message<Cell> {
        return contract.createTransferMessageFromUnsignedBody(
            address = contract.address,
            seqno = seqno,
            unsignedBody = Cell.empty(),
            useEmptySigner = false,
        )
    }

    fun message(seqno: Int = 1): Cell {
        return contract.createTransferMessageCellFromUnsignedBody(
            address = contract.address,
            seqno = seqno,
            unsignedBody = Cell.empty(),
            useEmptySigner = false,
        )
    }

    fun rawMessage(seqno: Int = 1): ByteArray {
        return message(seqno).toByteArray()
    }
}
