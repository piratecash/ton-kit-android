package com.tonapps.wallet.data.tonconnect.entities

import android.net.Uri
import android.os.Parcelable
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import com.tonapps.security.CryptoBox
import com.tonapps.security.Sodium
import com.tonapps.security.hex
import com.tonapps.wallet.data.account.entities.ProofDomainEntity
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
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
): Parcelable {

    @Ignore
    @IgnoredOnParcel
    val uri: Uri = Uri.parse(url)

    @Ignore
    @IgnoredOnParcel
    val domain = ProofDomainEntity(uri.host!!)

    @IgnoredOnParcel
    val publicKeyHex: String
        get() = hex(keyPair.publicKey)

    val uniqueId: String
        get() = "$walletId:$url"

    fun encrypt(body: String): ByteArray {
        return encrypt(body.toByteArray())
    }

    fun encrypt(body: ByteArray): ByteArray {
        val nonce = CryptoBox.nonce()
        val cipher = ByteArray(body.size + Sodium.cryptoBoxMacBytes())
        Sodium.cryptoBoxEasy(cipher, body, body.size, nonce, clientId.hex(), keyPair.privateKey)
        return nonce + cipher
    }

    fun decrypt(body: String): ByteArray {
        return decrypt(body.toByteArray())
    }

    fun decrypt(body: ByteArray): ByteArray {
        val nonce = body.sliceArray(0 until Sodium.cryptoBoxNonceBytes())
        val cipher = body.sliceArray(Sodium.cryptoBoxNonceBytes() until body.size)
        val message = ByteArray(cipher.size - Sodium.cryptoBoxMacBytes())
        Sodium.cryptoBoxOpenEasy(message, cipher, cipher.size, nonce, clientId.hex(), keyPair.privateKey)
        return message
    }

}