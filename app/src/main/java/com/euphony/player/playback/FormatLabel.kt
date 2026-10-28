package com.euphony.player.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes

/** Builds the "FLAC · 24-bit · 96 kHz" style label shown on the player screen. */
object FormatLabel {

    fun describe(
        mimeType: String?,
        sampleRateHz: Int,
        channelCount: Int,
        pcmEncoding: Int,
        bitrate: Int,
    ): String? {
        val codec = codecName(mimeType) ?: return null
        val parts = mutableListOf(codec)
        bitDepth(pcmEncoding)?.let { parts += it }
        if (sampleRateHz > 0) parts += sampleRate(sampleRateHz)
        when {
            channelCount == 1 -> parts += "Mono"
            channelCount > 2 -> parts += "$channelCount ch"
        }
        if (!isLossless(mimeType) && bitrate > 0) parts += "${bitrate / 1000} kbps"
        return parts.joinToString(" · ")
    }

    fun describe(format: Format): String? = describe(
        format.sampleMimeType, format.sampleRate, format.channelCount, format.pcmEncoding, format.bitrate,
    )

    fun codecName(mimeType: String?): String? = when (mimeType) {
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_ALAC -> "ALAC"
        MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_VORBIS -> "Vorbis"
        MimeTypes.AUDIO_RAW, MimeTypes.AUDIO_WAV -> "WAV"
        null -> null
        else -> mimeType.substringAfter('/').uppercase()
    }

    fun isLossless(mimeType: String?): Boolean = mimeType in setOf(
        MimeTypes.AUDIO_FLAC, MimeTypes.AUDIO_ALAC, MimeTypes.AUDIO_RAW, MimeTypes.AUDIO_WAV,
    )

    fun bitDepth(pcmEncoding: Int): String? = when (pcmEncoding) {
        C.ENCODING_PCM_8BIT -> "8-bit"
        C.ENCODING_PCM_16BIT -> "16-bit"
        C.ENCODING_PCM_24BIT -> "24-bit"
        C.ENCODING_PCM_32BIT -> "32-bit"
        C.ENCODING_PCM_FLOAT -> "32-bit float"
        else -> null
    }

    fun sampleRate(hz: Int): String {
        val khz = hz / 1000.0
        return if (hz % 1000 == 0) "${hz / 1000} kHz" else "${"%.1f".format(java.util.Locale.US, khz)} kHz"
    }
}
