package com.euphony.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.euphony.player.AppGraph
import com.euphony.player.MainActivity
import com.euphony.player.data.SmartPlaylists
import com.euphony.player.model.MAX_CROSSFADE_SEC
import com.euphony.player.model.ReplayGainMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

object PlaybackCommands {
    const val ACTION_SLEEP_TIMER = "com.euphony.player.SLEEP_TIMER"
    const val EXTRA_MINUTES = "minutes"
    const val EXTRA_SLEEP_ENDS_AT = "sleepEndsAt"
    /** ReplayGain applied to the current song in dB; absent when none. */
    const val EXTRA_REPLAY_GAIN_DB = "replayGainDb"
    const val EXTRA_BIT_PERFECT = "bitPerfect"
}

/** Owns the player. Media3 turns the session into the notification and lock-screen controls. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var mixer: VolumeMixer
    private lateinit var fading: FadingPlayer
    private lateinit var crossfader: Crossfader
    private lateinit var resume: ResumeStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sleepJob: Job? = null
    private var sleepEndsAt = 0L
    private var replayGainDb: Float? = null
    private var ticker: Job? = null

    // Play counting: the current song counts once it has played past its threshold.
    private var countedMediaId: String? = null

    private val settings get() = AppGraph.settings.settings.value
    private val bitPerfect get() = AppGraph.bitPerfect

    private val attributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    override fun onCreate() {
        super.onCreate()
        resume = ResumeStore(this)
        player = buildPlayer(handleAudioFocus = true)
        mixer = VolumeMixer { player.volume = it }
        fading = FadingPlayer(player, mixer, scope) { settings.fadePlayPause && !bitPerfect.isActive }
        crossfader = Crossfader(
            main = player,
            mainMixer = mixer,
            scope = scope,
            buildPlayer = { buildPlayer(handleAudioFocus = false) },
            lengthMs = { if (bitPerfect.isActive) 0L else settings.crossfadeSec.coerceIn(0, MAX_CROSSFADE_SEC) * 1000L },
            replayGainFactor = { tracks -> ReplayGain.volumeFactor(replayGainFor(tracks) ?: 0f) },
        )

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                AppGraph.equalizer.attachToSession(audioSessionId)
            }

            override fun onTracksChanged(tracks: Tracks) = applyReplayGain()

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                countedMediaId = null
                savePosition()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) saveQueue()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    AppGraph.outputs.refresh()
                    startTicker()
                } else {
                    savePosition()
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = savePosition()

            override fun onRepeatModeChanged(repeatMode: Int) = savePosition()
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) =
                onOutputOpened(audioTrackConfig)
        })
        AppGraph.equalizer.attachToSession(player.audioSessionId)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, fading)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()

        watchSettings()
        restoreQueue()
    }

    private fun buildPlayer(handleAudioFocus: Boolean): ExoPlayer {
        // Framework decoders handle FLAC natively and use less power than bundled software ones.
        // Float output keeps 24-bit sources at full resolution; otherwise the decoder hands over 16-bit PCM.
        val renderers = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableAudioFloatOutput(true)
        return ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(attributes, handleAudioFocus)
            .setHandleAudioBecomingNoisy(handleAudioFocus)
            .setWakeMode(if (handleAudioFocus) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NONE)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePosition()
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        savePosition()
        scope.cancel()
        crossfader.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        AppGraph.equalizer.release()
        super.onDestroy()
    }

    /** Reacts to settings the player itself applies. */
    private fun watchSettings() {
        scope.launch {
            AppGraph.settings.settings.map { it.replayGain }.distinctUntilChanged().drop(1).collect { applyReplayGain() }
        }
        scope.launch {
            AppGraph.settings.settings.map { it.crossfadeSec }.distinctUntilChanged().collect { if (it == 0) crossfader.cancel() }
        }
        scope.launch {
            AppGraph.settings.settings.map { it.bitPerfect }.distinctUntilChanged().drop(1).collect { reopenForBitPerfect() }
        }
        scope.launch {
            AppGraph.outputs.usbDac.map { it?.id }.distinctUntilChanged().drop(1).collect { reopenForBitPerfect() }
        }
    }

    // ---- ReplayGain ----

    /** The gain for the playing song under the current setting, or null when off or untagged. */
    private fun replayGainFor(tracks: Tracks): Float? {
        val mode = settings.replayGain
        if (mode == ReplayGainMode.OFF) return null
        val format = selectedAudioFormat(tracks) ?: return null
        val info = ReplayGain.fromMetadata(format.metadata)
        return if (info.isEmpty) null else ReplayGain.gainDb(info, mode)
    }

    private fun applyReplayGain() {
        val gain = replayGainFor(player.currentTracks)
        replayGainDb = gain
        val db = gain ?: 0f
        mixer.replayGain = ReplayGain.volumeFactor(db)
        AppGraph.equalizer.setReplayGainBoost(ReplayGain.boostDb(db))
        publishExtras()
    }

    private fun selectedAudioFormat(tracks: Tracks) = tracks.groups
        .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
        ?.let { group -> (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let(group::getTrackFormat) }

    // ---- Bit-perfect ----

    private var lastOutput: AudioSink.AudioTrackConfig? = null

    private fun onOutputOpened(config: AudioSink.AudioTrackConfig) {
        lastOutput = config
        val changed = bitPerfect.prepare(settings.bitPerfect, config.sampleRate, config.channelConfig, config.encoding)
        applyBitPerfectState()
        // Android applies the mode to outputs opened after it was set: reopen ours.
        if (changed) reopenOutput()
    }

    private fun reopenForBitPerfect() {
        val config = lastOutput
        val changed = if (config == null) {
            bitPerfect.prepare(settings.bitPerfect, 0, 0, 0)
        } else {
            bitPerfect.prepare(settings.bitPerfect, config.sampleRate, config.channelConfig, config.encoding)
        }
        applyBitPerfectState()
        if (changed) reopenOutput()
    }

    private fun applyBitPerfectState() {
        val active = bitPerfect.isActive
        mixer.bypassed = active
        crossfader.setBypassed(active)
        if (active) {
            crossfader.cancel()
            fading.cancelFade()
        }
        AppGraph.equalizer.setBypassed(active)
        publishExtras()
    }

    /** A seek to the same spot makes the player close and reopen its audio output. */
    private fun reopenOutput() {
        if (player.mediaItemCount > 0 && player.playbackState != Player.STATE_IDLE) {
            player.seekTo(player.currentPosition)
        }
    }

    // ---- Resume and play counts ----

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            var sinceSave = 0L
            while (isActive && player.isPlaying) {
                countPlay()
                sinceSave += TICK_MS
                if (sinceSave >= SAVE_EVERY_MS) {
                    sinceSave = 0
                    savePosition()
                }
                delay(TICK_MS)
            }
        }
    }

    private fun countPlay() {
        val item = player.currentMediaItem ?: return
        if (countedMediaId == item.mediaId) return
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        if (player.currentPosition < SmartPlaylists.playThresholdMs(duration)) return
        countedMediaId = item.mediaId
        // Files opened from outside the library have no stable id and aren't counted.
        item.mediaId.toLongOrNull()?.let { AppGraph.usage.recordPlay(it) }
    }

    private fun savePosition() {
        if (!::player.isInitialized || player.mediaItemCount == 0) return
        val item = player.currentMediaItem ?: return
        resume.savePosition(
            ResumePosition(
                index = player.currentMediaItemIndex,
                mediaId = item.mediaId,
                positionMs = player.currentPosition.coerceAtLeast(0),
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
            ),
        )
    }

    private fun saveQueue() {
        // Files opened from a file manager are left out: Android's access to them ends with the app.
        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
            .filter { it.mediaId.toLongOrNull() != null }
            .map { it.toQueueItem() }
        scope.launch(Dispatchers.IO) { resume.saveQueue(items) }
        savePosition()
    }

    /** Puts the last queue back, paused, unless something was already started in the meantime. */
    private fun restoreQueue() {
        scope.launch {
            val items = withContext(Dispatchers.IO) { resume.loadQueue() }
            if (items.isEmpty() || player.mediaItemCount > 0) return@launch
            val position = resume.loadPosition()
            val index = ResumeCodec.startIndex(items, position)
            player.setMediaItems(items.map { it.toMediaItem() }, index, ResumeCodec.startPosition(items, position, index))
            position?.let {
                player.shuffleModeEnabled = it.shuffle
                player.repeatMode = it.repeatMode
            }
            player.prepare()
        }
    }

    private fun savedResumption(): MediaSession.MediaItemsWithStartPosition? {
        val items = resume.loadQueue()
        val position = resume.loadPosition()
        val index = ResumeCodec.startIndex(items, position)
        if (index < 0) return null
        return MediaSession.MediaItemsWithStartPosition(
            items.map { it.toMediaItem() },
            index,
            ResumeCodec.startPosition(items, position, index),
        )
    }

    private fun MediaItem.toQueueItem() = QueueItem(
        mediaId = mediaId,
        uri = (requestMetadata.mediaUri ?: localConfiguration?.uri)?.toString() ?: mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        album = mediaMetadata.albumTitle?.toString().orEmpty(),
    )

    private fun QueueItem.toMediaItem(): MediaItem {
        val parsed = Uri.parse(uri)
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(parsed)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(parsed).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title.ifEmpty { null })
                    .setArtist(artist.ifEmpty { null })
                    .setAlbumTitle(album.ifEmpty { null })
                    .setIsPlayable(true)
                    .build(),
            )
            .build()
    }

    // ---- Sleep timer and session extras ----

    private fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        sleepEndsAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        if (minutes > 0) {
            sleepJob = scope.launch {
                delay(minutes * 60_000L)
                session?.player?.pause()
                sleepEndsAt = 0L
                publishExtras()
            }
        }
        publishExtras()
    }

    private fun publishExtras() {
        session?.setSessionExtras(
            Bundle().apply {
                putLong(PlaybackCommands.EXTRA_SLEEP_ENDS_AT, sleepEndsAt)
                replayGainDb?.let { putFloat(PlaybackCommands.EXTRA_REPLAY_GAIN_DB, it) }
                putBoolean(PlaybackCommands.EXTRA_BIT_PERFECT, bitPerfect.isActive)
            },
        )
    }

    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(PlaybackCommands.ACTION_SLEEP_TIMER, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) = publishExtras()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == PlaybackCommands.ACTION_SLEEP_TIMER) {
                setSleepTimer(args.getInt(PlaybackCommands.EXTRA_MINUTES, 0))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        // Controllers send items with the playable uri in requestMetadata; restore it here.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
                if (uri == null) item else item.buildUpon().setUri(uri).build()
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        // A headset or Bluetooth play button after the app was closed, or the system's media resumption card.
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val saved = runBlocking(Dispatchers.IO) { savedResumption() }
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
            return Futures.immediateFuture(saved)
        }
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val SAVE_EVERY_MS = 10_000L
    }
}
