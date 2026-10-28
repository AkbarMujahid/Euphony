package com.euphony.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.euphony.player.AppGraph
import com.euphony.player.data.LibraryGrouping
import com.euphony.player.model.Track
import com.euphony.player.ui.theme.Accent
import com.euphony.player.ui.theme.TextPrimary
import com.euphony.player.ui.theme.TextSecondary

/** Opens the "Add to playlist" picker for the given track ids. */
val LocalAddToPlaylist = staticCompositionLocalOf<(List<Long>) -> Unit> { {} }

class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

enum class TrackLeading { Cover, Number, None }

@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: TrackLeading = TrackLeading.Cover,
    extraActions: List<MenuAction> = emptyList(),
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (leading) {
            TrackLeading.Cover -> CoverArt(track.uri, Modifier.size(48.dp), corner = 6.dp)
            TrackLeading.Number -> Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                if (isCurrent) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = "Now playing", tint = Accent, modifier = Modifier.size(20.dp))
                } else {
                    val n = LibraryGrouping.displayTrackNumber(track.trackNumber)
                    Text(
                        if (n > 0) n.toString() else "–",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            TrackLeading.None -> Unit
        }
        if (leading != TrackLeading.None) Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) Accent else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(track.artist)
                    if (track.isFlac) append(" · FLAC")
                    if (track.durationMs > 0) append(" · ").append(formatDuration(track.durationMs))
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TrackMenuButton(track, extraActions)
    }
}

@Composable
fun TrackMenuButton(track: Track, extraActions: List<MenuAction> = emptyList()) {
    val message = LocalMessenger.current
    val addToPlaylist = LocalAddToPlaylist.current
    val actions = buildList {
        add(MenuAction("Play next", Icons.Rounded.SkipNext) {
            AppGraph.player.playNext(listOf(track))
            message("Playing \"${track.title}\" next")
        })
        add(MenuAction("Add to queue", Icons.AutoMirrored.Rounded.QueueMusic) {
            AppGraph.player.addToQueue(listOf(track))
            message("Added to queue")
        })
        if (track.id >= 0) {
            add(MenuAction("Add to playlist…", Icons.AutoMirrored.Rounded.PlaylistAdd) { addToPlaylist(listOf(track.id)) })
        }
        addAll(extraActions)
    }
    OverflowMenu(actions, contentDescription = "More options for ${track.title}")
}

@Composable
fun OverflowMenu(actions: List<MenuAction>, contentDescription: String = "More options") {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = contentDescription, tint = TextSecondary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    onClick = {
                        open = false
                        action.onClick()
                    },
                )
            }
        }
    }
}
