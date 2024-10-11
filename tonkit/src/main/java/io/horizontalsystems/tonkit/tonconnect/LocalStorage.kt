package io.horizontalsystems.tonkit.tonconnect

class LocalStorage(private val keyValueDao: KeyValueDao) {
    fun setLastSSEventId(v: String) = keyValueDao.set("LastSSEventId", v)
    fun getLastSSEventId() = keyValueDao.get("LastSSEventId")
}
