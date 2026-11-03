package com.euphony.player.model

import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) { DARK("Dark"), BLACK("AMOLED black"), LIGHT("Light"), SYSTEM("Follow system") }

enum class ReplayGainMode(val label: String) { OFF("Off"), TRACK("Track"), ALBUM("Album") }

enum class TrackSort(val label: String) { NAME("Name"), DATE_ADDED("Date added"), TRACK_NUMBER("Track number") }

/** Order of the album and folder cards. */
enum class GroupSort(val label: String) { NAME("Name"), DATE_ADDED("Date added") }

enum class LayoutMode { GRID, LIST }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    /** Tint the app with a colour taken from the playing song's cover. */
    val albumArtAccent: Boolean = false,
    val replayGain: ReplayGainMode = ReplayGainMode.OFF,
    /** Short fade-in and fade-out when playback starts and pauses. */
    val fadePlayPause: Boolean = true,
    /** Seconds of overlap between songs; 0 keeps gapless playback. */
    val crossfadeSec: Int = 0,
    /** Android 14+: hand the audio to a USB DAC unchanged. */
    val bitPerfect: Boolean = false,
    /** Remember separate equalizer settings for each output (speaker, headphones, each Bluetooth device). */
    val perDeviceEq: Boolean = true,
    val songSort: TrackSort = TrackSort.NAME,
    /** Order inside albums, artists and folders. */
    val collectionSort: TrackSort = TrackSort.TRACK_NUMBER,
    val albumSort: GroupSort = GroupSort.NAME,
    val folderSort: GroupSort = GroupSort.NAME,
    val albumLayout: LayoutMode = LayoutMode.GRID,
    val folderLayout: LayoutMode = LayoutMode.GRID,
)

const val MAX_CROSSFADE_SEC = 12

interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    fun update(change: (AppSettings) -> AppSettings)
}

/** Play history and favorites, kept on the device only. Keys are track ids. */
data class TrackStats(val playCount: Int, val lastPlayedMs: Long)

data class Usage(
    /** Track id → when it was marked as a favorite. */
    val favorites: Map<Long, Long> = emptyMap(),
    val plays: Map<Long, TrackStats> = emptyMap(),
)

interface UsageRepository {
    val usage: StateFlow<Usage>
    fun toggleFavorite(trackId: Long)
    fun recordPlay(trackId: Long, atMs: Long = System.currentTimeMillis())
}

enum class SmartList(val title: String) {
    FAVORITES("Favorites"),
    RECENTLY_ADDED("Recently added"),
    RECENTLY_PLAYED("Recently played"),
    MOST_PLAYED("Most played"),
}

/** Where the audio is going; [key] identifies it for per-device equalizer settings. */
data class OutputDevice(val key: String, val name: String, val kind: Kind) {
    enum class Kind { SPEAKER, WIRED, BLUETOOTH, USB, OTHER }

    companion object {
        val SPEAKER = OutputDevice("speaker", "Phone speaker", Kind.SPEAKER)
    }
}

sealed interface BitPerfectStatus {
    /** Setting is off. */
    data object Off : BitPerfectStatus
    data object NeedsAndroid14 : BitPerfectStatus
    data object NoUsbDac : BitPerfectStatus
    /** A DAC is connected but has no bit-perfect mode for this song's format. */
    data class NoMatch(val dac: String, val format: String) : BitPerfectStatus
    /** Waiting for a song to play so its format is known. */
    data class Ready(val dac: String) : BitPerfectStatus
    data class Active(val dac: String, val format: String) : BitPerfectStatus
}
