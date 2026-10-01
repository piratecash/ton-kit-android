package com.tonapps.blockchain.ton.contract

import org.ton.kotlin.crypto.PublicKeyEd25519
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import com.tonapps.blockchain.ton.extensions.base64

class WalletV3R2Contract(
    workchain: Int = DEFAULT_WORKCHAIN,
    publicKey: PublicKeyEd25519,
    override val hashSigner: HashSigner
) : WalletV3R1Contract(workchain, publicKey, hashSigner) {

    override fun getWalletVersion() = WalletVersion.V3R2

    override fun getSignaturePosition(): SignaturePosition {
        return SignaturePosition.Front
    }

    override fun getCode(): Cell {
        return CODE
    }

    companion object {
        @JvmField
        val CODE =
            BagOfCells(base64("te6cckEBAQEAcQAA3v8AIN0gggFMl7ohggEznLqxn3Gw7UTQ0x/THzHXC//jBOCk8mCDCNcYINMf0x/TH/gjE7vyY+1E0NMf0x/T/9FRMrryoVFEuvKiBPkBVBBV+RDyo/gAkyDXSpbTB9QC+wDo0QGkyMsfyx/L/8ntVBC9ba0=")).first()

    }
}
