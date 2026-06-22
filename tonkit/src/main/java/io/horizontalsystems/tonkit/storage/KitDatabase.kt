package io.horizontalsystems.tonkit.storage

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
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
)
@TypeConverters(Converters::class)
abstract class KitDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun jettonDao(): JettonDao
    abstract fun eventDao(): EventDao
    abstract fun rawMessageBroadcastDao(): RawMessageBroadcastDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
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

        fun getInstance(context: Context, name: String): KitDatabase {
            return Room
                .databaseBuilder(context, KitDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .allowMainThreadQueries()
                .build()
        }
    }
}
