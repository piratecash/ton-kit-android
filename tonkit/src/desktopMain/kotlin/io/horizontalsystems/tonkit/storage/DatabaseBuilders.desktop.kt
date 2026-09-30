package io.horizontalsystems.tonkit.storage

import androidx.room.Room
import androidx.room.RoomDatabase
import io.horizontalsystems.tonkit.PlatformContext
import io.horizontalsystems.tonkit.tonconnect.TonConnectKitDatabase
import kotlinx.coroutines.Dispatchers

internal fun kitDatabaseBuilder(
    context: PlatformContext,
    name: String,
    databaseKey: ByteArray,
): RoomDatabase.Builder<KitDatabase> = desktopDatabaseBuilder(tonKitNamespace, context, name, databaseKey)

internal fun tonConnectKitDatabaseBuilder(
    context: PlatformContext,
    name: String,
    databaseKey: ByteArray,
): RoomDatabase.Builder<TonConnectKitDatabase> =
    desktopDatabaseBuilder(tonConnectNamespace, context, name, databaseKey)

private inline fun <reified T : RoomDatabase> desktopDatabaseBuilder(
    namespace: KitDatabaseNamespace,
    context: PlatformContext,
    name: String,
    databaseKey: ByteArray,
): RoomDatabase.Builder<T> {
    val file = databaseFile(context, name)
    // Verifies the file against the key before any directory is created.
    val builder = namespace.databases.encrypted(Room.databaseBuilder<T>(file.path), file.path, databaseKey)
    // Unlike Android's getDatabasePath, a JVM driver does not create the parent directory.
    file.absoluteFile.parentFile?.mkdirs()
    return builder.setQueryCoroutineContext(Dispatchers.IO)
}
