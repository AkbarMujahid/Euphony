package com.euphony.player.playback

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.euphony.player.model.ReplayGainMode
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayGainTest {

    private val tagged = ReplayGainInfo(trackGainDb = -7.5f, trackPeak = 0.9f, albumGainDb = -6f, albumPeak = 0.98f)

    @Test
    fun parsesTheUsualGainSpellings() {
        assertEquals(-6.2f, ReplayGain.parseGain("-6.20 dB")!!, 0.001f)
        assertEquals(1.5f, ReplayGain.parseGain("+1.5dB")!!, 0.001f)
        assertEquals(-3.1f, ReplayGain.parseGain("−3,1 dB")!!, 0.001f)
        assertEquals(-4f, ReplayGain.parseGain(" -4 ")!!, 0.001f)
        assertNull(ReplayGain.parseGain("loud"))
        assertNull(ReplayGain.parsePeak("0"))
    }

    @Test
    fun readsTagsInAnyCase() {
        val info = ReplayGain.parse(
            listOf(
                "replaygain_track_gain" to "-8.10 dB",
                "REPLAYGAIN_TRACK_PEAK" to "0.988",
                "ReplayGain_Album_Gain" to "-7.00 dB",
                "REPLAYGAIN_ALBUM_PEAK" to "1.000",
            ),
        )
        assertEquals(ReplayGainInfo(-8.1f, 0.988f, -7f, 1f), info)
    }

    @Test
    fun opusR128GainsAreConvertedToTheReplayGainReference() {
        // -2560 / 256 = -10 dB relative to -23 LUFS, which is -5 dB at ReplayGain's level.
        assertEquals(-5f, ReplayGain.parse(listOf("R128_TRACK_GAIN" to "-2560")).trackGainDb!!, 0.001f)
    }

    @Test
    fun modesPickTheirGainAndFallBackToTheOther() {
        assertEquals(0f, ReplayGain.gainDb(tagged, ReplayGainMode.OFF), 0f)
        assertEquals(-7.5f, ReplayGain.gainDb(tagged, ReplayGainMode.TRACK), 0.001f)
        assertEquals(-6f, ReplayGain.gainDb(tagged, ReplayGainMode.ALBUM), 0.001f)
        assertEquals(-7.5f, ReplayGain.gainDb(tagged.copy(albumGainDb = null), ReplayGainMode.ALBUM), 0.001f)
        assertEquals(-6f, ReplayGain.gainDb(tagged.copy(trackGainDb = null), ReplayGainMode.TRACK), 0.001f)
        assertEquals(0f, ReplayGain.gainDb(ReplayGainInfo(), ReplayGainMode.TRACK), 0f)
    }

    @Test
    fun peakCapsABoostSoTheSongCannotClip() {
        // A peak of 0.5 leaves about 6 dB of headroom, so a +9 dB gain is held to that.
        val gain = ReplayGain.gainDb(ReplayGainInfo(trackGainDb = 9f, trackPeak = 0.5f), ReplayGainMode.TRACK)
        assertEquals(6.02f, gain, 0.01f)
        // Without a peak tag the gain is used as is (the limiter catches any overs).
        assertEquals(4f, ReplayGain.gainDb(ReplayGainInfo(trackGainDb = 4f), ReplayGainMode.TRACK), 0.001f)
    }

    @Test
    fun cutsGoToVolumeAndBoostsToTheEffect() {
        assertEquals(0.5012f, ReplayGain.volumeFactor(-6f), 0.001f)
        assertEquals(0f, ReplayGain.boostDb(-6f), 0f)
        assertEquals(1f, ReplayGain.volumeFactor(3f), 0f)
        assertEquals(3f, ReplayGain.boostDb(3f), 0f)
    }

    @Test
    fun readsVorbisCommentsAndId3Frames() {
        val metadata = Metadata(
            VorbisComment("REPLAYGAIN_TRACK_GAIN", "-6.50 dB"),
            TextInformationFrame("TXXX", "REPLAYGAIN_ALBUM_GAIN", ImmutableList.of("-5.00 dB")),
        )
        val info = ReplayGain.fromMetadata(metadata)
        assertEquals(-6.5f, info.trackGainDb!!, 0.001f)
        assertEquals(-5f, info.albumGainDb!!, 0.001f)
        assertTrue(ReplayGain.fromMetadata(null).isEmpty)
    }
}
