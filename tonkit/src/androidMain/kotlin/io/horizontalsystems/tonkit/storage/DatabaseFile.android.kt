package io.horizontalsystems.tonkit.storage

import io.horizontalsystems.tonkit.PlatformContext
import java.io.File

internal actual fun databaseFile(context: PlatformContext, name: String): File =
    context.getDatabasePath(name)
