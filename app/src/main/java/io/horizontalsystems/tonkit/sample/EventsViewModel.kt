package io.horizontalsystems.tonkit.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.TagQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EventsViewModel : ViewModel() {
    private val tonKit = App.tonKit
    private var events: List<Event>? = null
    private val tagQuery = TagQuery(null, null, null, null)
    private var page = 1

    var uiState by mutableStateOf(
        EventsUiState(
            events = events
        )
    )
        private set

    init {
        viewModelScope.launch(Dispatchers.Default) {
            tonKit.eventFlow(tagQuery).collect {
                reloadEvents()
            }
        }

        reloadEvents()
    }

    fun onBottomReached() {
        page++
        reloadEvents()
    }

    private fun reloadEvents() {
        events = tonKit.events(tagQuery, limit = 10 * page)
        emitState()
    }

    private fun emitState() {
        viewModelScope.launch {
            uiState = EventsUiState(
                events = events
            )
        }
    }
}


data class EventsUiState(val events: List<Event>?)