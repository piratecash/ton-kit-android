package io.horizontalsystems.tonkit.tonconnect

import androidx.room.execSQL
import androidx.room.useWriterConnection
import io.horizontalsystems.sqlcipher.room.DatabaseKeyMismatchException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationRequiredException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationResult
import io.horizontalsystems.tonkit.ManifestPhase
import io.horizontalsystems.tonkit.PlatformContext
import io.horizontalsystems.tonkit.TonV2Fixture
import io.horizontalsystems.tonkit.encryptedTables
import io.horizontalsystems.tonkit.hasPlaintextSqliteHeader
import io.horizontalsystems.tonkit.interruptMigration
import io.horizontalsystems.tonkit.lockFileName
import io.horizontalsystems.tonkit.migrationArtifacts
import io.horizontalsystems.tonkit.plaintextTables
import io.horizontalsystems.tonkit.storage.TON_CONNECT_DATABASE_NAME
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import java.io.File

/** How the kit wires sqlcipher-room for the shared TON Connect database. */
class TonConnectDatabaseMigrationDesktopTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    private val key = ByteArray(32) { (it * 3).toByte() }
    private val otherKey = ByteArray(32) { (it * 5 + 1).toByte() }

    private val directory: File get() = tmp.root
    private val context: PlatformContext get() = PlatformContext(directory)
    private val database: File get() = File(directory, "ton-connect")

    @Test
    fun migrateDatabase_plaintextDatabase_encryptsAndPreservesData() = runBlocking {
        copyFixture()
        val plaintext = plaintextTables(database)

        val result = TonConnectKit.migrateDatabase(context, key)

        assertEquals(DatabaseMigrationResult(1, 0), result)
        assertFalse(hasPlaintextSqliteHeader(database))
        assertEquals(plaintext, encryptedTables(database, key))
        assertEquals(emptyList<String>(), migrationArtifacts(directory))
    }

    @Test
    fun migrateDatabase_alreadyEncrypted_reportsCountAndKeepsFileBytes() = runBlocking {
        copyFixture()
        TonConnectKit.migrateDatabase(context, key)
        val encryptedBytes = database.readBytes()

        val result = TonConnectKit.migrateDatabase(context, key)

        assertEquals(DatabaseMigrationResult(0, 1), result)
        assertArrayEquals(encryptedBytes, database.readBytes())
    }

    @Test
    fun getInstance_databaseEncryptedWithOtherKey_throwsKeyMismatchWithoutChangingFile() = runBlocking {
        copyFixture()
        TonConnectKit.migrateDatabase(context, key)
        val encryptedBytes = database.readBytes()

        assertThrows(DatabaseKeyMismatchException::class.java) { runBlocking { kit(otherKey) } }

        assertArrayEquals(encryptedBytes, database.readBytes())
    }

    @Test
    fun getInstance_plaintextWithoutMigration_throwsMigrationRequiredWithoutChangingFile() {
        copyFixture()
        val plaintextBytes = database.readBytes()

        assertThrows(DatabaseMigrationRequiredException::class.java) { runBlocking { kit(key) } }

        assertArrayEquals(plaintextBytes, database.readBytes())
    }

    @Test
    fun migrateDatabase_stagedMigrationWasInterrupted_recoversAndMigrates() = runBlocking {
        copyFixture()
        val plaintext = plaintextTables(database)
        interruptMigration(NAMESPACE, database, ManifestPhase.STAGED, otherKey)

        val result = TonConnectKit.migrateDatabase(context, key)

        assertEquals(DatabaseMigrationResult(1, 0), result)
        assertEquals(plaintext, encryptedTables(database, key))
        assertEquals(emptyList<String>(), migrationArtifacts(directory))
    }

    @Test
    fun clear_interruptedMigration_removesDatabaseFamilyAndKeepsTonKitFiles() = runBlocking {
        copyFixture()
        interruptMigration(NAMESPACE, database, ManifestPhase.STAGED, key)
        listOf("-wal", "-shm", "-journal").forEach { suffix -> File("${database.path}$suffix").writeText("x") }
        val tonKitDatabase = TonV2Fixture.copyTo(TonV2Fixture.KIT_V2_RESOURCE, File(directory, "wallet-MainNet"))
        val tonKitBytes = tonKitDatabase.readBytes()

        TonConnectKit.clear(context)

        assertEquals(listOf(lockFileName(NAMESPACE), tonKitDatabase.name).sorted(), directory.list()?.sorted())
        assertArrayEquals(tonKitBytes, tonKitDatabase.readBytes())
    }

    @Test
    fun migrateDatabase_invalidKey_throwsBeforeAnyFileIsCreated() {
        val dataContext = PlatformContext(File(directory, "data"))
        listOf(ByteArray(0), ByteArray(31), ByteArray(33)).forEach { databaseKey ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { TonConnectKit.migrateDatabase(dataContext, databaseKey) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { TonConnectKit.getInstance(dataContext, databaseKey, APP_NAME, APP_VERSION) }
            }
            assertEquals(emptyList<String>(), directory.list()?.toList())
        }
    }

    @Test
    fun removeDAppsExcept_otherWallet_deletesItsDAppsAndSendRequestsOnly() = runBlocking {
        copyFixture()
        TonConnectKit.migrateDatabase(context, key)
        val kit = kit(key)

        kit.removeDAppsExcept(listOf(TonV2Fixture.WALLET_ID_2))

        assertEquals(TonV2Fixture.dApps().filter { it.walletId == TonV2Fixture.WALLET_ID_2 }, kit.getDApps().first())
        val tables = encryptedTables(database, key)
        assertEquals(emptyList<List<String?>>(), tables.getValue("SendRequestEntity"))
        assertEquals(1, tables.getValue("KeyValue").size)
    }

    @Test
    fun removeDAppsExcept_walletWithPendingRequest_keepsItsDAppAndRequest() = runBlocking {
        copyFixture()
        TonConnectKit.migrateDatabase(context, key)
        val kit = kit(key)

        kit.removeDAppsExcept(listOf(TonV2Fixture.WALLET_ID_1, "unknown-wallet"))

        assertEquals(TonV2Fixture.dApps().filter { it.walletId == TonV2Fixture.WALLET_ID_1 }, kit.getDApps().first())
        assertEquals(1, encryptedTables(database, key).getValue("SendRequestEntity").size)
    }

    @Test
    fun removeDAppsExcept_noWallets_deletesEveryDApp() = runBlocking {
        copyFixture()
        TonConnectKit.migrateDatabase(context, key)
        val kit = kit(key)

        kit.removeDAppsExcept(emptyList())

        assertEquals(emptyList<Any>(), kit.getDApps().first())
        assertEquals(emptyList<List<String?>>(), encryptedTables(database, key).getValue("SendRequestEntity"))
    }

    @Test
    fun getAllFlow_storedDAppWithNonHttpUrl_readsEveryDAppWithUriHost() = runBlocking {
        copyFixture()
        TonConnectKit.migrateDatabase(context, key)
        val tonConnectDatabase = TonConnectKitDatabase.getInstance(context, TON_CONNECT_DATABASE_NAME, key)
        try {
            tonConnectDatabase.useWriterConnection {
                it.execSQL("UPDATE DAppEntity SET url = 'ftp://Legacy.Example.com' WHERE url = 'https://dapp-one.example.com'")
            }

            val domains = tonConnectDatabase.dAppDao().getAllFlow().first().map { it.domain.value }

            assertEquals(setOf("Legacy.Example.com", "dapp-two.example.com"), domains.toSet())
        } finally {
            tonConnectDatabase.close()
        }
    }

    private suspend fun kit(databaseKey: ByteArray): TonConnectKit =
        TonConnectKit.getInstance(context, databaseKey, APP_NAME, APP_VERSION)

    private fun copyFixture(): File = TonV2Fixture.copyTo(TonV2Fixture.TON_CONNECT_V2_RESOURCE, database)

    private companion object {
        const val NAMESPACE = "ton-connect"
        const val APP_NAME = "Desktop test"
        const val APP_VERSION = "0"
    }
}
