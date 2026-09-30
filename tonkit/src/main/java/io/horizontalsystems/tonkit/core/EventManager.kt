package io.horizontalsystems.tonkit.core

import co.touchlab.kermit.Logger
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.api.IApi
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.EventInfo
import io.horizontalsystems.tonkit.models.EventSyncState
import io.horizontalsystems.tonkit.models.SyncState
import io.horizontalsystems.tonkit.models.Tag
import io.horizontalsystems.tonkit.models.TagQuery
import io.horizontalsystems.tonkit.models.TagToken
import io.horizontalsystems.tonkit.storage.EventDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class EventManager(
    private val address: Address,
    private val api: IApi,
    private val dao: EventDao,
    private val logger: Logger,
) {
    private val eventFlow = MutableSharedFlow<EventInfoWithTags>()

    private val _syncStateFlow =
        MutableStateFlow<SyncState>(SyncState.NotSynced(TonKit.SyncError.NotStarted))
    val syncStateFlow = _syncStateFlow.asStateFlow()

    suspend fun events(tagQuery: TagQuery, beforeLt: Long?, limit: Int?): List<Event> {
        return dao.events(tagQuery, beforeLt, limit ?: 100)
    }

    fun eventFlow(tagQuery: TagQuery): Flow<EventInfo> {
        var filteredEventFlow: Flow<EventInfoWithTags> = eventFlow.asSharedFlow()

        if (!tagQuery.isEmpty) {
            filteredEventFlow = filteredEventFlow.filter { info: EventInfoWithTags ->
                info.events.any { eventWithTags ->
                    eventWithTags.tags.any { it.conforms(tagQuery) }
                }
            }
        }

        return filteredEventFlow.map { info ->
            EventInfo(
                info.events.map { it.event },
                info.initial
            )
        }
    }

    suspend fun tagTokens(): List<TagToken> {
        return dao.tagTokens()
    }

    suspend fun sync() {
        logger.d { "Syncing events..." }

        if (_syncStateFlow.value is SyncState.Syncing) {
            logger.d { "Syncing events is in progress" }
            return
        }

        _syncStateFlow.update {
            SyncState.Syncing
        }

        try {
            val latestEvent = dao.latestEvent()

            if (latestEvent != null) {
                logger.d { "Fetching latest events..." }

                val startTimestamp = latestEvent.timestamp
                var beforeLt: Long? = null

                do {
                    val events = api.getEvents(address, beforeLt, startTimestamp, limit)
                    logger.d {
                        "Got latest events: ${events.size}, beforeLt: $beforeLt, startTimestamp: $startTimestamp"
                    }

                    handleLatest(events)

                    if (events.size < limit) {
                        break
                    }

                    beforeLt = events.lastOrNull()?.lt

                } while (true)
            }
            val eventSyncState = dao.eventSyncState()
            val allSynced = eventSyncState?.allSynced ?: false

            if (!allSynced) {
                logger.d { "Fetching history events..." }

                val oldestEvent = dao.oldestEvent()
                var beforeLt = oldestEvent?.lt
                do {
                    val events = api.getEvents(address, beforeLt, null, limit)
                    logger.d { "Got history events: ${events.size}, beforeLt: $beforeLt" }

                    handle(events, true)

                    if (events.size < limit) {
                        break
                    }

                    beforeLt = events.lastOrNull()?.lt

                } while (true)

                val newOldestEvent = dao.oldestEvent()

                if (newOldestEvent != null) {
                    dao.save(EventSyncState(allSynced = true))
                }
            }

            _syncStateFlow.update {
                SyncState.Synced
            }
        } catch (e: Throwable) {
            _syncStateFlow.update {
                SyncState.NotSynced(e)
            }
        }
    }

    private suspend fun handleLatest(events: List<Event>) {
        val (inProgressEvents, completedEvents) = events.partition { it.inProgress }

        val eventsToHandle = mutableListOf<Event>()

        if (completedEvents.isNotEmpty()) {
            val existingEvents = dao.events(completedEvents.map { it.id })
            completedEvents.forEach { completedEvent ->
                val existingEvent = existingEvents.find { it.id == completedEvent.id }
                if (existingEvent == null || existingEvent.inProgress) {
                    eventsToHandle.add(completedEvent)
                }
            }
        }

        handle(inProgressEvents + eventsToHandle, false)
    }

    private suspend fun handle(events: List<Event>, initial: Boolean) {
        if (events.isEmpty()) return

        val eventsWithTags = events.map { event ->
            EventWithTags(event, event.tags(address))
        }

        val tags = eventsWithTags.map { it.tags }.flatten()
        dao.saveWithTags(events, tags)

        eventFlow.emit(EventInfoWithTags(eventsWithTags, initial))
    }

    suspend fun isEventCompleted(eventId: String) : Boolean {
        return dao.isEventCompleted(eventId)
    }

    companion object {
        private const val limit = 100
    }

    private data class EventWithTags(
        val event: Event,
        val tags: List<Tag>,
    )

    private data class EventInfoWithTags(
        val events: List<EventWithTags>,
        val initial: Boolean,
    )
}