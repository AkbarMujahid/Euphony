package com.euphony.player.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

enum class LibraryTab(val label: String) { Folders("Folders"), Songs("Songs"), Albums("Albums"), Artists("Artists"), Playlists("Playlists") }

enum class CollectionKind { Album, Artist, Folder, Playlist }

sealed interface Screen {
    data object Library : Screen
    /** A group of tracks; [key] is the album id, artist name, folder path or playlist id. */
    data class Collection(val kind: CollectionKind, val key: String) : Screen
    data object NowPlaying : Screen
    data object Queue : Screen
    data object Equalizer : Screen
}

/** UI-only state that should survive rotation: the back stack, the library tab and the search text. */
class UiViewModel : ViewModel() {
    val backStack = mutableStateListOf<Screen>(Screen.Library)
    var tab by mutableStateOf(LibraryTab.Folders)
    var query by mutableStateOf("")
    var searchOpen by mutableStateOf(false)

    /** Track ids waiting for the "Add to playlist" picker, or null when it is closed. */
    var pendingPlaylistAdd by mutableStateOf<List<Long>?>(null)

    /** When the music folder was last scanned (elapsedRealtime), so returning to the app rescans only now and then. */
    var lastScanAt = 0L

    val current: Screen get() = backStack.last()

    fun open(screen: Screen) {
        if (current == screen) return
        // Re-opening a screen already on the stack goes back to it instead of stacking a copy.
        val existing = backStack.indexOf(screen)
        if (existing >= 0) {
            while (backStack.size > existing + 1) backStack.removeAt(backStack.lastIndex)
        } else {
            backStack.add(screen)
        }
    }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }
}
