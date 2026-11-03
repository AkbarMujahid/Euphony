package com.euphony.player

import android.app.Application
import com.euphony.player.data.LibraryRepository
import com.euphony.player.data.PlaylistStore
import com.euphony.player.data.SettingsStore
import com.euphony.player.data.UsageStore
import com.euphony.player.model.EqualizerControl
import com.euphony.player.model.MusicLibrary
import com.euphony.player.model.PlayerController
import com.euphony.player.model.PlaylistRepository
import com.euphony.player.model.SettingsRepository
import com.euphony.player.model.UsageRepository
import com.euphony.player.playback.AudioOutputMonitor
import com.euphony.player.playback.BitPerfectRouter
import com.euphony.player.playback.EqualizerController
import com.euphony.player.playback.PlayerConnection
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** App-wide singletons. Small enough that a DI framework would be overkill. */
object AppGraph {
    lateinit var library: MusicLibrary
    lateinit var playlists: PlaylistRepository
    lateinit var equalizer: EqualizerControl
    lateinit var player: PlayerController
    lateinit var settings: SettingsRepository
    lateinit var usage: UsageRepository
    lateinit var outputs: AudioOutputMonitor
    lateinit var bitPerfect: BitPerfectRouter
}

class EuphonyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.settings = SettingsStore(this)
        AppGraph.usage = UsageStore(this)
        AppGraph.outputs = AudioOutputMonitor(this)
        AppGraph.bitPerfect = BitPerfectRouter(this, AppGraph.outputs)
        AppGraph.library = LibraryRepository(this)
        AppGraph.playlists = PlaylistStore(this)
        AppGraph.equalizer = EqualizerController(this)
        AppGraph.player = PlayerConnection(this)

        val scope = MainScope()
        // Switch equalizer settings when headphones, a speaker or a DAC take over the sound.
        scope.launch {
            combine(AppGraph.outputs.current, AppGraph.settings.settings.map { it.perDeviceEq }.distinctUntilChanged(), ::Pair)
                .collect { (device, perDevice) -> AppGraph.equalizer.onOutputChanged(device, perDevice) }
        }
        // Keep the bit-perfect status current for the settings screen, even with nothing playing.
        scope.launch {
            combine(AppGraph.outputs.usbDac, AppGraph.settings.settings.map { it.bitPerfect }.distinctUntilChanged(), ::Pair)
                .collect { (_, enabled) -> AppGraph.bitPerfect.onDevicesChanged(enabled) }
        }
    }
}
