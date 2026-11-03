package com.euphony.player.playback

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The player the media session sees. Play and pause from the app, notification, lock screen or
 * headset fade in and out briefly instead of starting and stopping abruptly. Pauses Android makes
 * itself (headphones unplugged, a call) stay instant.
 */
@OptIn(UnstableApi::class)
class FadingPlayer(
    private val exo: ExoPlayer,
    private val mixer: VolumeMixer,
    private val scope: CoroutineScope,
    private val fadesEnabled: () -> Boolean,
) : ForwardingPlayer(exo) {

    private var fadeJob: Job? = null

    override fun play() {
        fadeJob?.cancel()
        if (!fadesEnabled()) {
            mixer.fade = 1f
            super.play()
            return
        }
        // Resuming during a fade-out picks up from the current level instead of jumping to silence.
        if (!exo.playWhenReady) mixer.fade = 0f
        super.play()
        fadeJob = scope.launch { ramp(to = 1f, durationMs = Fades.FADE_IN_MS) }
    }

    override fun pause() {
        fadeJob?.cancel()
        if (!fadesEnabled() || !exo.isPlaying) {
            super.pause()
            mixer.fade = 1f
            return
        }
        fadeJob = scope.launch {
            ramp(to = 0f, durationMs = Fades.FADE_OUT_MS)
            exo.pause()
            mixer.fade = 1f
        }
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) play() else pause()
    }

    /** Stops a fade in progress and restores full volume, e.g. when the player is reset. */
    fun cancelFade() {
        fadeJob?.cancel()
        fadeJob = null
        mixer.fade = 1f
    }

    private suspend fun ramp(to: Float, durationMs: Long) {
        val from = mixer.fade
        val start = SystemClock.elapsedRealtime()
        while (true) {
            val t = ((SystemClock.elapsedRealtime() - start).toFloat() / durationMs).coerceIn(0f, 1f)
            mixer.fade = from + (to - from) * t
            if (t >= 1f) break
            delay(STEP_MS)
        }
    }

    private companion object {
        const val STEP_MS = 16L
    }
}
