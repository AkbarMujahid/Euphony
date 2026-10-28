package com.euphony.player.playback

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatLabelTest {

    @Test
    fun hiResFlacShowsDepthAndRateButNoBitrate() {
        assertEquals(
            "FLAC · 24-bit · 96 kHz",
            FormatLabel.describe(MimeTypes.AUDIO_FLAC, 96_000, 2, C.ENCODING_PCM_24BIT, 4_600_000),
        )
    }

    @Test
    fun cdQualityFlacUsesDecimalRate() {
        assertEquals(
            "FLAC · 16-bit · 44.1 kHz",
            FormatLabel.describe(MimeTypes.AUDIO_FLAC, 44_100, 2, C.ENCODING_PCM_16BIT, 0),
        )
    }

    @Test
    fun lossyFormatsShowBitrateAndMono() {
        assertEquals(
            "MP3 · 44.1 kHz · Mono · 128 kbps",
            FormatLabel.describe(MimeTypes.AUDIO_MPEG, 44_100, 1, Format_NO_VALUE, 128_000),
        )
    }

    @Test
    fun unknownMimeReturnsNull() {
        assertNull(FormatLabel.describe(null, 44_100, 2, C.ENCODING_PCM_16BIT, 0))
    }

    private companion object {
        const val Format_NO_VALUE = -1
    }
}
