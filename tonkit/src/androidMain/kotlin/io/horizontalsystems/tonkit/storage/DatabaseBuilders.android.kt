package io.horizontalsystems.tonkit.storage

import androidx.room.Room
import androidx.room.RoomDatabase
import io.horizontalsystems.tonkit.PlatformContext
import io.horizontalsystems.tonkit.tonconnect.TonConnectKitDatabase

internal fun kitDatabaseBuilder(
    context: PlatformContext,
    name: String,
    databaseKey: ByteArray,
): RoomDatabase.Builder<KitDatabase> =
    tonKitNamespace.databases.encrypted(
        Room.databaseBuilder(context, KitDatabase::class.java, name),
        databaseFile(context, name).path,
        databaseKey,
    )

internal fun tonConnectKitDatabaseBuilder(
    context: PlatformContext,
    name: String,
    databaseKey: ByteArray,
): RoomDatabase.Builder<TonConnectKitDatabase> =
    tonConnectNamespace.databases.encrypted(
        Room.databaseBuilder(context, TonConnectKitDatabase::class.java, name),
        databaseFile(context, name).path,
        databaseKey,
    )
