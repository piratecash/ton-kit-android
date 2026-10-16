package io.horizontalsystems.tonkit

import co.touchlab.kermit.Logger
import io.horizontalsystems.tonkit.core.AccountManager
import io.horizontalsystems.tonkit.core.EventManager
import io.horizontalsystems.tonkit.core.JettonManager
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.JettonBalance
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.models.SyncState
import io.horizontalsystems.tonkit.models.Tag
import io.horizontalsystems.tonkit.models.TagQuery
import io.horizontalsystems.tonkit.storage.KitDatabase
import io.horizontalsystems.tonkit.tonconnect.DAppManager
import io.horizontalsystems.tonkit.tonconnect.LocalStorage
import io.horizontalsystems.tonkit.tonconnect.TonConnectKit
import io.horizontalsystems.tonkit.tonconnect.TonConnectKitDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TonKitDesktopSmokeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context by lazy { PlatformContext(tmp.root) }
    private val logger = Logger.withTag("TonKitDesktopSmokeTest")
    private val owner = TonV2Fixture.ownerAddress
    private val event = TonV2Fixture.event
    private val jettonAddress = TonV2Fixture.jettonBalances.first().jettonAddress

    @Test
    fun getInstance_emptyDatabase_startsWithoutStoredState() = runBlocking {
        val kit = kit()

        assertNull(kit.account)
        assertEquals(emptyMap<Address, JettonBalance>(), kit.jettonBalanceMap)
        assertEquals(emptyList<Event>(), kit.events(TagQuery(null, null, null, null)))

        kit.stop()
    }

    @Test
    fun getInstance_afterManagersSync_loadsStateAndFiltersEventsByTags() = runBlocking {
        val database = KitDatabase.getInstance(context, "$WALLET_ID-${Network.MainNet.name}", KEY)
        try {
            val api = FixtureApi(TonV2Fixture.jettonBalances)
            AccountManager(owner, api, database.accountDao(), null, logger).sync()
            val jettonManager = JettonManager(owner, api, database.jettonDao(), emptyList(), logger)
            jettonManager.sync()
            api.jettonBalances = TonV2Fixture.jettonBalances.take(1)
            jettonManager.sync()
            val eventManager = EventManager(owner, api, database.eventDao(), logger)
            eventManager.sync()
            assertEquals(SyncState.Synced, eventManager.syncStateFlow.value)
        } finally {
            database.close()
        }

        val kit = kit()

        assertEquals(TonV2Fixture.account, kit.account)
        assertEquals(TonV2Fixture.jettonBalances.take(1).associateBy { it.jettonAddress }, kit.jettonBalanceMap)
        assertEquals(listOf(event), kit.events(TagQuery(Tag.Type.Incoming, Tag.Platform.Jetton, jettonAddress, null)))
        assertEquals(listOf(event), kit.events(TagQuery(Tag.Type.Outgoing, Tag.Platform.Native, null, null)))
        assertEquals(emptyList<Event>(), kit.events(TagQuery(Tag.Type.Swap, null, null, null)))
        assertEquals(emptyList<Event>(), kit.events(TagQuery(null, null, null, null), beforeLt = event.lt))
        assertEquals(listOf(jettonAddress), kit.tagTokens().mapNotNull { it.jettonAddress })

        kit.stop()
    }

    @Test
    fun getInstance_storedDApp_emitsItFromDatabase() = runBlocking {
        val dApp = TonV2Fixture.dApps().first()
        val database = TonConnectKitDatabase.getInstance(context, TON_CONNECT_DATABASE, KEY)
        try {
            DAppManager(database.dAppDao()).addApp(dApp)
            val localStorage = LocalStorage(database.keyValueDao())
            localStorage.setLastSSEventId(TonV2Fixture.LAST_SSE_EVENT_ID)
            assertEquals(TonV2Fixture.LAST_SSE_EVENT_ID, localStorage.getLastSSEventId())
        } finally {
            database.close()
        }

        val kit = TonConnectKit.getInstance(context, KEY, "Desktop smoke", "0")

        val dApps = kit.getDApps().first()
        assertEquals(listOf(dApp.uniqueId), dApps.map { it.uniqueId })
        assertTrue(dApps.single().keyPair.privateKey.contentEquals(dApp.keyPair.privateKey))
    }

    private suspend fun kit() = TonKit.getInstance(
        tonWallet = TonWallet.WatchOnly(owner.toRaw()),
        network = Network.MainNet,
        context = context,
        walletId = WALLET_ID,
        databaseKey = KEY,
    )

    private companion object {
        const val WALLET_ID = "desktop-smoke"
        const val TON_CONNECT_DATABASE = "ton-connect"
        val KEY = ByteArray(32) { it.toByte() }
    }
}
