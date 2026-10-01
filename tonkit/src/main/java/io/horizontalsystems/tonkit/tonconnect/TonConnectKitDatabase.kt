package io.horizontalsystems.tonkit.tonconnect

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import io.horizontalsystems.tonkit.PlatformContext
import io.horizontalsystems.tonkit.storage.tonConnectKitDatabaseBuilder

@Database(
    entities = [
        DAppEntity::class,
        SendRequestEntity::class,
        KeyValue::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(TonConnectDBConverters::class)
abstract class TonConnectKitDatabase : RoomDatabase() {
    abstract fun dAppDao(): DAppDao
    abstract fun sendRequestDao(): SendRequestDao
    abstract fun keyValueDao(): KeyValueDao

    companion object {
        internal fun getInstance(context: PlatformContext, name: String, databaseKey: ByteArray): TonConnectKitDatabase {
            return tonConnectKitDatabaseBuilder(context, name, databaseKey).tonConnectSchemaPolicy().build()
        }
    }
}

internal fun RoomDatabase.Builder<TonConnectKitDatabase>.tonConnectSchemaPolicy(): RoomDatabase.Builder<TonConnectKitDatabase> =
    fallbackToDestructiveMigration(dropAllTables = true)
