package io.horizontalsystems.tonkit.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.EventSyncState
import io.horizontalsystems.tonkit.models.Tag
import io.horizontalsystems.tonkit.models.TagQuery
import io.horizontalsystems.tonkit.models.TagToken

@Dao
interface EventDao {

    @Query("SELECT * FROM EventSyncState LIMIT 0, 1")
    fun eventSyncState(): EventSyncState?

    fun events(tagsQuery: TagQuery, beforeLt: Long?, limit: Int): List<Event> {
        TODO()
    }

    @Query("SELECT * FROM Event WHERE id IN (:ids)")
    fun events(ids: List<String>): List<Event>

    @Query("SELECT * FROM Event ORDER BY lt DESC LIMIT 0, 1")
    fun latestEvent(): Event?

    @Query("SELECT * FROM Event ORDER BY lt ASC LIMIT 0, 1")
    fun oldestEvent(): Event?

    @Query("SELECT platform, jettonAddress FROM Tag WHERE platform IS NOT NULL")
    fun tagTokens(): List<TagToken>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(eventSyncState: EventSyncState)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(events: List<Event>)

    fun resave(tags: List<Tag>, eventIds: List<String>) {
        deleteTags(eventIds)
        insertTags(tags)
    }

    @Query("DELETE FROM Tag WHERE eventId IN (:eventIds)")
    fun deleteTags(eventIds: List<String>)

    @Insert
    fun insertTags(tags: List<Tag>)
}
