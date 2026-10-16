package io.horizontalsystems.tonkit.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RawMessageBroadcastDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: RawMessageBroadcastRecord): Long

    @Query("SELECT * FROM RawMessageBroadcastRecord ORDER BY firstSendTime ASC")
    suspend fun records(): List<RawMessageBroadcastRecord>

    @Query("DELETE FROM RawMessageBroadcastRecord WHERE messageHash = :messageHash")
    suspend fun delete(messageHash: String)

    @Query("UPDATE RawMessageBroadcastRecord SET retriesCount = :retriesCount, lastSendTime = :lastSendTime WHERE messageHash = :messageHash")
    suspend fun updateRetry(messageHash: String, retriesCount: Int, lastSendTime: Long)
}
