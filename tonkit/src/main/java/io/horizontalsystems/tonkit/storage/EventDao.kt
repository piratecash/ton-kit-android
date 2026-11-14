package io.horizontalsystems.tonkit.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import androidx.room.Transaction
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.EventSyncState
import io.horizontalsystems.tonkit.models.Tag
import io.horizontalsystems.tonkit.models.TagQuery
import io.horizontalsystems.tonkit.models.TagToken

@Dao
interface EventDao {

    @Query("SELECT * FROM EventSyncState LIMIT 0, 1")
    suspend fun eventSyncState(): EventSyncState?

    suspend fun events(tagQuery: TagQuery, beforeLt: Long?, limit: Int): List<Event> {
        val arguments = mutableListOf<String>()
        val whereConditions = mutableListOf<String>()
        var joinClause = ""

        if (!tagQuery.isEmpty) {
            tagQuery.type?.let { type ->
                whereConditions.add("Tag.type = ?")
                arguments.add(type.name)
            }
            tagQuery.platform?.let { platform ->
                whereConditions.add("Tag.platform = ?")
                arguments.add(platform.name)
            }
            tagQuery.jettonAddress?.let { jettonAddress ->
                whereConditions.add("Tag.jettonAddress = ?")
                arguments.add(jettonAddress.toRaw())
            }
            tagQuery.address?.let { address ->
                whereConditions.add("Tag.addresses LIKE ?")
                arguments.add("%${address.toRaw()}%")
            }

            joinClause = "INNER JOIN tag ON event.id = tag.eventId"
        }

        beforeLt?.let {
            whereConditions.add("event.lt < ?")
            arguments.add(it.toString())
        }

        val limitClause = "LIMIT $limit"
        val orderClause = "ORDER BY event.lt DESC"
        val whereClause = if (whereConditions.size > 0) {
            "WHERE ${whereConditions.joinToString(" AND ")}"
        } else {
            ""
        }

        val sql = """
            SELECT DISTINCT Event.*
            FROM Event
            $joinClause
            $whereClause
            $orderClause
            $limitClause
            """

        val query = RoomRawQuery(sql) { statement ->
            arguments.forEachIndexed { index, argument -> statement.bindText(index + 1, argument) }
        }

        return events(query)
    }

    @RawQuery
    suspend fun events(query: RoomRawQuery): List<Event>

    @Query("SELECT * FROM Event WHERE id IN (:ids)")
    suspend fun events(ids: List<String>): List<Event>

    @Query("SELECT COUNT(*) FROM Event WHERE id = :id AND inProgress = 0")
    suspend fun isEventCompleted(id: String): Boolean

    @Query("SELECT * FROM Event ORDER BY lt DESC LIMIT 0, 1")
    suspend fun latestEvent(): Event?

    @Query("SELECT * FROM Event ORDER BY lt ASC LIMIT 0, 1")
    suspend fun oldestEvent(): Event?

    @Query("SELECT platform, jettonAddress FROM Tag WHERE platform IS NOT NULL")
    suspend fun tagTokens(): List<TagToken>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(eventSyncState: EventSyncState)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(events: List<Event>)

    @Transaction
    suspend fun saveWithTags(events: List<Event>, tags: List<Tag>) {
        save(events)
        deleteTags(events.map { it.id })
        insertTags(tags)
    }

    @Query("DELETE FROM Tag WHERE eventId IN (:eventIds)")
    suspend fun deleteTags(eventIds: List<String>)

    @Insert
    suspend fun insertTags(tags: List<Tag>)
}
