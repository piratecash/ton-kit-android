package com.tonapps.blockchain.ton.contract

import com.tonapps.blockchain.ton.extensions.sign
import org.ton.bitstring.BitString
import org.ton.kotlin.crypto.PrivateKeyEd25519

class PrivateKeyHashSigner(private val privateKey: PrivateKeyEd25519) : HashSigner {
    override fun sign(hash: BitString): BitString {
        return BitString(privateKey.sign(hash))
    }
}