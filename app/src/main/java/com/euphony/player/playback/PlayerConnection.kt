package com.euphony.player.playback

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.euphony.player.AppGraph
import com.euphony.player.model.PlaybackUiState
import com.euphony.player.model.PlayerController
import com.euphony.player.model.RepeatMode
import com.euphony.player.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI-side handle on [PlaybackService]. Commands issued before the connection is ready are queued. */
class PlayerConnection(private val context: Context) : PlayerController {

    private val _state = MutableStateFlow(PlaybackUiState())
    override val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var ticker: Job? = null
    private val pending = ArrayDeque<(MediaController) -> Unit>()

    /** Tracks that are not in the scanned library (e.g. opened from a file manager), keyed by media id. */
    private val adHocTracks = HashMap<String, Track>()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
    }

    override fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onExtrasChanged(controller: MediaController, extras: Bundle) = publish()
            })
            .buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            if (future !== f) {
                c.release()
                return@addListener
            }
            controller = c
            c.addListener(playerListener)
            while (pending.isNotEmpty()) pending.removeFirst().invoke(c)
            publish()
        }, ContextCompat.getMainExecutor(context))

        ticker = scope.launch {
            while (isActive) {
                controller?.let { c ->
                    if (c.isPlaying) _state.update { it.copy(positionMs = c.currentPosition.coerceAtLeast(0)) }
                }
                delay(TICK_MS)
            }
        }
    }

    override fun disconnect() {
        ticker?.cancel()
        ticker = null
        controller?.removeListener(playerListener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    override fun playTracks(tracks: List<Track>, startIndex: Int, shuffle: Boolean) {
        if (tracks.isEmpty()) return
        withController { c ->
            c.shuffleModeEnabled = shuffle
            val start = if (shuffle && startIndex == 0) tracks.indices.random() else startIndex
            c.setMediaItems(tracks.map(::toMediaItem), start.coerceIn(tracks.indices), 0L)
            c.prepare()
            c.play()
        }
    }

    override fun playUri(uri: String) {
        scope.launch {
            val parsed = Uri.parse(uri)
            val track = libraryTrackFor(parsed) ?: withContext(Dispatchers.IO) { describeFile(parsed) }
            if (track.id < 0) adHocTracks[uri] = track
            playTracks(listOf(track))
        }
    }

    /** The library's own entry when [uri] is one of its files, so it shows the same title and cover. */
    private fun libraryTrackFor(uri: Uri): Track? {
        val key = uri.toString()
        return AppGraph.library.snapshot.value.tracks.firstOrNull { it.uri == key }
    }

    /** Builds a track for a file outside the library from its display name and embedded tags. Runs off the main thread. */
    private fun describeFile(uri: Uri): Track {
        val resolver = context.contentResolver
        var name: String? = null
        var size = 0L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0)
                    size = if (c.isNull(1)) 0L else c.getLong(1)
                }
            }
        }
        val fileName = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Audio file"
        val retriever = MediaMetadataRetriever()
        try {
            runCatching { retriever.setDataSource(context, uri) }
            fun tag(key: Int) = runCatching { retriever.extractMetadata(key) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            return Track(
                id = -1, uri = uri.toString(),
                title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: fileName.substringBeforeLast('.'),
                artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: "Unknown artist",
                album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "",
                albumId = -1,
                durationMs = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                trackNumber = tag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.toIntOrNull() ?: 0,
                year = tag(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull() ?: 0,
                folderPath = "", fileName = fileName,
                mimeType = resolver.getType(uri) ?: tag(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "",
                sizeBytes = size,
                bitrate = tag(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0,
                dateAddedSec = 0,
            )
        } finally {
            retriever.release()
        }
    }

    override fun togglePlayPause() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    override fun next() = withController { it.seekToNext() }

    override fun previous() = withController { it.seekToPrevious() }

    override fun seekTo(positionMs: Long) = withController {
        it.seekTo(positionMs)
        _state.update { s -> s.copy(positionMs = positionMs) }
    }

    override fun setShuffle(enabled: Boolean) = withController { it.shuffleModeEnabled = enabled }

    override fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun playQueueIndex(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            c.play()
        }
    }

    override fun removeFromQueue(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    override fun moveQueueItem(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    override fun playNext(tracks: List<Track>) = withController { c ->
        if (c.mediaItemCount == 0) {
            playTracks(tracks)
        } else {
            c.addMediaItems(c.currentMediaItemIndex + 1, tracks.map(::toMediaItem))
        }
    }

    override fun addToQueue(tracks: List<Track>) = withController { c ->
        if (c.mediaItemCount == 0) playTracks(tracks) else c.addMediaItems(tracks.map(::toMediaItem))
    }

    override fun setSleepTimer(minutes: Int?) = withController { c ->
        val args = Bundle().apply { putInt(PlaybackCommands.EXTRA_MINUTES, minutes ?: 0) }
        c.sendCustomCommand(SessionCommand(PlaybackCommands.ACTION_SLEEP_TIMER, Bundle.EMPTY), args)
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) {
            action(c)
        } else {
            pending.addLast(action)
            connect()
        }
    }

    private fun publish() {
        val c = controller ?: return
        val queue = (0 until c.mediaItemCount).map { trackFor(c.getMediaItemAt(it)) }
        val index = c.currentMediaItemIndex.takeIf { it in queue.indices } ?: -1
        val current = queue.getOrNull(index)
        val duration = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: current?.durationMs ?: 0L
        val sleepEndsAt = c.sessionExtras.getLong(PlaybackCommands.EXTRA_SLEEP_ENDS_AT, 0L)
        _state.value = PlaybackUiState(
            currentTrack = current,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            shuffle = c.shuffleModeEnabled,
            repeat = when (c.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            },
            queue = queue,
            currentIndex = index,
            formatLabel = currentFormatLabel(c),
            sleepTimerEndsAtMs = sleepEndsAt.takeIf { it > System.currentTimeMillis() },
        )
    }

    private fun currentFormatLabel(c: MediaController): String? {
        for (group in c.currentTracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until group.length) {
                if (group.isTrackSelected(i)) return FormatLabel.describe(group.getTrackFormat(i))
            }
        }
        return null
    }

    private fun trackFor(item: MediaItem): Track {
        item.mediaId.toLongOrNull()?.let { id -> AppGraph.library.snapshot.value.byId[id]?.let { return it } }
        adHocTracks[item.mediaId]?.let { return it }
        val meta = item.mediaMetadata
        return Track(
            id = item.mediaId.toLongOrNull() ?: -1,
            uri = item.requestMetadata.mediaUri?.toString() ?: item.mediaId,
            title = meta.title?.toString() ?: "Unknown",
            artist = meta.artist?.toString() ?: "Unknown artist",
            album = meta.albumTitle?.toString() ?: "",
            albumId = -1, durationMs = 0, trackNumber = meta.trackNumber ?: 0, year = 0,
            folderPath = "", fileName = "", mimeType = "", sizeBytes = 0, bitrate = 0, dateAddedSec = 0,
        )
    }

    private fun toMediaItem(track: Track): MediaItem {
        val uri = Uri.parse(track.uri)
        val mediaId = if (track.id >= 0) track.id.toString() else track.uri
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setTrackNumber(track.trackNumber.takeIf { it > 0 })
                    .setIsPlayable(true)
                    .build(),
            )
            .build()
    }

    private companion object {
        const val TICK_MS = 500L
    }
}
