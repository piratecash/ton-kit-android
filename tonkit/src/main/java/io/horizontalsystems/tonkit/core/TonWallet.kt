package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.contract.WalletV4R2Contract
import io.horizontalsystems.tonkit.Address
import org.ton.api.pk.PrivateKeyEd25519

sealed interface TonWallet {
    val address: Address

    data class WatchOnly(val addressStr: String) : TonWallet {
        override val address = Address.parse(this.addressStr)
    }

    open class FullAccess(val privateKey: PrivateKeyEd25519) : TonWallet {
        override val address: Address by lazy {
            val walletV4R2Contract = WalletV4R2Contract(publicKey = privateKey.publicKey())
            Address(walletV4R2Contract.address)
        }
    }

    data class Seed(val seed: ByteArray) : FullAccess(PrivateKeyEd25519(seed))
    data class Mnemonic(val words: List<String>, val passphrase: String = "") : FullAccess(
        PrivateKeyEd25519(
            org.ton.mnemonic.Mnemonic.toSeed(words, passphrase)
        )
    )
}