package io.horizontalsystems.tonkit.tonconnect

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity

@Database(
    entities = [
        DAppEntity::class,
        SendRequestEntity::class,
    ],
    version = 1,
)
@TypeConverters(TonConnectDBConverters::class)
abstract class TonConnectKitDatabase : RoomDatabase() {
    abstract fun dAppDao(): DAppDao
    abstract fun sendRequestDao(): SendRequestDao

    companion object {
        fun getInstance(context: Context, name: String): TonConnectKitDatabase {
            return Room.databaseBuilder(context, TonConnectKitDatabase::class.java, name)
                .build()
        }
    }
}
