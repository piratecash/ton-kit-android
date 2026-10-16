package io.horizontalsystems.tonkit.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.horizontalsystems.sqlcipher.room.DatabaseKeyMismatchException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationRequiredException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationResult
import io.horizontalsystems.tonkit.TonV2Fixture
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tonkit.hasPlaintextSqliteHeader
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.tonconnect.TonConnectKit
import io.horizontalsystems.tonkit.tonconnect.TonConnectKitDatabase
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** The SQLCipher path on a real Android runtime: native library, SupportOpenHelperFactory and getDatabasePath. */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseAndroidTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val key = ByteArray(32) { (it * 3).toByte() }
    private val otherKey = ByteArray(32) { (it * 5 + 1).toByte() }
    private val walletId = "encrypted-${UUID.randomUUID()}"
    private val databaseName = "$walletId-${Network.MainNet.name}"
    private val databaseFile = context.getDatabasePath(databaseName)

    @After
    fun tearDown() = runBlocking {
        TonKit.clear(context, Network.MainNet, walletId)
        TonConnectKit.clear(context)
    }

    @Test
    fun migrateDatabase_kitVersion2Fixture_preservesStoredWalletState() = runBlocking {
        copyFixture()

        val result = migrate(key)

        assertEquals(DatabaseMigrationResult(1, 0), result)
        assertFalse(hasPlaintextSqliteHeader(databaseFile))
        assertFixtureOpensWith(key)
    }

    @Test
    fun migrateDatabase_alreadyEncrypted_reportsAlreadyEncrypted() = runBlocking {
        copyFixture()
        migrate(key)

        val result = migrate(key)

        assertEquals(DatabaseMigrationResult(0, 1), result)
    }

    @Test
    fun getInstance_otherKey_throwsKeyMismatchWithoutChangingFile() = runBlocking {
        copyFixture()
        migrate(key)
        val encryptedBytes = databaseFile.readBytes()

        assertThrows(DatabaseKeyMismatchException::class.java) { runBlocking { kit(otherKey) } }

        assertArrayEquals(encryptedBytes, databaseFile.readBytes())
        assertFixtureOpensWith(key)
    }

    @Test
    fun getInstance_plaintextWithoutMigration_throwsMigrationRequired() {
        copyFixture()
        val plaintextBytes = databaseFile.readBytes()

        assertThrows(DatabaseMigrationRequiredException::class.java) { runBlocking { kit(key) } }

        assertArrayEquals(plaintextBytes, databaseFile.readBytes())
    }

    @Test
    fun migrateDatabase_stagedMigrationWasInterrupted_recoversAndPreservesData() = runBlocking {
        copyFixture()
        val staging = File("${databaseFile.path}.sqlcipher-migrating")
        exportEncrypted(databaseFile, staging, otherKey)
        val manifest = File(databaseFile.parentFile, ".ton-kit-sqlcipher-${UUID.randomUUID()}.json")
        manifest.writeText(
            """{"version":1,"phase":"STAGED","entries":[{"databasePath":"${databaseFile.path}","stagingPath":"${staging.path}"}]}"""
        )

        val result = migrate(key)

        assertEquals(DatabaseMigrationResult(1, 0), result)
        assertFalse(manifest.exists())
        assertFalse(staging.exists())
        assertFixtureOpensWith(key)
    }

    @Test
    fun clear_encryptedDatabase_removesEveryFile() = runBlocking {
        copyFixture()
        migrate(key)
        assertFixtureOpensWith(key)

        TonKit.clear(context, Network.MainNet, walletId)

        val leftovers = databaseFile.parentFile?.list()?.filter { name ->
            name.startsWith(databaseFile.name) || (name.startsWith(".ton-kit-sqlcipher-") && name.endsWith(".json"))
        }
        assertTrue("leftovers: $leftovers", leftovers.isNullOrEmpty())
    }

    @Test
    fun migrateDatabase_tonConnectFixture_preservesConnections() = runBlocking {
        TonV2Fixture.copyTo(TonV2Fixture.TON_CONNECT_V2_RESOURCE, context.getDatabasePath(TON_CONNECT_DB_NAME))

        val result = TonConnectKit.migrateDatabase(context, key)

        assertEquals(DatabaseMigrationResult(1, 0), result)
        val database = TonConnectKitDatabase.getInstance(context, TON_CONNECT_DB_NAME, key)
        try {
            TonV2Fixture.assertTonConnectContents(database)
        } finally {
            database.close()
        }
    }

    private suspend fun migrate(databaseKey: ByteArray): DatabaseMigrationResult =
        TonKit.migrateDatabase(context, Network.MainNet, walletId, databaseKey)

    private suspend fun kit(databaseKey: ByteArray): TonKit = TonKit.getInstance(
        tonWallet = TonWallet.WatchOnly(TonV2Fixture.ownerAddress.toRaw()),
        network = Network.MainNet,
        context = context,
        walletId = walletId,
        databaseKey = databaseKey,
    )

    private fun copyFixture() {
        TonV2Fixture.copyTo(TonV2Fixture.KIT_V2_RESOURCE, databaseFile)
    }

    private suspend fun assertFixtureOpensWith(databaseKey: ByteArray) {
        val database = KitDatabase.getInstance(context, databaseName, databaseKey)
        try {
            TonV2Fixture.assertKitContents(database)
        } finally {
            database.close()
        }
    }

    // A staging file as sqlcipher-room writes it: sqlcipher_export into an attached database keyed with x'<hex>'.
    private fun exportEncrypted(source: File, target: File, databaseKey: ByteArray) {
        System.loadLibrary("sqlcipher")
        val keyLiteral = ("x'" + databaseKey.joinToString("") { "%02x".format(it) } + "'").encodeToByteArray()
        // ATTACH inherits these flags: without CREATE_IF_NECESSARY it cannot create the target file.
        val flags = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY
        SQLiteDatabase.openDatabase(source.path, null, flags).use { database ->
            val userVersion = database.rawQuery("PRAGMA user_version", emptyArray<String>()).use { cursor ->
                check(cursor.moveToFirst()) { "SQLCipher returned no user_version" }
                cursor.getInt(0)
            }
            database.execSQL("ATTACH DATABASE ? AS encrypted KEY ?", arrayOf(target.path, keyLiteral))
            database.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            database.rawExecSQL("PRAGMA encrypted.user_version=$userVersion")
            database.rawExecSQL("DETACH DATABASE encrypted")
        }
    }

    private companion object {
        const val TON_CONNECT_DB_NAME = "ton-connect"
    }
}
