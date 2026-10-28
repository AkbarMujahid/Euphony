package com.euphony.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.euphony.player.AppGraph
import com.euphony.player.model.PlaybackUiState
import com.euphony.player.ui.theme.Accent
import com.euphony.player.ui.theme.TextPrimary
import com.euphony.player.ui.theme.TextSecondary

@Composable
fun QueueScreen(vm: UiViewModel, state: PlaybackUiState, contentPadding: PaddingValues) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        if (state.currentIndex > 0) listState.scrollToItem((state.currentIndex - 1).coerceAtLeast(0))
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text("Queue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(pluralTracks(state.queue.size), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        if (state.queue.isEmpty()) {
            EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, "The queue is empty", "Play something from your library.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
            // Queue entries can repeat the same track, so key by position.
            itemsIndexed(state.queue, key = { index, t -> "$index:${t.id}:${t.uri}" }) { index, track ->
                val current = index == state.currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (current) Accent.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { AppGraph.player.playQueueIndex(index) }
                        .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverArt(track.uri, Modifier.size(44.dp), corner = 6.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            color = if (current) Accent else TextPrimary,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(track.artist, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { AppGraph.player.moveQueueItem(index, index - 1) }, enabled = index > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up")
                    }
                    IconButton(onClick = { AppGraph.player.moveQueueItem(index, index + 1) }, enabled = index < state.queue.lastIndex) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down")
                    }
                    IconButton(onClick = { AppGraph.player.removeFromQueue(index) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove from queue", tint = TextSecondary)
                    }
                }
            }
        }
    }
}
