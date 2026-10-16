package com.tonapps.blockchain.ton.contract

import org.ton.bitstring.BitString

interface HashSigner {
    fun sign(hash: BitString): BitString
}