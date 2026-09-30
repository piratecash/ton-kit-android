package com.tonapps.security

import io.horizontalsystems.tonkit.tweetnacl.TweetNaclFast.Box

object CryptoBox {

    data class KeyPair(
        val publicKey: ByteArray,
        val privateKey: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as KeyPair

            if (!publicKey.contentEquals(other.publicKey)) return false
            if (!privateKey.contentEquals(other.privateKey)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = publicKey.contentHashCode()
            result = 31 * result + privateKey.contentHashCode()
            return result
        }
    }

    fun keyPair(): KeyPair {
        val keyPair = Box.keyPair()
        return KeyPair(keyPair.publicKey, keyPair.secretKey)
    }

    /** Returns nonce ‖ MAC ‖ ciphertext, the TON Connect bridge layout of `crypto_box_easy`. */
    fun encrypt(message: ByteArray, remotePublicKey: ByteArray, localPrivateKey: ByteArray): ByteArray {
        val nonce = Security.randomBytes(Box.nonceLength)
        return nonce + Box(remotePublicKey, localPrivateKey).box(message, nonce)
    }

    fun decrypt(body: ByteArray, remotePublicKey: ByteArray, localPrivateKey: ByteArray): ByteArray {
        val nonce = body.sliceArray(0 until Box.nonceLength)
        val cipher = body.sliceArray(Box.nonceLength until body.size)
        // On auth failure the libsodium binding returned its untouched zeroed buffer; keep that.
        return Box(remotePublicKey, localPrivateKey).open(cipher, nonce)
            ?: ByteArray(cipher.size - Box.overheadLength)
    }
}
