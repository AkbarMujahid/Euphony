package com.euphony.player.playback

import com.euphony.player.model.EQ_BAND_FREQUENCIES_HZ
import com.euphony.player.model.EQ_MAX_DB
import com.euphony.player.model.EQ_MIN_DB
import com.euphony.player.model.EqPreset
import kotlin.math.abs
import kotlin.math.sqrt

/** Pure equalizer logic, kept free of Android types so it can be unit tested. */
object EqMath {

    const val CUSTOM = "Custom"

    val PRESETS: List<EqPreset> = listOf(
        EqPreset("Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("Bass Boost", listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("Treble Boost", listOf(0f, 0f, 0f, 0f, 0f, 1f, 2f, 4f, 5f, 6f)),
        EqPreset("Vocal", listOf(-2f, -2f, -1f, 1f, 3f, 4f, 3f, 1f, 0f, -1f)),
        EqPreset("Rock", listOf(5f, 4f, 2f, 0f, -1f, -1f, 1f, 3f, 4f, 5f)),
        EqPreset("Pop", listOf(-1f, 0f, 2f, 3f, 4f, 3f, 1f, 0f, -1f, -1f)),
        EqPreset("Jazz", listOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f)),
        EqPreset("Classical", listOf(4f, 3f, 2f, 1f, 0f, 0f, 0f, 2f, 3f, 4f)),
        EqPreset("Electronic", listOf(5f, 4f, 1f, 0f, -2f, 1f, 0f, 2f, 4f, 5f)),
        EqPreset("Loudness", listOf(6f, 4f, 0f, 0f, -1f, 0f, -1f, -2f, 4f, 2f)),
    )

    fun clampGain(db: Float): Float = db.coerceIn(EQ_MIN_DB, EQ_MAX_DB)

    fun presetGains(name: String): List<Float>? = PRESETS.firstOrNull { it.name == name }?.gainsDb

    /** Name of the preset whose gains match exactly, or [CUSTOM]. */
    fun matchPreset(gains: List<Float>): String =
        PRESETS.firstOrNull { preset -> preset.gainsDb.zip(gains).all { (a, b) -> abs(a - b) < 0.05f } }?.name ?: CUSTOM

    /**
     * Upper edge of each band for DynamicsProcessing's pre-EQ, which takes cutoff frequencies
     * rather than centres: the geometric midpoint between neighbouring centres.
     */
    fun bandCutoffsHz(centres: List<Int> = EQ_BAND_FREQUENCIES_HZ): List<Float> =
        centres.mapIndexed { i, f ->
            if (i == centres.lastIndex) 20_000f else sqrt(f.toFloat() * centres[i + 1].toFloat())
        }

    /** Index of the band in [centres] closest to [freqHz] on a log scale. */
    fun nearestBand(freqHz: Float, centres: List<Int> = EQ_BAND_FREQUENCIES_HZ): Int =
        centres.indices.minBy { abs(kotlin.math.ln(centres[it].toFloat()) - kotlin.math.ln(freqHz.coerceAtLeast(1f))) }

    fun encode(gains: List<Float>): String = gains.joinToString(",")

    fun decode(raw: String?, size: Int = EQ_BAND_FREQUENCIES_HZ.size): List<Float> {
        val parsed = raw?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }.orEmpty()
        return if (parsed.size == size) parsed.map(::clampGain) else List(size) { 0f }
    }
}
