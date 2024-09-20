package io.horizontalsystems.tonkit.sample

import android.app.Application
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonKit.WalletType
import io.horizontalsystems.tonkit.models.Network

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        initTonKit()
    }

    private fun initTonKit() {
        val walletType = WalletType.Watch("UQBpAeJL-VSLCigCsrgGQHCLeiEBdAuZBlbrrUGI4BVQJoPM")
//        val words =
//            "used ugly meat glad balance divorce inner artwork hire invest already piano".split(" ")
//        val walletType = WalletType.Mnemonic(words, "")

        tonKit = TonKit.getInstance(
            walletType,
            Network.MainNet,
            this,
            "wallet-${walletType.javaClass.simpleName}"
        )
    }

    companion object {
        lateinit var tonKit: TonKit
    }
}
