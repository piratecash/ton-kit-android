package io.horizontalsystems.tonkit.tonconnect

import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import kotlinx.coroutines.flow.Flow

class DAppManager(private val dao: DAppDao) {
    fun addApp(app: DAppEntity) {
        dao.save(app)
    }

    fun getAllFlow(): Flow<List<DAppEntity>> {
        return dao.getAllFlow()
    }

    fun remove(dApp: DAppEntity) {
        return dao.delete(dApp)
    }
}
