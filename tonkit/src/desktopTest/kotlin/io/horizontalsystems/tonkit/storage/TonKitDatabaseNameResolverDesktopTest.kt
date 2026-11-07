package io.horizontalsystems.tonkit.storage

import io.horizontalsystems.databasenaming.DatabaseNameResolver
import io.horizontalsystems.tonkit.PlatformContext
import io.horizontalsystems.tonkit.TonV2Fixture
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tonkit.models.Network
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import java.io.File

// Android keeps the default names forever: a change here makes every existing wallet resync from scratch.
class TonKitDatabaseNameResolverDesktopTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    private val key = ByteArray(32) { it.toByte() }
    private val directory: File get() = tmp.root
    private val context: PlatformContext get() = PlatformContext(directory)
    private val opaque = DatabaseNameResolver { "n${it.hashCode().toUInt()}" }

    @Test
    fun getInstance_defaultResolver_opensLiteralName() = runBlocking<Unit> {
        kit(WALLET, Network.MainNet)

        assertTrue(File(directory, "w1-MainNet").isFile)
    }

    @Test
    fun getInstance_differentNetworksAndWallets_namesArePairwiseDistinct() = runBlocking<Unit> {
        listOf(WALLET to Network.MainNet, WALLET to Network.TestNet, "w2" to Network.MainNet).forEach { (wallet, network) ->
            kit(wallet, network)
            kit(wallet, network, opaque)
        }

        val defaults = listOf("w1-MainNet", "w1-TestNet", "w2-MainNet")
        assertEquals((defaults + defaults.map(opaque::resolve)).toSet(), databaseFiles())
    }

    @Test
    fun getInstance_overrideResolver_leavesNoDefaultNameOnDisk() = runBlocking<Unit> {
        kit(WALLET, Network.MainNet, opaque)

        assertEquals(setOf(opaque.resolve("w1-MainNet")), databaseFiles())
    }

    @Test
    fun migrateDatabase_overrideResolver_namespaceFilesUseResolvedName() = runBlocking<Unit> {
        TonKit.migrateDatabase(context, Network.MainNet, WALLET, key, opaque)

        assertEquals(listOf(".${opaque.resolve("ton-kit")}-sqlcipher.lock"), directory.list().orEmpty().toList())
    }

    @Test
    fun clear_defaultResolver_removesLiteralName() = runBlocking {
        kit(WALLET, Network.MainNet)

        TonKit.clear(context, Network.MainNet, WALLET)

        assertFalse(File(directory, "w1-MainNet").exists())
    }

    @Test
    fun clear_overrideResolver_removesResolvedNameAndKeepsDefaultOne() = runBlocking {
        kit(WALLET, Network.MainNet, opaque)
        val foreign = File(directory, "w1-MainNet").apply { writeText("x") }

        TonKit.clear(context, Network.MainNet, WALLET, opaque)

        assertEquals(setOf(foreign.name), databaseFiles())
    }

    @Test
    fun clear_resolvedNameInResolvedNamespacePrefix_throws() {
        val reserved = DatabaseNameResolver { if (it == "ton-kit") "ns" else ".ns-sqlcipher-x" }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { TonKit.clear(context, Network.MainNet, WALLET, reserved) }
        }
    }

    private suspend fun kit(
        walletId: String,
        network: Network,
        nameResolver: DatabaseNameResolver = DatabaseNameResolver.Identity,
    ): TonKit = TonKit.getInstance(
        tonWallet = TonWallet.WatchOnly(TonV2Fixture.ownerAddress.toRaw()),
        network = network,
        context = context,
        walletId = walletId,
        databaseKey = key,
        httpClient = TonKit.createHttpClient(),
        nameResolver = nameResolver,
    )

    private fun databaseFiles(): Set<String> = directory.list().orEmpty()
        .filterNot { it.startsWith(".") || it.endsWith(".lck") || it.endsWith("-wal") || it.endsWith("-shm") }.toSet()

    private companion object {
        const val WALLET = "w1"
    }
}
