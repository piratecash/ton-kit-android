package io.horizontalsystems.tonkit.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.horizontalsystems.tonkit.models.Action

@Composable
fun Events(viewModel: MainViewModel, uiState: MainUiState) {
    val events = uiState.events

    events?.let {
        LazyColumn {
            items(events) { event ->
                Card(modifier = Modifier.padding(bottom = 12.dp)) {
                    Row(modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)) {
                        Column {
                            Text(event.id)

                            event.actions.forEach { action: Action ->
                                Text(action.type.name)
                            }
                        }
                    }
                }
            }
        }
    }
}
