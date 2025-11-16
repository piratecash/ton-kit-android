package com.tonapps.blockchain.ton.extensions

import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.toHexString
import org.ton.kotlin.crypto.PublicKeyEd25519

fun String.publicKey(): PublicKeyEd25519 {
    return try {
        PublicKeyEd25519(ByteString(base64()))
    } catch (e: Throwable) {
        PublicKeyEd25519(ByteString(hex(this)))
    }
}

fun String.safePublicKey(): PublicKeyEd25519? {
    return try {
        publicKey()
    } catch (e: Throwable) {
        null
    }
}

fun PublicKeyEd25519.base64(): String {
    return base64(key.toByteArray())
}

@OptIn(ExperimentalStdlibApi::class)
fun PublicKeyEd25519.hex(): String {
    return key.toHexString()
}