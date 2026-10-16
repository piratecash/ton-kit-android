package io.horizontalsystems.tonkit.storage

import io.horizontalsystems.sqlcipher.room.DatabaseKeyMismatchException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationRequiredException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationResult
import io.horizontalsystems.tonkit.ManifestPhase
import io.horizontalsystems.tonkit.PlatformContext
import io.horizontalsystems.tonkit.TonV2Fixture
import io.horizontalsystems.tonkit.backupOf
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tonkit.encryptedTables
import io.horizontalsystems.tonkit.hasPlaintextSqliteHeader
import io.horizontalsystems.tonkit.interruptMigration
import io.horizontalsystems.tonkit.lockFileName
import io.horizontalsystems.tonkit.migrationArtifacts
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.plaintextTables
import io.horizontalsystems.tonkit.tonconnect.TonConnectKit
import io.horizontalsystems.tonkit.writeManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

/** How the kit wires sqlcipher-room for the per-account databases; engine internals are covered by the module. */
class TonKitDatabaseMigrationDesktopTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    private val key = ByteArray(32) { it.toByte() }
    private val otherKey = ByteArray(32) { (it + 1).toByte() }

    private val directory: File get() = tmp.root
    private val context: PlatformContext get() = PlatformContext(directory)
    private val database: File get() = File(directory, "$WALLET_ID-MainNet")

    @Test
    fun migrateDatabase_plaintextDatabase_encryptsAndPreservesData() = runBlocking {
        copyFixture(database)
        val plaintext = plaintextTables(database)

        val result = migrate(key)

        assertEquals(DatabaseMigrationResult(1, 0), result)
        assertFalse(hasPlaintextSqliteHeader(database))
        assertEquals(plaintext, encryptedTables(database, key))
        assertEquals(emptyList<String>(), migrationArtifacts(directory))
    }

    @Test
    fun migrateDatabase_alreadyEncrypted_reportsCountAndKeepsFileBytes() = runBlocking {
        copyFixture(database)
        migrate(key)
        val encryptedBytes = database.readBytes()

        val result = migrate(key)

        assertEquals(DatabaseMigrationResult(0, 1), result)
        assertArrayEquals(encryptedBytes, database.readBytes())
    }

    @Test
    fun migrateDatabase_otherKey_throwsKeyMismatchWithoutChangingFile() = runBlocking {
        copyFixture(database)
        migrate(key)
        val encryptedBytes = database.readBytes()

        assertThrows(DatabaseKeyMismatchException::class.java) { runBlocking { migrate(otherKey) } }

        assertArrayEquals(encryptedBytes, database.readBytes())
    }

    @Test
    fun getInstance_databaseEncryptedWithOtherKey_throwsKeyMismatchWithoutChangingFile() = runBlocking {
        copyFixture(database)
        migrate(key)
        val encryptedBytes = database.readBytes()

        assertThrows(DatabaseKeyMismatchException::class.java) { runBlocking { kit(otherKey) } }

        assertArrayEquals(encryptedBytes, database.readBytes())
        assertEquals(TonV2Fixture.account, kit(key).account)
    }

    @Test
    fun getInstance_plaintextWithoutMigration_throwsMigrationRequiredWithoutChangingFile() {
        copyFixture(database)
        val plaintextBytes = database.readBytes()

        assertThrows(DatabaseMigrationRequiredException::class.java) { runBlocking { kit(key) } }

        assertArrayEquals(plaintextBytes, database.readBytes())
    }

    @Test
    fun migrateDatabase_stagedMigrationWasInterrupted_recoversAndMigrates() = runBlocking {
        copyFixture(database)
        val plaintext = plaintextTables(database)
        interruptMigration(NAMESPACE, database, ManifestPhase.STAGED, key)

        val result = migrate(key)

        assertEquals(DatabaseMigrationResult(1, 0), result)
        assertEquals(plaintext, encryptedTables(database, key))
        assertEquals(emptyList<String>(), migrationArtifacts(directory))
    }

    @Test
    fun migrateDatabase_stagedUnderOtherKeyWasInterrupted_restoresPlaintextAndEncryptsWithNewKey() = runBlocking {
        copyFixture(database)
        val plaintext = plaintextTables(database)
        interruptMigration(NAMESPACE, database, ManifestPhase.STAGED, key)

        val result = migrate(otherKey)

        assertEquals(DatabaseMigrationResult(1, 0), result)
        assertEquals(plaintext, encryptedTables(database, otherKey))
        assertEquals(emptyList<String>(), migrationArtifacts(directory))
    }

    @Test
    fun migrateDatabase_committedMigrationWasInterrupted_finishesItAndKeepsData() = runBlocking {
        copyFixture(database)
        val plaintext = plaintextTables(database)
        interruptMigration(NAMESPACE, database, ManifestPhase.COMMITTED, key)

        val result = migrate(key)

        assertEquals(DatabaseMigrationResult(0, 1), result)
        assertEquals(plaintext, encryptedTables(database, key))
        assertEquals(emptyList<String>(), migrationArtifacts(directory))
    }

    @Test
    fun migrateDatabase_committedUnderOtherKeyWasInterrupted_throwsKeyMismatchAndKeepsCiphertext() {
        copyFixture(database)
        val plaintext = plaintextTables(database)
        interruptMigration(NAMESPACE, database, ManifestPhase.COMMITTED, key)

        assertThrows(DatabaseKeyMismatchException::class.java) { runBlocking { migrate(otherKey) } }

        assertEquals(plaintext, encryptedTables(database, key))
        assertEquals(emptyList<String>(), migrationArtifacts(directory))
    }

    @Test
    fun migrateDatabase_clearWasInterrupted_finishesClearing() = runBlocking {
        copyFixture(database)
        migrate(key)
        writeManifest(NAMESPACE, database, ManifestPhase.CLEARING)

        val result = migrate(key)

        assertEquals(DatabaseMigrationResult(0, 0), result)
        assertEquals(listOf(lockFileName(NAMESPACE)), directory.list()?.toList())
    }

    @Test
    fun clear_interruptedMigration_removesDatabaseFamilyAndMigrationLeftovers() = runBlocking {
        copyFixture(database)
        interruptMigration(NAMESPACE, database, ManifestPhase.STAGED, key)
        listOf("-wal", "-shm", "-journal").forEach { suffix -> File("${database.path}$suffix").writeText("x") }

        TonKit.clear(context, Network.MainNet, WALLET_ID)

        assertEquals(listOf(lockFileName(NAMESPACE)), directory.list()?.toList())
    }

    @Test
    fun migrateAndClear_otherNamespacesInSameDirectory_areIgnoredAndUntouched() = runBlocking {
        val foreignFiles = FOREIGN_NAMESPACES.flatMap { namespace ->
            val foreignDatabase = File(directory, "$namespace.db")
            val backup = copyFixture(backupOf(foreignDatabase))
            val manifest = File(directory, ".$namespace-sqlcipher-0123456789abcdef.json")
            manifest.writeText("""{"version":1,"phase":"STAGED","entries":[]}""")
            val lock = File(directory, lockFileName(namespace)).apply { writeText("") }
            listOf(backup, manifest, lock)
        }
        val snapshot = foreignFiles.associateWith(File::readBytes)
        copyFixture(database)

        val result = migrate(key)
        kit(key)
        TonKit.clear(context, Network.MainNet, WALLET_ID)

        assertEquals(DatabaseMigrationResult(1, 0), result)
        assertFalse(database.exists())
        snapshot.forEach { (file, bytes) -> assertArrayEquals(file.name, bytes, file.readBytes()) }
        // Room's JVM open lock outlives the database; sqlcipher-room's clear does not know that file.
        val expected = foreignFiles.map(File::getName) + lockFileName(NAMESPACE) + "${database.name}.lck"
        assertEquals(expected.sorted(), directory.list()?.sorted())
    }

    @Test
    fun invalidArguments_throwBeforeAnyFileIsCreated() {
        val dataContext = PlatformContext(File(directory, "data"))
        invalidArguments().forEach { (walletId, databaseKey) ->
            assertThrows(walletId, IllegalArgumentException::class.java) {
                runBlocking { TonKit.migrateDatabase(dataContext, Network.MainNet, walletId, databaseKey) }
            }
            assertThrows(walletId, IllegalArgumentException::class.java) {
                runBlocking { kit(databaseKey, dataContext, walletId) }
            }
            if (databaseKey.size == key.size) {
                assertThrows(walletId, IllegalArgumentException::class.java) {
                    runBlocking { TonKit.clear(dataContext, Network.MainNet, walletId) }
                }
            }
            assertEquals("files after '$walletId'", emptyList<String>(), directory.list()?.toList())
        }
    }

    @Test
    fun migrateDatabase_twoAccountsAndTonConnectInParallel_allSucceed() = runBlocking {
        val accounts = listOf("parallel-a", "parallel-b")
        accounts.forEach { walletId -> copyFixture(File(directory, "$walletId-MainNet")) }
        TonV2Fixture.copyTo(TonV2Fixture.TON_CONNECT_V2_RESOURCE, File(directory, "ton-connect"))

        val results = listOf(
            async(Dispatchers.IO) { TonKit.migrateDatabase(context, Network.MainNet, accounts[0], key) },
            async(Dispatchers.IO) { TonKit.migrateDatabase(context, Network.MainNet, accounts[1], key) },
            async(Dispatchers.IO) { TonConnectKit.migrateDatabase(context, key) },
        ).awaitAll()

        assertEquals(List(3) { DatabaseMigrationResult(1, 0) }, results)
        assertEquals(emptyList<String>(), migrationArtifacts(directory))
    }

    @Test
    fun getInstance_whileAnotherAccountOfNamespaceMigrates_succeeds() = runBlocking {
        copyFixture(database)
        migrate(key)
        // Repeated: each round races a fresh migration against the open.
        repeat(5) { round ->
            val migrating = "migrating-$round"
            copyFixture(File(directory, "$migrating-MainNet"))

            val migration = async(Dispatchers.IO) { TonKit.migrateDatabase(context, Network.MainNet, migrating, key) }
            val kit = async(Dispatchers.IO) { kit(key) }

            assertEquals(DatabaseMigrationResult(1, 0), migration.await())
            assertEquals(TonV2Fixture.account, kit.await().account)
        }
    }

    private suspend fun migrate(databaseKey: ByteArray): DatabaseMigrationResult =
        TonKit.migrateDatabase(context, Network.MainNet, WALLET_ID, databaseKey)

    private suspend fun kit(
        databaseKey: ByteArray,
        kitContext: PlatformContext = context,
        walletId: String = WALLET_ID,
    ): TonKit = TonKit.getInstance(
        tonWallet = TonWallet.WatchOnly(TonV2Fixture.ownerAddress.toRaw()),
        network = Network.MainNet,
        context = kitContext,
        walletId = walletId,
        databaseKey = databaseKey,
    )

    private fun copyFixture(target: File): File = TonV2Fixture.copyTo(TonV2Fixture.KIT_V2_RESOURCE, target)

    private fun invalidArguments(): List<Pair<String, ByteArray>> = listOf(
        WALLET_ID to ByteArray(31),
        WALLET_ID to ByteArray(33),
        "" to key,
        " " to key,
        "a/b" to key,
        "a\\b" to key,
        ".ton-kit-sqlcipher-x" to key,
        ".ton-connect-sqlcipher-x" to key,
        ".bitcoin-kit-sqlcipher-x" to key,
        ".tron-kit-sqlcipher-x" to key,
        ".stellar-kit-sqlcipher-x" to key,
        ".solana-kit-sqlcipher-x" to key,
        "wallet.plaintext-backup" to key,
        "wallet.sqlcipher-migrating" to key,
    )

    private companion object {
        const val NAMESPACE = "ton-kit"
        const val WALLET_ID = "wallet"
        val FOREIGN_NAMESPACES = listOf("ton-connect", "bitcoin-kit", "tron-kit", "stellar-kit", "solana-kit")
    }
}
