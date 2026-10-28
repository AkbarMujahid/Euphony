package com.euphony.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.euphony.player.AppGraph
import com.euphony.player.model.LibrarySnapshot
import com.euphony.player.model.Playlist
import com.euphony.player.model.Track
import com.euphony.player.ui.theme.Accent
import com.euphony.player.ui.theme.SurfaceHigh
import com.euphony.player.ui.theme.TextSecondary

/** A track as it appears in a collection; [position] is its index in the playlist, when it is one. */
private data class Entry(val track: Track, val position: Int)

private class CollectionInfo(
    val title: String,
    val subtitle: String,
    val coverUri: String?,
    val icon: ImageVector,
    val entries: List<Entry>,
    val playlist: Playlist?,
)

private fun resolve(screen: Screen.Collection, library: LibrarySnapshot, playlists: List<Playlist>): CollectionInfo? {
    fun plain(ids: List<Long>) = library.resolve(ids).mapIndexed { i, t -> Entry(t, i) }
    return when (screen.kind) {
        CollectionKind.Album -> library.albums.firstOrNull { it.id.toString() == screen.key }?.let { album ->
            val entries = plain(album.trackIds)
            CollectionInfo(
                album.title,
                listOfNotNull(album.artist, album.year.takeIf { it > 0 }?.toString(), pluralTracks(entries.size)).joinToString(" · "),
                album.coverTrackUri, Icons.Rounded.Person, entries, null,
            )
        }
        CollectionKind.Artist -> library.artists.firstOrNull { it.name == screen.key }?.let { artist ->
            val entries = plain(artist.trackIds)
            CollectionInfo(artist.name, "Artist · ${pluralTracks(entries.size)}", null, Icons.Rounded.Person, entries, null)
        }
        CollectionKind.Folder -> library.folders.firstOrNull { it.path == screen.key }?.let { folder ->
            val entries = plain(folder.trackIds)
            CollectionInfo(folder.name, "${folder.path.trimEnd('/')} · ${pluralTracks(entries.size)}", entries.firstOrNull()?.track?.uri, Icons.Rounded.Folder, entries, null)
        }
        CollectionKind.Playlist -> playlists.firstOrNull { it.id == screen.key }?.let { playlist ->
            // Keep each track's real playlist index: songs deleted from the device are skipped, not renumbered.
            val entries = playlist.trackIds.mapIndexedNotNull { i, id -> library.byId[id]?.let { Entry(it, i) } }
            CollectionInfo(playlist.name, "Playlist · ${pluralTracks(entries.size)}", null, Icons.AutoMirrored.Rounded.PlaylistPlay, entries, playlist)
        }
    }
}

@Composable
fun CollectionScreen(
    vm: UiViewModel,
    screen: Screen.Collection,
    library: LibrarySnapshot,
    playlists: List<Playlist>,
    currentTrackId: Long?,
    contentPadding: PaddingValues,
) {
    val info = resolve(screen, library, playlists)
    if (info == null) {
        // The playlist was deleted or the files vanished; nothing left to show.
        if (!library.isLoading) LaunchedEffect(screen) { vm.back() }
        return
    }
    val tracks = info.entries.map { it.track }
    val message = LocalMessenger.current
    val addToPlaylist = LocalAddToPlaylist.current
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "header") {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                    Spacer(Modifier.weight(1f))
                    val actions = buildList {
                        add(MenuAction("Add all to queue", Icons.AutoMirrored.Rounded.QueueMusic) {
                            AppGraph.player.addToQueue(tracks)
                            message("Added ${pluralTracks(tracks.size)} to queue")
                        })
                        if (info.playlist == null) {
                            add(MenuAction("Add all to playlist…", Icons.AutoMirrored.Rounded.PlaylistAdd) { addToPlaylist(tracks.map { it.id }) })
                        } else {
                            add(MenuAction("Rename playlist", Icons.Rounded.Edit) { renaming = true })
                            add(MenuAction("Delete playlist", Icons.Rounded.Delete) { deleting = true })
                        }
                    }
                    OverflowMenu(actions)
                }
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (info.coverUri != null) {
                        CoverArt(info.coverUri, Modifier.size(120.dp), large = true, corner = 14.dp)
                    } else {
                        Box(
                            Modifier.size(120.dp).clip(RoundedCornerShape(14.dp)).background(SurfaceHigh),
                            contentAlignment = Alignment.Center,
                        ) { Icon(info.icon, contentDescription = null, tint = Accent, modifier = Modifier.size(48.dp)) }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            info.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(info.subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { AppGraph.player.playTracks(tracks) },
                        enabled = tracks.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                    OutlinedButton(
                        onClick = { AppGraph.player.playTracks(tracks, shuffle = true) },
                        enabled = tracks.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
            }
        }
        if (info.entries.isEmpty()) {
            item(key = "empty") {
                Text(
                    if (info.playlist != null) "This playlist is empty. Add songs from any song's menu." else "No tracks.",
                    color = TextSecondary,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
        items(info.entries, key = { "${it.position}:${it.track.id}" }) { entry ->
            val playlist = info.playlist
            val extra = if (playlist == null) {
                emptyList()
            } else {
                buildList {
                    if (entry.position > 0) {
                        add(MenuAction("Move up", Icons.Rounded.KeyboardArrowUp) {
                            AppGraph.playlists.move(playlist.id, entry.position, entry.position - 1)
                        })
                    }
                    if (entry.position < playlist.trackIds.lastIndex) {
                        add(MenuAction("Move down", Icons.Rounded.KeyboardArrowDown) {
                            AppGraph.playlists.move(playlist.id, entry.position, entry.position + 1)
                        })
                    }
                    add(MenuAction("Remove from playlist", Icons.Rounded.RemoveCircleOutline) {
                        AppGraph.playlists.removeTrackAt(playlist.id, entry.position)
                        message("Removed \"${entry.track.title}\"")
                    })
                }
            }
            TrackRow(
                track = entry.track,
                isCurrent = entry.track.id == currentTrackId,
                onClick = { AppGraph.player.playTracks(tracks, info.entries.indexOf(entry)) },
                leading = if (screen.kind == CollectionKind.Album) TrackLeading.Number else TrackLeading.Cover,
                extraActions = extra,
            )
        }
    }

    val playlist = info.playlist
    if (renaming && playlist != null) {
        NameDialog("Rename playlist", playlist.name, "Rename", onConfirm = { name ->
            AppGraph.playlists.rename(playlist.id, name)
            renaming = false
        }, onDismiss = { renaming = false })
    }
    if (deleting && playlist != null) {
        ConfirmDialog(
            title = "Delete playlist?",
            body = "\"${playlist.name}\" will be removed. The songs stay on your device.",
            confirmLabel = "Delete",
            onConfirm = {
                deleting = false
                AppGraph.playlists.delete(playlist.id)
                message("Deleted \"${playlist.name}\"")
            },
            onDismiss = { deleting = false },
        )
    }
}
