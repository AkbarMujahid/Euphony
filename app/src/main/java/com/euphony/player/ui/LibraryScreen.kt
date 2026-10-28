package com.euphony.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.euphony.player.AppGraph
import com.euphony.player.model.Album
import com.euphony.player.model.Artist
import com.euphony.player.model.Folder
import com.euphony.player.model.LibrarySnapshot
import com.euphony.player.model.Playlist
import com.euphony.player.model.Track
import com.euphony.player.ui.theme.Accent
import com.euphony.player.ui.theme.Background
import com.euphony.player.ui.theme.SurfaceHigh
import com.euphony.player.ui.theme.TextSecondary

@Composable
fun LibraryScreen(
    vm: UiViewModel,
    library: LibrarySnapshot,
    playlists: List<Playlist>,
    currentTrackId: Long?,
    contentPadding: PaddingValues,
    folderName: String,
    onChangeFolder: () -> Unit,
    onRescan: () -> Unit,
) {
    BackHandler(enabled = vm.searchOpen) {
        vm.searchOpen = false
        vm.query = ""
    }
    val q = vm.query.trim()
    Column(Modifier.fillMaxSize()) {
        LibraryTopBar(vm, folderName, library, onChangeFolder, onRescan)
        PrimaryScrollableTabRow(
            selectedTabIndex = vm.tab.ordinal,
            containerColor = Background,
            edgePadding = 8.dp,
        ) {
            LibraryTab.entries.forEach { tab ->
                Tab(
                    selected = vm.tab == tab,
                    onClick = { vm.tab = tab },
                    text = { Text(tab.label) },
                    selectedContentColor = Accent,
                    unselectedContentColor = TextSecondary,
                )
            }
        }
        Box(Modifier.weight(1f)) {
            when {
                library.isLoading && library.tracks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Accent)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (library.scanTotal > 0) "Reading songs… ${library.scanDone} of ${library.scanTotal}" else "Looking in $folderName…",
                            color = TextSecondary,
                        )
                    }
                }
                library.tracks.isEmpty() && vm.tab != LibraryTab.Playlists -> Column(
                    Modifier.fillMaxSize().padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    EmptyState(
                        Icons.Rounded.LibraryMusic,
                        "No songs in $folderName",
                        "Copy audio files (FLAC, MP3, OGG, …) into this folder or its subfolders, then rescan.",
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    FilledTonalButton(onClick = onRescan) { Text("Rescan") }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onChangeFolder) { Text("Choose another folder") }
                }
                else -> when (vm.tab) {
                    LibraryTab.Songs -> SongsTab(vm, library.tracks.filter { it.matches(q) }, currentTrackId, contentPadding)
                    LibraryTab.Albums -> AlbumsTab(vm, library.albums.filter { it.matches(q) }, contentPadding)
                    LibraryTab.Artists -> ArtistsTab(vm, library.artists.filter { it.name.contains(q, true) }, contentPadding)
                    LibraryTab.Folders -> FoldersTab(vm, library, library.folders.filter { it.path.contains(q, true) }, contentPadding)
                    LibraryTab.Playlists -> PlaylistsTab(vm, playlists.filter { it.name.contains(q, true) }, contentPadding)
                }
            }
        }
    }
}

private fun Track.matches(q: String) =
    q.isEmpty() || title.contains(q, true) || artist.contains(q, true) || album.contains(q, true) || fileName.contains(q, true)

private fun Album.matches(q: String) = q.isEmpty() || title.contains(q, true) || artist.contains(q, true)

@Composable
private fun LibraryTopBar(
    vm: UiViewModel,
    folderName: String,
    library: LibrarySnapshot,
    onChangeFolder: () -> Unit,
    onRescan: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (vm.searchOpen) {
            val focus = remember { FocusRequester() }
            TextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                placeholder = { Text("Search ${vm.tab.label.lowercase()}") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceHigh,
                    unfocusedContainerColor = SurfaceHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            IconButton(onClick = {
                vm.searchOpen = false
                vm.query = ""
            }) { Icon(Icons.Rounded.Close, contentDescription = "Close search") }
            LaunchedEffect(Unit) { focus.requestFocus() }
        } else {
            Column(Modifier.weight(1f)) {
                Text("Euphony", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (library.tracks.isEmpty()) folderName else "$folderName · ${pluralTracks(library.tracks.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { vm.searchOpen = true }) { Icon(Icons.Rounded.Search, contentDescription = "Search") }
            OverflowMenu(
                listOf(
                    MenuAction("Rescan folder", Icons.Rounded.Refresh, onRescan),
                    MenuAction("Change folder", Icons.Rounded.FolderOpen, onChangeFolder),
                ),
                contentDescription = "Library options",
            )
        }
    }
    // A thin bar while a rescan runs in the background over an already-loaded library.
    if (library.isLoading && library.tracks.isNotEmpty()) {
        if (library.scanTotal > 0) {
            LinearProgressIndicator(
                progress = { library.scanDone.toFloat() / library.scanTotal },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Accent,
                trackColor = SurfaceHigh,
                drawStopIndicator = {},
            )
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Accent, trackColor = SurfaceHigh)
        }
    }
}

@Composable
private fun NoMatches(vm: UiViewModel) =
    EmptyState(Icons.Rounded.SearchOff, "Nothing matches \"${vm.query.trim()}\"")

@Composable
private fun SongsTab(vm: UiViewModel, tracks: List<Track>, currentTrackId: Long?, contentPadding: PaddingValues) {
    if (tracks.isEmpty()) return NoMatches(vm)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "header") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(pluralTracks(tracks.size), color = TextSecondary, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { AppGraph.player.playTracks(tracks, shuffle = true) }) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle all")
                }
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
            TrackRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                onClick = { AppGraph.player.playTracks(tracks, index) },
            )
        }
    }
}

@Composable
private fun AlbumsTab(vm: UiViewModel, albums: List<Album>, contentPadding: PaddingValues) {
    if (albums.isEmpty()) return NoMatches(vm)
    CoverGrid(contentPadding) {
        items(albums, key = { it.id }) { album ->
            CoverCard(
                coverUri = album.coverTrackUri,
                title = album.title,
                subtitle = album.artist,
                onClick = { vm.open(Screen.Collection(CollectionKind.Album, album.id.toString())) },
            )
        }
    }
}

@Composable
private fun CoverGrid(contentPadding: PaddingValues, content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun CoverCard(coverUri: String?, title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        CoverArt(coverUri, Modifier.fillMaxWidth().aspectRatio(1f), corner = 10.dp)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ArtistsTab(vm: UiViewModel, artists: List<Artist>, contentPadding: PaddingValues) {
    if (artists.isEmpty()) return NoMatches(vm)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        items(artists, key = { it.name }) { artist ->
            ListRow(
                title = artist.name,
                subtitle = "${if (artist.albumCount == 1) "1 album" else "${artist.albumCount} albums"} · ${pluralTracks(artist.trackIds.size)}",
                onClick = { vm.open(Screen.Collection(CollectionKind.Artist, artist.name)) },
                leading = { Monogram(artist.name) },
            )
        }
    }
}

@Composable
private fun FoldersTab(vm: UiViewModel, library: LibrarySnapshot, folders: List<Folder>, contentPadding: PaddingValues) {
    if (folders.isEmpty()) return NoMatches(vm)
    CoverGrid(contentPadding) {
        items(folders, key = { it.path }) { folder ->
            // Nested folders also show where they sit, e.g. "Punjabi · 12 tracks" for Music/Punjabi/Karan Aujla.
            val parents = folder.path.trimEnd('/').split('/').drop(1).dropLast(1)
            val count = pluralTracks(folder.trackIds.size)
            CoverCard(
                coverUri = folder.trackIds.firstNotNullOfOrNull { library.byId[it]?.uri },
                title = folder.name,
                subtitle = if (parents.isEmpty()) count else parents.joinToString(" / ") + " · " + count,
                onClick = { vm.open(Screen.Collection(CollectionKind.Folder, folder.path)) },
            )
        }
    }
}

@Composable
private fun PlaylistsTab(vm: UiViewModel, playlists: List<Playlist>, contentPadding: PaddingValues) {
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }
    val message = LocalMessenger.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "new") {
            ListRow(
                title = "New playlist",
                subtitle = null,
                onClick = { creating = true },
                leading = { IconTile(Icons.Rounded.Add, tint = Accent) },
                titleColor = Accent,
            )
        }
        items(playlists, key = { it.id }) { playlist ->
            ListRow(
                title = playlist.name,
                subtitle = pluralTracks(playlist.trackIds.size),
                onClick = { vm.open(Screen.Collection(CollectionKind.Playlist, playlist.id)) },
                leading = { IconTile(Icons.AutoMirrored.Rounded.PlaylistPlay) },
                trailing = {
                    OverflowMenu(
                        listOf(
                            MenuAction("Rename", Icons.Rounded.Edit) { renaming = playlist },
                            MenuAction("Delete", Icons.Rounded.Delete) { deleting = playlist },
                        ),
                        contentDescription = "More options for ${playlist.name}",
                    )
                },
            )
        }
        if (playlists.isEmpty() && vm.query.isBlank()) {
            item(key = "hint") {
                Text(
                    "Create a playlist here, or use \"Add to playlist\" from any song's menu.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
    }

    if (creating) {
        NameDialog("New playlist", "", "Create", onConfirm = { name ->
            AppGraph.playlists.create(name)
            creating = false
        }, onDismiss = { creating = false })
    }
    renaming?.let { playlist ->
        NameDialog("Rename playlist", playlist.name, "Rename", onConfirm = { name ->
            AppGraph.playlists.rename(playlist.id, name)
            renaming = null
        }, onDismiss = { renaming = null })
    }
    deleting?.let { playlist ->
        ConfirmDialog(
            title = "Delete playlist?",
            body = "\"${playlist.name}\" will be removed. The songs stay on your device.",
            confirmLabel = "Delete",
            onConfirm = {
                AppGraph.playlists.delete(playlist.id)
                deleting = null
                message("Deleted \"${playlist.name}\"")
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
fun ListRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {},
    titleColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

@Composable
private fun Monogram(name: String) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(SurfaceHigh), contentAlignment = Alignment.Center) {
        Text(
            name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = Accent,
        )
    }
}

@Composable
private fun IconTile(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = TextSecondary) {
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceHigh), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}
