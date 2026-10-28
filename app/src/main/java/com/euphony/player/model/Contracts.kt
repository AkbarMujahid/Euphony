package com.euphony.player.model

import kotlinx.coroutines.flow.StateFlow

// Shared types and interfaces. Every module codes against this file; keep it stable.

data class Track(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val folderPath: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val bitrate: Int,
    val dateAddedSec: Long,
) {
    val isFlac: Boolean
        get() = mimeType.contains("flac", ignoreCase = true) || fileName.endsWith(".flac", ignoreCase = true)
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val trackIds: List<Long>,
    /** Uri of a track on this album, used to load embedded cover art. */
    val coverTrackUri: String,
)

data class Artist(
    val name: String,
    val albumCount: Int,
    val trackIds: List<Long>,
)

data class Folder(
    val path: String,
    val name: String,
    val trackIds: List<Long>,
)

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<Long>,
)

data class LibrarySnapshot(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val isLoading: Boolean = false,
    /** Files read so far / found during a scan, for a progress bar. Both 0 when not scanning. */
    val scanDone: Int = 0,
    val scanTotal: Int = 0,
) {
    val byId: Map<Long, Track> by lazy { tracks.associateBy { it.id } }

    fun resolve(ids: List<Long>): List<Track> = ids.mapNotNull { byId[it] }
}

enum class RepeatMode { OFF, ALL, ONE }

data class PlaybackUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    /** Human-readable decoded format, e.g. "FLAC · 24-bit · 96 kHz". Null until known. */
    val formatLabel: String? = null,
    /** Wall-clock time (System.currentTimeMillis) when the sleep timer pauses playback, or null. */
    val sleepTimerEndsAtMs: Long? = null,
)

/** The one folder the person gave the app access to; [uri] is a Storage Access Framework tree uri. */
data class MusicFolder(val uri: String, val name: String)

interface MusicLibrary {
    val snapshot: StateFlow<LibrarySnapshot>

    /** The chosen music folder, or null until one is picked (or after its access was revoked). */
    val folder: StateFlow<MusicFolder?>

    /** Keeps access to [treeUri] across restarts and makes it the library; false if access can't be kept. */
    fun setFolder(treeUri: String): Boolean

    /** Re-read the chosen folder. Unchanged files are not re-read. Safe to call repeatedly. */
    suspend fun refresh()
}

interface PlaylistRepository {
    val playlists: StateFlow<List<Playlist>>
    fun create(name: String): Playlist
    fun rename(playlistId: String, name: String)
    fun delete(playlistId: String)
    fun addTracks(playlistId: String, trackIds: List<Long>)
    fun removeTrackAt(playlistId: String, index: Int)
    fun move(playlistId: String, from: Int, to: Int)
}

interface PlayerController {
    val state: StateFlow<PlaybackUiState>

    /** Bind to the playback service. Call from the Activity's onStart. */
    fun connect()

    /** Unbind. Call from the Activity's onStop. Playback continues in the service. */
    fun disconnect()

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false)
    fun playUri(uri: String)
    fun togglePlayPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun setShuffle(enabled: Boolean)
    fun cycleRepeat()
    fun playQueueIndex(index: Int)
    fun removeFromQueue(index: Int)
    fun moveQueueItem(from: Int, to: Int)
    fun playNext(tracks: List<Track>)
    fun addToQueue(tracks: List<Track>)

    /** Pause playback after [minutes]; null cancels the timer. */
    fun setSleepTimer(minutes: Int?)
}

data class EqPreset(val name: String, val gainsDb: List<Float>)

data class EqualizerState(
    val enabled: Boolean = false,
    val preampDb: Float = 0f,
    /** One gain per entry of [EQ_BAND_FREQUENCIES_HZ], each within [EQ_MIN_DB]..[EQ_MAX_DB]. */
    val bandGainsDb: List<Float> = List(EQ_BAND_FREQUENCIES_HZ.size) { 0f },
    val presetName: String = "Flat",
    /** True when the device exposes the effect; false means controls are shown but inert. */
    val supported: Boolean = true,
)

val EQ_BAND_FREQUENCIES_HZ = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
const val EQ_MIN_DB = -12f
const val EQ_MAX_DB = 12f

interface EqualizerControl {
    val state: StateFlow<EqualizerState>
    val presets: List<EqPreset>
    fun setEnabled(enabled: Boolean)
    fun setBandGain(index: Int, gainDb: Float)
    fun setPreamp(gainDb: Float)
    fun applyPreset(name: String)

    /** Called by the playback service whenever the player's audio session id changes. */
    fun attachToSession(audioSessionId: Int)
    fun release()
}
