package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.contract.WalletV4R2Contract
import io.horizontalsystems.tonkit.Address
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.kotlin.crypto.Signer
import org.ton.kotlin.crypto.mnemonic.Mnemonic

sealed interface TonWallet {
    val address: Address

    data class WatchOnly(val addressStr: String) : TonWallet {
        override val address = Address.parse(this.addressStr)
    }

    open class FullAccess(val privateKey: PrivateKeyEd25519, signer: Signer? = null) : TonWallet {
        override val address: Address by lazy {
            val walletV4R2Contract = WalletV4R2Contract(publicKey = privateKey.publicKey(), signer = signer)
            Address(walletV4R2Contract.address)
        }
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
