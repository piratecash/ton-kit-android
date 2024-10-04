package io.horizontalsystems.tonkit.sample

import android.app.Application
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonKit.WalletType
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.tonconnect.TonConnectKit

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        initTonKit()
    }

    private fun initTonKit() {
        val walletId = "wallet-${walletType.javaClass.simpleName}"
//        val walletId = UUID.randomUUID().toString()

        tonKit = TonKit.getInstance(
            walletType,
            Network.MainNet,
            this,
            walletId
        )

        tonConnectKit = TonConnectKit.getInstance(this)
    }

    companion object {
        val walletType = WalletType.Watch("EQDfvVvoSX_cDJ_L38Z2hkhA3fitZCPW1WV9mw6CcNbIrH-Q")
//        val words =
//            "used ugly meat glad balance divorce inner artwork hire invest already piano".split(" ")
//        val walletType = WalletType.Mnemonic(words, "")

        lateinit var tonKit: TonKit
        lateinit var tonConnectKit: TonConnectKit
    }
}
