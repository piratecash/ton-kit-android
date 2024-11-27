package io.horizontalsystems.tonkit.sample

import android.app.Application
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.tonconnect.TonConnectKit

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        initTonKit()
    }

    private fun initTonKit() {
        val walletId = "wallet-${tonWallet.javaClass.simpleName}"
//        val walletId = UUID.randomUUID().toString()

        val network = Network.MainNet
        tonKit = TonKit.getInstance(
            tonWallet,
            network,
            this,
            walletId
        )

        tonConnectKit = TonConnectKit.getInstance(this, "Unstoppable Wallet", "0.41.0")
    }

    companion object {
        val tonWallet: TonWallet = TonWallet.WatchOnly("EQDfvVvoSX_cDJ_L38Z2hkhA3fitZCPW1WV9mw6CcNbIrH-Q")
//        val words =
//            "used ugly meat glad balance divorce inner artwork hire invest already piano".split(" ")
//        val tonWallet = TonWallet.Mnemonic(words, "")

        lateinit var tonKit: TonKit
        lateinit var tonConnectKit: TonConnectKit
    }
}
