package io.horizontalsystems.tonkit.storage

import androidx.room.RoomDatabase
import androidx.room.useReaderConnection
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationResult
import io.horizontalsystems.sqlcipher.room.SqlCipherDatabases
import io.horizontalsystems.tonkit.models.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

// The engine rejects a concurrent migrate/clear and an open during any migration of the namespace,
// so within the process they queue on this lock instead.
internal class KitDatabaseNamespace(namespace: String) {
    val databases = SqlCipherDatabases(namespace)
    private val mutex = Mutex()

    // Each database is its own migration group keyed by its path, so clearing it also drops its interrupted migration.
    suspend fun migrate(file: File, databaseKey: ByteArray): DatabaseMigrationResult = mutex.withLock {
        databases.migrateDatabases(file.directoryPath, listOf(file.name), file.path, databaseKey)
    }

    suspend fun clear(file: File) = mutex.withLock {
        withContext(Dispatchers.IO) {
            databases.clearDatabases(file.directoryPath, listOf(file.name), file.path)
        }
    }

    /** Builds and opens the database, so key and open failures surface here rather than on a later query. */
    suspend fun <T : RoomDatabase> open(build: () -> T): T = mutex.withLock {
        withContext(Dispatchers.IO) {
            val database = build()
            try {
                database.useReaderConnection { }
                database
            } catch (error: Throwable) {
                database.close()
                throw error
            }
        }
    }
}

// The namespaces name the on-disk manifest and lock files (`.ton-kit-sqlcipher*`, `.ton-connect-sqlcipher*`);
// they must never change.
internal val tonKitNamespace = KitDatabaseNamespace("ton-kit")
internal val tonConnectNamespace = KitDatabaseNamespace("ton-connect")

internal const val TON_CONNECT_DATABASE_NAME = "ton-connect"

internal fun kitDatabaseName(walletId: String, network: Network) = "$walletId-${network.name}"

internal fun requireValidKitDatabase(walletId: String, network: Network) {
    require(walletId.isNotBlank()) { "Wallet id must not be blank" }
    requireValidDatabaseName(kitDatabaseName(walletId, network))
}

internal fun requireValidDatabaseKey(databaseKey: ByteArray) {
    require(databaseKey.size == DATABASE_KEY_SIZE) { "Database key must contain exactly $DATABASE_KEY_SIZE bytes" }
}

private fun requireValidDatabaseName(name: String) {
    require(name.isNotBlank()) { "Database name must not be blank" }
    require(name.none { character -> character == '/' || character == '\\' }) {
        "Database name must not contain a path separator: $name"
    }
    require(RESERVED_PREFIXES.none(name::startsWith)) { "Database name uses a reserved migration prefix: $name" }
    // Contains, not endsWith: the SQLite family of a staging file (-wal, -shm, ...) is recovered too.
    require(RESERVED_SUFFIXES.none(name::contains)) { "Database name uses a reserved migration suffix: $name" }
}

private const val DATABASE_KEY_SIZE = 32

// Mirror sqlcipher-room's private file names, so a wallet database never collides with migration files.
private val RESERVED_PREFIXES = listOf(
    ".ton-kit-sqlcipher",
    ".ton-connect-sqlcipher",
    ".bitcoin-kit-sqlcipher",
    ".tron-kit-sqlcipher",
    ".stellar-kit-sqlcipher",
    ".solana-kit-sqlcipher",
)
private val RESERVED_SUFFIXES = listOf(".sqlcipher-migrating", ".plaintext-backup")

private val File.directoryPath: String
    get() = checkNotNull(absoluteFile.parent) { "Database path has no parent directory: $path" }
