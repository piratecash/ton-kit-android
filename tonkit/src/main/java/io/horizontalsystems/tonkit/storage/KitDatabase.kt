package io.horizontalsystems.tonkit.storage

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import io.horizontalsystems.tonkit.PlatformContext
import io.horizontalsystems.tonkit.models.Account
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.EventSyncState
import io.horizontalsystems.tonkit.models.JettonBalance
import io.horizontalsystems.tonkit.models.Tag

@Database(
    entities = [
        Account::class,
        JettonBalance::class,
        Event::class,
        EventSyncState::class,
        Tag::class,
        RawMessageBroadcastRecord::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KitDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun jettonDao(): JettonDao
    abstract fun eventDao(): EventDao
    abstract fun rawMessageBroadcastDao(): RawMessageBroadcastDao

    companion object {
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS RawMessageBroadcastRecord (
                        messageHash TEXT NOT NULL,
                        bocBase64 TEXT NOT NULL,
                        validUntil INTEGER NOT NULL,
                        senderAddress TEXT,
                        seqno INTEGER,
                        firstSendTime INTEGER NOT NULL,
                        lastSendTime INTEGER NOT NULL,
                        retriesCount INTEGER NOT NULL,
                        PRIMARY KEY(messageHash)
                    )
                    """.trimIndent()
                )
            }
        }

        internal fun getInstance(context: PlatformContext, name: String, databaseKey: ByteArray): KitDatabase {
            return kitDatabaseBuilder(context, name, databaseKey).kitSchemaPolicy().build()
        }
    }
}

internal fun RoomDatabase.Builder<KitDatabase>.kitSchemaPolicy(): RoomDatabase.Builder<KitDatabase> =
    addMigrations(KitDatabase.MIGRATION_1_2)
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)
