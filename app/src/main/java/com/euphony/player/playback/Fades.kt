package com.euphony.player.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Volume curves for fades and crossfades. Pure, so they are unit tested. */
object Fades {

    const val FADE_IN_MS = 300L
    const val FADE_OUT_MS = 200L

    /** Equal-power curves keep the loudness steady while two songs overlap; [t] runs 0 → 1. */
    fun equalPowerIn(t: Float): Float = sin(t.coerceIn(0f, 1f) * PI.toFloat() / 2)

    fun equalPowerOut(t: Float): Float = cos(t.coerceIn(0f, 1f) * PI.toFloat() / 2)
}

/**
 * A player's volume is the product of independent parts: ReplayGain, the play/pause fade and the
 * crossfade. Each part sets its own factor; [apply] receives the combined value.
 * In bit-perfect mode the volume isn't applied by Android, so everything stays at 1.
 */
class VolumeMixer(private val apply: (Float) -> Unit) {

    var replayGain = 1f
        set(value) { field = value; push() }
    var fade = 1f
        set(value) { field = value; push() }
    var crossfade = 1f
        set(value) { field = value; push() }
    var bypassed = false
        set(value) { field = value; push() }

    val volume: Float get() = if (bypassed) 1f else (replayGain * fade * crossfade).coerceIn(0f, 1f)

    fun push() = apply(volume)
}
