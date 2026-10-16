package io.horizontalsystems.tonkit.sample

import android.app.Application
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.tonconnect.TonConnectKit
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        initTonKit()
    }

    private fun initTonKit() = runBlocking {
        val walletId = "wallet-${tonWallet.javaClass.simpleName}"
//        val walletId = UUID.randomUUID().toString()

        val network = Network.MainNet
        // Demo keys only: a real wallet keeps a random key in secure storage.
        val databaseKey = sha256(walletId)
        TonKit.migrateDatabase(this@App, network, walletId, databaseKey)
        tonKit = TonKit.getInstance(
            tonWallet,
            network,
            this@App,
            walletId,
            databaseKey
        )

        val tonConnectDatabaseKey = sha256("ton-connect-demo")
        TonConnectKit.migrateDatabase(this@App, tonConnectDatabaseKey)
        tonConnectKit = TonConnectKit.getInstance(this@App, tonConnectDatabaseKey, "Unstoppable Wallet", "0.41.0")
    }

    private fun sha256(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())

    companion object {
        //        val tonWallet: TonWallet = TonWallet.WatchOnly("EQDfvVvoSX_cDJ_L38Z2hkhA3fitZCPW1WV9mw6CcNbIrH-Q")
        val words = BuildConfig.WORDS.split(" ")
//        val words =
//            "used ugly meat glad balance divorce inner artwork hire invest already piano".split(" ")
        val tonWallet = TonWallet.Mnemonic(words, "")

        lateinit var tonKit: TonKit
        lateinit var tonConnectKit: TonConnectKit
    }
}
