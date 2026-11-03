package com.euphony.player.playback

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.euphony.player.model.ReplayGainMode
import kotlin.math.log10
import kotlin.math.pow

/** Gains in dB and peaks as linear sample values (1.0 = full scale); null when the tag is missing. */
data class ReplayGainInfo(
    val trackGainDb: Float? = null,
    val trackPeak: Float? = null,
    val albumGainDb: Float? = null,
    val albumPeak: Float? = null,
) {
    val isEmpty: Boolean get() = trackGainDb == null && albumGainDb == null
}

/** Reading ReplayGain tags and turning them into a playback gain. Pure apart from [fromMetadata]. */
object ReplayGain {

    private const val MIN_DB = -30f
    private const val MAX_DB = 15f

    /** Opus R128 gains are relative to -23 LUFS; ReplayGain's reference is about 5 dB louder. */
    private const val R128_TO_REPLAYGAIN_DB = 5f

    /** Tag name/value pairs, in any case: Vorbis comments (FLAC, Ogg, Opus), ID3 TXXX frames (MP3) and iTunes freeform atoms (M4A). */
    fun parse(tags: List<Pair<String, String>>): ReplayGainInfo {
        val map = tags.associate { (k, v) -> k.trim().uppercase() to v }
        return ReplayGainInfo(
            trackGainDb = map["REPLAYGAIN_TRACK_GAIN"]?.let(::parseGain) ?: map["R128_TRACK_GAIN"]?.let(::parseR128),
            trackPeak = map["REPLAYGAIN_TRACK_PEAK"]?.let(::parsePeak),
            albumGainDb = map["REPLAYGAIN_ALBUM_GAIN"]?.let(::parseGain) ?: map["R128_ALBUM_GAIN"]?.let(::parseR128),
            albumPeak = map["REPLAYGAIN_ALBUM_PEAK"]?.let(::parsePeak),
        )
    }

    /** "-6.20 dB", "+1.5dB", "−3,1 dB" → dB. */
    fun parseGain(raw: String): Float? = number(raw.replace("dB", "", ignoreCase = true))

    fun parsePeak(raw: String): Float? = number(raw)?.takeIf { it > 0f }

    /** R128 tags are Q7.8 fixed point integers in units of 1/256 dB. */
    fun parseR128(raw: String): Float? = raw.trim().toIntOrNull()?.let { it / 256f + R128_TO_REPLAYGAIN_DB }

    private fun number(raw: String): Float? =
        raw.trim().replace('−', '-').replace(',', '.').removePrefix("+").toFloatOrNull()?.takeIf { it.isFinite() }

    /**
     * The gain to play a song at. Album mode falls back to the track gain and the other way round;
     * songs without tags play unchanged. The peak, when known, caps the gain so the song can't clip.
     */
    fun gainDb(info: ReplayGainInfo, mode: ReplayGainMode): Float {
        val (gain, peak) = when (mode) {
            ReplayGainMode.OFF -> return 0f
            ReplayGainMode.TRACK -> if (info.trackGainDb != null) info.trackGainDb to info.trackPeak else info.albumGainDb to info.albumPeak
            ReplayGainMode.ALBUM -> if (info.albumGainDb != null) info.albumGainDb to info.albumPeak else info.trackGainDb to info.trackPeak
        }
        if (gain == null) return 0f
        val capped = if (peak != null && peak > 0f) minOf(gain, -20f * log10(peak)) else gain
        return capped.coerceIn(MIN_DB, MAX_DB)
    }

    /** The part of [gainDb] applied by turning the player's volume down (it can't go above 1). */
    fun volumeFactor(gainDb: Float): Float = 10f.pow(minOf(gainDb, 0f) / 20f)

    /** The part of [gainDb] that needs a boost, applied by the equalizer effect in front of its limiter. */
    fun boostDb(gainDb: Float): Float = maxOf(gainDb, 0f)

    fun fromMetadata(metadata: Metadata?): ReplayGainInfo {
        if (metadata == null) return ReplayGainInfo()
        val tags = (0 until metadata.length()).mapNotNull { i ->
            when (val e = metadata.get(i)) {
                is VorbisComment -> e.key to e.value
                is TextInformationFrame -> e.description?.let { d -> e.values.firstOrNull()?.let { d to it } }
                is InternalFrame -> e.description to e.text
                else -> null
            }
        }
        return parse(tags)
    }

    /** "+3.5 dB" style label for the player screen. */
    fun label(gainDb: Float): String = "%+.1f dB".format(java.util.Locale.US, gainDb)
}
