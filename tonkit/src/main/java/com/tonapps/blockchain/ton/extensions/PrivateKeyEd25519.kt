package com.tonapps.blockchain.ton.extensions

import org.ton.bitstring.BitString
import org.ton.kotlin.crypto.PrivateKeyEd25519

// EmptyPrivateKeyEd25519 for emulation - creates a dummy key with all zeros
val EmptyPrivateKeyEd25519: PrivateKeyEd25519 = PrivateKeyEd25519(ByteArray(32))

fun PrivateKeyEd25519.sign(data: ByteArray): ByteArray = signToByteArray(data, 0, data.size)

fun PrivateKeyEd25519.sign(bits: BitString): ByteArray = sign(bits.toByteArray())
