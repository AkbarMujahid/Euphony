package com.euphony.player

import android.app.Application
import com.euphony.player.data.LibraryRepository
import com.euphony.player.data.PlaylistStore
import com.euphony.player.model.EqualizerControl
import com.euphony.player.model.MusicLibrary
import com.euphony.player.model.PlayerController
import com.euphony.player.model.PlaylistRepository
import com.euphony.player.playback.EqualizerController
import com.euphony.player.playback.PlayerConnection

/** App-wide singletons. Small enough that a DI framework would be overkill. */
object AppGraph {
    lateinit var library: MusicLibrary
    lateinit var playlists: PlaylistRepository
    lateinit var equalizer: EqualizerControl
    lateinit var player: PlayerController
}

class EuphonyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.library = LibraryRepository(this)
        AppGraph.playlists = PlaylistStore(this)
        AppGraph.equalizer = EqualizerController(this)
        AppGraph.player = PlayerConnection(this)
    }
}
