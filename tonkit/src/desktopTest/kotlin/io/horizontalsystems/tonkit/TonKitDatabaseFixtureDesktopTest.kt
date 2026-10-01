package io.horizontalsystems.tonkit

import io.horizontalsystems.sqlcipher.room.DatabaseMigrationResult
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.storage.KitDatabase
import io.horizontalsystems.tonkit.tonconnect.TonConnectKit
import io.horizontalsystems.tonkit.tonconnect.TonConnectKitDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import java.io.File

/** The Room 2.6.1 plaintext fixtures survive the SQLCipher migration with every stored value intact. */
class TonKitDatabaseFixtureDesktopTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    private val key = ByteArray(32) { (it * 7).toByte() }
    private val context: PlatformContext get() = PlatformContext(tmp.root)

    @Test
    fun migrateDatabase_kitVersion2Fixture_keepsEveryRowAndOpensUnchanged() = runBlocking {
        val file = TonV2Fixture.copyTo(TonV2Fixture.KIT_V2_RESOURCE, File(tmp.root, KIT_DB_NAME))
        val plaintext = plaintextTables(file)

        val result = TonKit.migrateDatabase(context, Network.MainNet, WALLET_ID, key)

        assertEquals(DatabaseMigrationResult(1, 0), result)
        assertFalse(hasPlaintextSqliteHeader(file))
        assertEquals(2, plaintext.getValue("RawMessageBroadcastRecord").size)
        assertEquals(plaintext, encryptedTables(file, key))

        val kit = kit()
        assertEquals(TonV2Fixture.account, kit.account)
        assertEquals(TonV2Fixture.jettonBalances.associateBy { it.jettonAddress }, kit.jettonBalanceMap)
        withKitDatabase { database -> TonV2Fixture.assertKitContents(database) }
        // An unchanged version proves neither a migration nor a destructive fallback ran after the transfer.
        assertEquals(2, encryptedUserVersion(file, key))
    }

    @Test
    fun migrateDatabase_kitVersion1Fixture_appliesMigration1To2AfterEncryption() = runBlocking {
        val file = TonV2Fixture.copyTo(TonV2Fixture.KIT_V1_RESOURCE, File(tmp.root, KIT_DB_NAME))
        val plaintext = plaintextTables(file)

        val result = TonKit.migrateDatabase(context, Network.MainNet, WALLET_ID, key)

        assertEquals(DatabaseMigrationResult(1, 0), result)
        assertEquals(plaintext, encryptedTables(file, key))
        assertEquals(1, encryptedUserVersion(file, key))

        assertEquals(TonV2Fixture.account, kit().account)
        withKitDatabase { database -> TonV2Fixture.assertKitContents(database, expectedRecords = emptyList()) }
        assertEquals(2, encryptedUserVersion(file, key))
    }

    @Test
    fun migrateDatabase_tonConnectVersion2Fixture_keepsKeyPairsRequestsAndLastEventId() = runBlocking {
        val file = TonV2Fixture.copyTo(TonV2Fixture.TON_CONNECT_V2_RESOURCE, File(tmp.root, TON_CONNECT_DB_NAME))
        val plaintext = plaintextTables(file)

        val result = TonConnectKit.migrateDatabase(context, key)

        assertEquals(DatabaseMigrationResult(1, 0), result)
        assertFalse(hasPlaintextSqliteHeader(file))
        assertEquals(plaintext, encryptedTables(file, key))

        val database = TonConnectKitDatabase.getInstance(context, TON_CONNECT_DB_NAME, key)
        try {
            TonV2Fixture.assertTonConnectContents(database)
        } finally {
            database.close()
        }
        assertEquals(2, encryptedUserVersion(file, key))
    }

    private suspend fun kit(): TonKit = TonKit.getInstance(
        tonWallet = TonWallet.WatchOnly(TonV2Fixture.ownerAddress.toRaw()),
        network = Network.MainNet,
        context = context,
        walletId = WALLET_ID,
        databaseKey = key,
    )

    private suspend fun withKitDatabase(block: suspend (KitDatabase) -> Unit) {
        val database = KitDatabase.getInstance(context, KIT_DB_NAME, key)
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val WALLET_ID = "fixture"
        const val KIT_DB_NAME = "fixture-MainNet"
        const val TON_CONNECT_DB_NAME = "ton-connect"
    }
}
