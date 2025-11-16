package com.tonapps.blockchain.ton.contract

import com.tonapps.blockchain.ton.contract.w5.W5Context
import com.tonapps.blockchain.ton.contract.w5.WalletV5BetaContract
import com.tonapps.blockchain.ton.contract.w5.WalletV5R1Contract
import com.tonapps.blockchain.ton.extensions.sign
import org.ton.bitstring.BitString
import org.ton.block.AddrNone
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.Either
import org.ton.block.ExtInMsgInfo
import org.ton.block.Maybe
import org.ton.block.Message
import org.ton.block.MessageRelaxed
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.buildCell
import org.ton.contract.wallet.WalletTransfer
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.kotlin.crypto.PublicKeyEd25519
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeTlb
import java.math.BigInteger

enum class MessageType {
    Extension,
    Internal,
}

enum class SignaturePosition {
    Front,
    Tail,
}

abstract class BaseWalletContract(
    val workchain: Int = DEFAULT_WORKCHAIN,
    val publicKey: PublicKeyEd25519
) {

    companion object {
        const val DEFAULT_WORKCHAIN = 0
        const val DEFAULT_WALLET_ID: Int = 698983191

        fun create(
            publicKey: PublicKeyEd25519,
            v: String,
            networkGlobalId: Int
        ): BaseWalletContract {
            return when (v.lowercase()) {
                "v3r1" -> WalletV3R1Contract(publicKey = publicKey)
                "v3r2" -> WalletV3R2Contract(publicKey = publicKey)
                "v4r1" -> WalletV4R1Contract(publicKey = publicKey)
                "v4r2" -> WalletV4R2Contract(publicKey = publicKey)
                "v5beta" -> WalletV5BetaContract(
                    publicKey = publicKey,
                    networkGlobalId = networkGlobalId
                )

                "v5r1" -> WalletV5R1Contract(
                    publicKey = publicKey, context = W5Context.Client(
                        networkGlobalId = networkGlobalId,
                    )
                )

                else -> throw IllegalArgumentException("Unsupported contract version: $v")
            }
        }

        fun create(publicKey: PublicKeyEd25519, v: String, testnet: Boolean): BaseWalletContract {
            return create(publicKey, v, if (testnet) -3 else -239)
        }

        fun createIntMsg(gift: WalletTransfer): MessageRelaxed<Cell> = gift.toMessageRelaxed()
    }

    val walletId = DEFAULT_WALLET_ID + workchain

    val stateInit: StateInit by lazy {
        val cell = getStateCell()
        val code = getCode()
        StateInit(code, cell)
    }

    val address: AddrStd by lazy { stateInit.address(workchain) }

    abstract val maxMessages: Int

    abstract fun getStateCell(): Cell

    abstract fun getCode(): Cell

    abstract fun getWalletVersion(): WalletVersion

    abstract fun createTransferUnsignedBody(
        validUntil: Long,
        seqno: Int,
        messageType: MessageType = MessageType.Internal,
        queryId: BigInteger? = null,
        vararg gifts: WalletTransfer
    ): Cell

    abstract fun getSignaturePosition(): SignaturePosition

    private fun signBody(
        privateKey: PrivateKeyEd25519,
        unsignedBody: Cell,
    ): Cell {
        val signature = BitString(privateKey.sign(unsignedBody.hash()))
        return signedBody(signature, unsignedBody)
    }

    fun signedBody(
        signature: BitString,
        unsignedBody: Cell,
    ): Cell {
        return when (getSignaturePosition()) {
            SignaturePosition.Front -> CellBuilder.createCell {
                storeBits(signature)
                storeBits(unsignedBody.bits)
                storeRefs(unsignedBody.refs)
            }

            SignaturePosition.Tail -> CellBuilder.createCell {
                storeBits(unsignedBody.bits)
                storeBits(signature)
                storeRefs(unsignedBody.refs)
            }
        }
    }

    fun createTransferMessageCell(
        address: MsgAddressInt,
        privateKey: PrivateKeyEd25519,
        seqno: Int,
        unsignedBody: Cell
    ): Cell {
        val message = createTransferMessage(address, privateKey, seqno, unsignedBody)

        val cell = buildCell {
            storeTlb(Message.tlbCodec(AnyTlbConstructor), message)
        }
        return cell
    }

    fun createTransferMessage(
        address: MsgAddressInt,
        privateKey: PrivateKeyEd25519,
        seqno: Int,
        unsignedBody: Cell
    ): Message<Cell> {
        val info = ExtInMsgInfo(
            src = AddrNone,
            dest = address,
            importFee = Coins()
        )

        val init = if (seqno == 0) {
            stateInit
        } else null

        val maybeStateInit = Maybe.of(
            init?.let {
                Either.of<StateInit, CellRef<StateInit>>(
                    null,
                    CellRef(value = it, codec = StateInit)
                )
            }
        )

        val transferBody = signBody(privateKey, unsignedBody)

        val body = Either.of<Cell, CellRef<Cell>>(
            null,
            CellRef(cell = transferBody, codec = AnyTlbConstructor)
        )
        return Message(
            info = info,
            init = maybeStateInit,
            body = body
        )
    }

    fun createTransferMessage(
        address: MsgAddressInt,
        seqno: Int,
        transferBody: Cell
    ): Message<Cell> {
        val info = ExtInMsgInfo(
            src = AddrNone,
            dest = address,
            importFee = Coins()
        )

        val init = if (seqno == 0) {
            stateInit
        } else null

        val maybeStateInit = Maybe.of(
            init?.let {
                Either.of<StateInit, CellRef<StateInit>>(
                    null,
                    CellRef(value = it, codec = StateInit)
                )
            }
        )

        val body = Either.of<Cell, CellRef<Cell>>(
            null,
            CellRef(cell = transferBody, codec = AnyTlbConstructor)
        )
        return Message(
            info = info,
            init = maybeStateInit,
            body = body
        )
    }

    fun createTransferMessageCell(
        address: MsgAddressInt,
        seqno: Int,
        transferBody: Cell
    ): Cell {
        val message = createTransferMessage(address, seqno, transferBody)

        val cell = buildCell {
            storeTlb(Message.tlbCodec(AnyTlbConstructor), message)
        }
        return cell
    }
}
