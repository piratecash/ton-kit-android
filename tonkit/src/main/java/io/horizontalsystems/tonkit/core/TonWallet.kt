package io.horizontalsystems.tonkit.core

import com.tonapps.blockchain.ton.contract.HashSigner
import com.tonapps.blockchain.ton.contract.PrivateKeyHashSigner
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.models.Network
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.kotlin.crypto.PublicKeyEd25519
import org.ton.kotlin.crypto.mnemonic.Mnemonic

sealed interface TonWallet {
    /** The W5 address depends on the network global id. */
    fun address(network: Network): Address

    data class WatchOnly(val addressStr: String) : TonWallet {
        private val address = Address.parse(this.addressStr)

        override fun address(network: Network) = address
    }

    open class FullAccess(
        val publicKeyEd25519: PublicKeyEd25519,
        val hashSigner: HashSigner,
        open val version: TonWalletVersion,
    ) : TonWallet {
        constructor(privateKeyEd25519: PrivateKeyEd25519, version: TonWalletVersion) : this(
            publicKeyEd25519 = privateKeyEd25519.publicKey(),
            hashSigner = PrivateKeyHashSigner(privateKeyEd25519),
            version = version,
        )

        internal fun walletEntity(network: Network) = walletEntity(publicKeyEd25519, hashSigner, version, network)

        override fun address(network: Network) = walletEntity(network).senderAddress
    }

    /** Signs on a hardware device through [deviceSigner], never through a [HashSigner]. The device signs v4R2 only. */
    class ExternalSigner(
        val publicKeyEd25519: PublicKeyEd25519,
        val deviceSigner: TonTransactionDeviceSigner,
    ) : TonWallet {
        internal val version = TonWalletVersion.V4R2

        internal fun walletEntity(network: Network) =
            walletEntity(publicKeyEd25519, DeviceOnlyHashSigner, version, network)

        override fun address(network: Network) = walletEntity(network).senderAddress
    }

    data class Seed(val seed: ByteArray, override val version: TonWalletVersion) : FullAccess(
        PrivateKeyEd25519(if (seed.size == 64) seed.copyOfRange(0, 32) else seed),
        version,
    )

    data class Mnemonic(
        val words: List<String>,
        val passphrase: String,
        override val version: TonWalletVersion,
    ) : FullAccess(
        PrivateKeyEd25519(
            Mnemonic(words, passphrase.encodeToByteArray())
                .toSeed()
                .copyOfRange(0, 32)
        ),
        version,
    )
}

internal val WalletEntity.senderAddress: Address
    get() = Address(contract.address)

private fun walletEntity(
    publicKey: PublicKeyEd25519,
    hashSigner: HashSigner,
    version: TonWalletVersion,
    network: Network,
) = WalletEntity(
    id = "id",
    publicKey = publicKey,
    // WalletEntity derives the network global id of the W5 wallet id from the type.
    type = when (network) {
        Network.MainNet -> Wallet.Type.Default
        Network.TestNet -> Wallet.Type.Testnet
    },
    version = when (version) {
        TonWalletVersion.V4R2 -> WalletVersion.V4R2
        TonWalletVersion.W5 -> WalletVersion.V5R1
    },
    label = Wallet.Label("", "", 0),
    hashSigner = hashSigner,
)
