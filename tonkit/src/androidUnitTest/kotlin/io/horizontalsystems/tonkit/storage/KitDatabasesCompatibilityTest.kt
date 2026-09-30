package io.horizontalsystems.tonkit.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.horizontalsystems.tonkit.TonV2Fixture
import io.horizontalsystems.tonkit.tonconnect.TonConnectKitDatabase
import io.horizontalsystems.tonkit.tonconnect.tonConnectSchemaPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric cannot load the native SQLCipher library, so the plaintext fixtures are opened by plain Room
// builders with the kit's schema policy; the encrypted path is covered on desktop and on a device.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KitDatabasesCompatibilityTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun kitDatabase_version2Fixture_allValuesMatchWhatWasWritten() = runBlocking {
        val database = kitDatabase(TonV2Fixture.KIT_V2_RESOURCE)
        try {
            TonV2Fixture.assertKitContents(database)
        } finally {
            database.close()
        }
    }

    @Test
    fun kitDatabase_version1Fixture_migratesTo2AndKeepsValues() = runBlocking {
        val database = kitDatabase(TonV2Fixture.KIT_V1_RESOURCE)
        try {
            TonV2Fixture.assertKitContents(database, expectedRecords = emptyList())
        } finally {
            database.close()
        }
    }

    @Test
    fun tonConnectKitDatabase_version2Fixture_allValuesMatchWhatWasWritten() = runBlocking {
        val name = "ton-connect-compatibility"
        TonV2Fixture.copyTo(TonV2Fixture.TON_CONNECT_V2_RESOURCE, context.getDatabasePath(name))
        val database = Room.databaseBuilder(context, TonConnectKitDatabase::class.java, name)
            .tonConnectSchemaPolicy()
            .build()
        try {
            TonV2Fixture.assertTonConnectContents(database)
        } finally {
            database.close()
        }
    }

    private fun kitDatabase(resource: String): KitDatabase {
        val name = "ton-kit-compatibility"
        TonV2Fixture.copyTo(resource, context.getDatabasePath(name))
        return Room.databaseBuilder(context, KitDatabase::class.java, name)
            .kitSchemaPolicy()
            .build()
    }
}
