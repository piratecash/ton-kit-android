package io.horizontalsystems.tonkit

import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.horizontalsystems.sqlcipher.SqlCipherDriver
import io.horizontalsystems.sqlcipher.SqlCipherMigration
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal const val STAGING_SUFFIX = ".sqlcipher-migrating"
internal const val BACKUP_SUFFIX = ".plaintext-backup"

/** Every row of every table, blobs as hex, so two files compare value for value. */
internal fun plaintextTables(file: File): Map<String, List<List<String?>>> =
    BundledSQLiteDriver().open(file.path).use(::readTables)

internal fun encryptedTables(file: File, databaseKey: ByteArray): Map<String, List<List<String?>>> =
    SqlCipherDriver(databaseKey).use { driver -> driver.open(file.path).use(::readTables) }

internal fun encryptedUserVersion(file: File, databaseKey: ByteArray): Int =
    SqlCipherDriver(databaseKey).use { driver ->
        driver.open(file.path).use { connection ->
            connection.prepare("PRAGMA user_version").use { statement ->
                check(statement.step()) { "No user_version in ${file.name}" }
                statement.getInt(0)
            }
        }
    }

private fun readTables(connection: SQLiteConnection): Map<String, List<List<String?>>> {
    val tables = connection.prepare("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").use { statement ->
        buildList { while (statement.step()) add(statement.getText(0)) }
    }
    return tables.associateWith { table ->
        connection.prepare("SELECT * FROM `$table` ORDER BY rowid").use { statement ->
            buildList {
                while (statement.step()) {
                    add(List(statement.getColumnCount()) { column ->
                        when (statement.getColumnType(column)) {
                            SQLITE_DATA_NULL -> null
                            SQLITE_DATA_BLOB -> statement.getBlob(column).toHexString()
                            else -> statement.getText(column)
                        }
                    })
                }
            }
        }
    }
}

internal enum class ManifestPhase { STAGED, COMMITTED, CLEARING }

/**
 * Leaves the files a migration killed in [phase] leaves behind: ciphertext under [databaseKey], the
 * plaintext backup and the manifest the kit itself would have written for [database].
 */
internal fun interruptMigration(namespace: String, database: File, phase: ManifestPhase, databaseKey: ByteArray) {
    val staging = File("${database.path}$STAGING_SUFFIX")
    SqlCipherMigration.exportPlaintext(database.path, staging.path, databaseKey)
    Files.move(database.toPath(), backupOf(database).toPath(), StandardCopyOption.ATOMIC_MOVE)
    Files.move(staging.toPath(), database.toPath(), StandardCopyOption.ATOMIC_MOVE)
    writeManifest(namespace, database, phase)
}

internal fun writeManifest(namespace: String, database: File, phase: ManifestPhase) {
    manifestFile(namespace, database).writeText(manifestJson(phase, database))
}

// The kit's migration id is the database path; same derivation as sqlcipher-room: SHA-256 prefix in hex.
internal fun manifestFile(namespace: String, database: File): File {
    val digest = MessageDigest.getInstance("SHA-256").digest(database.path.encodeToByteArray())
    val id = digest.take(8).joinToString("") { "%02x".format(it) }
    return File(database.parentFile, ".$namespace-sqlcipher-$id.json")
}

internal fun lockFileName(namespace: String) = ".$namespace-sqlcipher.lock"

internal fun backupOf(file: File): File = File("${file.path}$BACKUP_SUFFIX")

// The sqlcipher-room manifest format, version 1.
private fun manifestJson(phase: ManifestPhase, database: File): String =
    """{"version":1,"phase":"${phase.name}","entries":[{"databasePath":${jsonString(database.path)},""" +
        """"stagingPath":${jsonString("${database.path}$STAGING_SUFFIX")}}]}"""

private fun jsonString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

internal fun migrationArtifacts(directory: File): List<String> = directory.list().orEmpty().filter { name ->
    name.endsWith(".json") || name.contains(STAGING_SUFFIX) || name.contains(BACKUP_SUFFIX)
}
