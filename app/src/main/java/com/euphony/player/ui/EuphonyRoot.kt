package com.euphony.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.euphony.player.AppGraph
import com.euphony.player.ui.theme.Background
import kotlinx.coroutines.launch

@Composable
fun EuphonyRoot(vm: UiViewModel, folderName: String, onChangeFolder: () -> Unit, onRescan: () -> Unit) {
    val library by AppGraph.library.snapshot.collectAsStateWithLifecycle()
    val playback by AppGraph.player.state.collectAsStateWithLifecycle()
    val equalizer by AppGraph.equalizer.state.collectAsStateWithLifecycle()
    val playlists by AppGraph.playlists.playlists.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messenger: (String) -> Unit = remember {
        { text ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(text)
            }
        }
    }
    // Keeps each screen's scroll position while it sits underneath another one.
    val saveableState = rememberSaveableStateHolder()

    BackHandler(enabled = vm.backStack.size > 1) { vm.back() }

    CompositionLocalProvider(
        LocalMessenger provides messenger,
        LocalAddToPlaylist provides { ids -> vm.pendingPlaylistAdd = ids },
    ) {
        Box(Modifier.fillMaxSize().background(Background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize()) {
                val listPadding = PaddingValues(bottom = 8.dp)
                AnimatedContent(
                    targetState = vm.current,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        if (targetState == Screen.NowPlaying) {
                            slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                        } else if (initialState == Screen.NowPlaying) {
                            fadeIn(tween(200)) togetherWith slideOutVertically(tween(220)) { it / 3 } + fadeOut(tween(180))
                        } else {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                        }
                    },
                    label = "screen",
                ) { screen ->
                    saveableState.SaveableStateProvider(screen.toString()) {
                        val currentId = playback.currentTrack?.id
                        when (screen) {
                            Screen.Library -> LibraryScreen(vm, library, playlists, currentId, listPadding, folderName, onChangeFolder, onRescan)
                            is Screen.Collection -> CollectionScreen(vm, screen, library, playlists, currentId, listPadding)
                            Screen.NowPlaying -> NowPlayingScreen(vm, playback)
                            Screen.Queue -> QueueScreen(vm, playback, listPadding)
                            Screen.Equalizer -> EqualizerScreen(vm, equalizer, listPadding)
                        }
                    }
                }
                val screen = vm.current
                if (playback.queue.isNotEmpty() && (screen == Screen.Library || screen is Screen.Collection)) {
                    MiniPlayer(playback, onOpen = { vm.open(Screen.NowPlaying) })
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp))
        }

        vm.pendingPlaylistAdd?.let { ids ->
            AddToPlaylistDialog(ids, playlists) { name ->
                vm.pendingPlaylistAdd = null
                if (name != null) messenger("Added to \"$name\"")
            }
        }
    }
}
