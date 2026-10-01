package com.tonapps.wallet.data.tonconnect.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import com.tonapps.security.CryptoBox
import com.tonapps.security.hex
import com.tonapps.wallet.data.account.entities.ProofDomainEntity

@Entity(primaryKeys = ["walletId", "url"])
data class DAppEntity(
    val url: String,
    val walletId: String,
    val accountId: String,
    val testnet: Boolean,
    val clientId: String,
    @Embedded(prefix = "keypair_")
    val keyPair: CryptoBox.KeyPair,
    val enablePush: Boolean = false,
    @Embedded(prefix = "manifest_")
    val manifest: DAppManifestEntity,
) {

    @Ignore
    val domain = ProofDomainEntity(requireNotNull(url.uriHost) { "dApp url has no host" })

    val publicKeyHex: String
        get() = hex(keyPair.publicKey)

    val uniqueId: String
        get() = "$walletId:$url"

    fun encrypt(body: String): ByteArray {
        return encrypt(body.toByteArray())
    }

    fun encrypt(body: ByteArray): ByteArray {
        return CryptoBox.encrypt(body, clientId.hex(), keyPair.privateKey)
    }

    fun decrypt(body: String): ByteArray {
        return decrypt(body.toByteArray())
    }

    fun decrypt(body: ByteArray): ByteArray {
        return CryptoBox.decrypt(body, clientId.hex(), keyPair.privateKey)
    }

}