package com.euphony.player.playback

import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Overlaps the end of one song with the start of the next. The queue stays in the main player;
 * a second player plays the start of the next song while the main one fades out. When the main
 * player reaches that song, it jumps to where the second player is and takes over with a short
 * blend, and the second player stops. Both share one audio session, so the equalizer covers both.
 */
@OptIn(UnstableApi::class)
class Crossfader(
    private val main: ExoPlayer,
    private val mainMixer: VolumeMixer,
    private val scope: CoroutineScope,
    private val buildPlayer: () -> ExoPlayer,
    /** Crossfade length in ms, or 0 when off (also off in bit-perfect mode). */
    private val lengthMs: () -> Long,
    /** ReplayGain volume factor for a song's audio format metadata. */
    private val replayGainFactor: (Tracks) -> Float,
) {

    private enum class Phase { IDLE, FADING, HANDOVER, BLENDING }

    private var phase = Phase.IDLE
    private var next: ExoPlayer? = null
    private var nextMixer: VolumeMixer? = null
    private var nextIndex = C.INDEX_UNSET
    private var fadeMs = 0L
    private var ticker: Job? = null

    val isRunning: Boolean get() = phase != Phase.IDLE

    private val mainListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startTicker()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) cancel()
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            when {
                phase == Phase.FADING && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && main.currentMediaItemIndex == nextIndex -> handOver()
                phase != Phase.IDLE -> cancel()
            }
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            // Only while fading: during the handover the seeks are our own.
            if (reason == Player.DISCONTINUITY_REASON_SEEK && phase == Phase.FADING) cancel()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            // The queue was edited mid-fade: the song we're fading into may no longer be next.
            if (phase == Phase.FADING && main.nextMediaItemIndex != nextIndex) cancel()
        }
    }

    init {
        main.addListener(mainListener)
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive && (main.isPlaying || phase != Phase.IDLE)) {
                tick()
                delay(if (phase == Phase.IDLE) IDLE_TICK_MS else FADE_TICK_MS)
            }
        }
    }

    private fun tick() {
        when (phase) {
            Phase.IDLE -> maybeStart()
            Phase.FADING -> fadeStep()
            Phase.HANDOVER -> handOverStep()
            Phase.BLENDING -> Unit
        }
    }

    private fun maybeStart() {
        val length = lengthMs()
        if (length <= 0 || !main.isPlaying || main.repeatMode == Player.REPEAT_MODE_ONE) return
        val duration = main.duration
        if (duration == C.TIME_UNSET || duration < length * 2) return
        val remaining = duration - main.currentPosition
        if (remaining > length || remaining < MIN_FADE_MS) return
        val index = main.nextMediaItemIndex
        if (index == C.INDEX_UNSET) return
        start(index, remaining)
    }

    private fun incomingPlayer(): ExoPlayer = next ?: buildPlayer().also { player ->
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                nextMixer?.replayGain = replayGainFactor(tracks)
            }
        })
        next = player
    }

    private fun start(index: Int, remaining: Long) {
        val player = incomingPlayer()
        val mixer = VolumeMixer { player.volume = it }.also { nextMixer = it }
        mixer.crossfade = 0f
        player.setAudioSessionId(main.audioSessionId)
        player.setMediaItem(main.getMediaItemAt(index))
        player.prepare()
        player.play()
        nextIndex = index
        fadeMs = remaining
        phase = Phase.FADING
        Log.d(TAG, "Crossfading into item $index over ${remaining} ms")
    }

    private fun fadeStep() {
        val player = next ?: return cancel()
        if (lengthMs() <= 0) return cancel()
        val remaining = (main.duration - main.currentPosition).coerceAtLeast(0)
        // Each side follows its own audio clock, so the curves stay in step even if the next song took a moment to start.
        mainMixer.crossfade = Fades.equalPowerOut(1f - remaining.toFloat() / fadeMs)
        nextMixer?.crossfade = Fades.equalPowerIn(player.currentPosition.toFloat() / fadeMs)
    }

    /** The main player just moved on to the next song at its start; jump to where the second player is. */
    private fun handOver() {
        val player = next ?: return cancel()
        mainMixer.crossfade = 0f
        nextMixer?.crossfade = 1f
        phase = Phase.HANDOVER
        handOverSince = 0L
        seekMainTo(player.currentPosition + HANDOVER_LEAD_MS)
    }

    private var handOverSince = 0L

    private fun handOverStep() {
        val player = next ?: return cancel()
        if (handOverSince == 0L) handOverSince = SystemClock.elapsedRealtime()
        val waited = SystemClock.elapsedRealtime() - handOverSince
        if (main.playbackState != Player.STATE_READY || !main.isPlaying) {
            if (waited > HANDOVER_TIMEOUT_MS) finish()
            return
        }
        val drift = main.currentPosition - player.currentPosition
        if (abs(drift) > MAX_DRIFT_MS && waited < HANDOVER_TIMEOUT_MS) {
            seekMainTo(player.currentPosition + HANDOVER_LEAD_MS)
            return
        }
        phase = Phase.BLENDING
        scope.launch {
            val start = SystemClock.elapsedRealtime()
            while (phase == Phase.BLENDING) {
                val t = ((SystemClock.elapsedRealtime() - start).toFloat() / BLEND_MS).coerceIn(0f, 1f)
                mainMixer.crossfade = Fades.equalPowerIn(t)
                nextMixer?.crossfade = Fades.equalPowerOut(t)
                if (t >= 1f) break
                delay(FADE_TICK_MS)
            }
            if (phase == Phase.BLENDING) finish()
        }
    }

    private fun seekMainTo(positionMs: Long) = main.seekTo(positionMs)

    private fun finish() {
        stopNext()
        mainMixer.crossfade = 1f
        phase = Phase.IDLE
    }

    /** Abandons a crossfade (pause, skip, seek, setting changed): the main player carries on at full volume. */
    fun cancel() {
        if (phase == Phase.IDLE) return
        Log.d(TAG, "Crossfade cancelled in $phase")
        finish()
    }

    private fun stopNext() {
        nextMixer = null
        next?.run {
            stop()
            clearMediaItems()
        }
        nextIndex = C.INDEX_UNSET
        handOverSince = 0L
    }

    /** Applies bit-perfect or ReplayGain changes to the incoming player too. */
    fun setBypassed(bypassed: Boolean) {
        nextMixer?.bypassed = bypassed
    }

    fun release() {
        cancel()
        main.removeListener(mainListener)
        ticker?.cancel()
        next?.release()
        next = null
    }

    private companion object {
        const val TAG = "Crossfader"
        const val IDLE_TICK_MS = 250L
        const val FADE_TICK_MS = 30L
        const val MIN_FADE_MS = 500L
        const val HANDOVER_LEAD_MS = 120L
        const val MAX_DRIFT_MS = 60L
        const val HANDOVER_TIMEOUT_MS = 1_500L
        const val BLEND_MS = 150L
    }
}
