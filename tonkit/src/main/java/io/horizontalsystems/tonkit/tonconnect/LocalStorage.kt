package io.horizontalsystems.tonkit.tonconnect

class LocalStorage(private val keyValueDao: KeyValueDao) {
    suspend fun setLastSSEventId(v: String) = keyValueDao.set("LastSSEventId", v)
    suspend fun getLastSSEventId() = keyValueDao.get("LastSSEventId")
}
