package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.contract.HashSigner
import com.tonapps.blockchain.ton.contract.PrivateKeyHashSigner
import com.tonapps.blockchain.ton.contract.WalletV4R2Contract
import io.horizontalsystems.tonkit.Address
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.kotlin.crypto.PublicKeyEd25519
import org.ton.kotlin.crypto.mnemonic.Mnemonic

sealed interface TonWallet {
    val address: Address

    data class WatchOnly(val addressStr: String) : TonWallet {
        override val address = Address.parse(this.addressStr)
    }

    open class FullAccess(
        val publicKeyEd25519: PublicKeyEd25519,
        val hashSigner: HashSigner
    ) : TonWallet {
        constructor(privateKeyEd25519: PrivateKeyEd25519) : this(
            publicKeyEd25519 = privateKeyEd25519.publicKey(),
            hashSigner = PrivateKeyHashSigner(privateKeyEd25519)
        )

        override val address: Address by lazy { v4R2Address(publicKeyEd25519, hashSigner) }
    }

    /** Signs on a hardware device through [deviceSigner], never through a [HashSigner]. */
    class ExternalSigner(
        val publicKeyEd25519: PublicKeyEd25519,
        val deviceSigner: TonTransactionDeviceSigner,
    ) : TonWallet {
        override val address: Address by lazy { v4R2Address(publicKeyEd25519, DeviceOnlyHashSigner) }
    }

    data class Seed(val seed: ByteArray) : FullAccess(
        PrivateKeyEd25519(if (seed.size == 64) seed.copyOfRange(0, 32) else seed)
    )

    data class Mnemonic(val words: List<String>, val passphrase: String = "") : FullAccess(
        PrivateKeyEd25519(
            Mnemonic(words, passphrase.encodeToByteArray())
                .toSeed()
                .copyOfRange(0, 32)
        )
    )
}

private fun v4R2Address(publicKey: PublicKeyEd25519, hashSigner: HashSigner): Address =
    Address(WalletV4R2Contract(publicKey = publicKey, hashSigner = hashSigner).address)
