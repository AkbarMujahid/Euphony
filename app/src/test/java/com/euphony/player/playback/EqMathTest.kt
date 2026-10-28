package com.euphony.player.playback

import com.euphony.player.model.EQ_BAND_FREQUENCIES_HZ
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqMathTest {

    @Test
    fun everyPresetHasOneGainPerBandWithinRange() {
        EqMath.PRESETS.forEach { preset ->
            assertEquals(preset.name, EQ_BAND_FREQUENCIES_HZ.size, preset.gainsDb.size)
            assertTrue(preset.name, preset.gainsDb.all { it in -12f..12f })
        }
    }

    @Test
    fun clampKeepsGainInsideLimits() {
        assertEquals(12f, EqMath.clampGain(40f))
        assertEquals(-12f, EqMath.clampGain(-40f))
        assertEquals(3.5f, EqMath.clampGain(3.5f))
    }

    @Test
    fun matchPresetRecognisesPresetsAndCustomCurves() {
        assertEquals("Rock", EqMath.matchPreset(EqMath.presetGains("Rock")!!))
        assertEquals(EqMath.CUSTOM, EqMath.matchPreset(List(10) { 1.25f }))
        assertNull(EqMath.presetGains("No such preset"))
    }

    @Test
    fun cutoffsRiseAndSitBetweenNeighbouringCentres() {
        val cutoffs = EqMath.bandCutoffsHz()
        assertEquals(EQ_BAND_FREQUENCIES_HZ.size, cutoffs.size)
        for (i in 0 until cutoffs.lastIndex) {
            assertTrue(cutoffs[i] > EQ_BAND_FREQUENCIES_HZ[i])
            assertTrue(cutoffs[i] < EQ_BAND_FREQUENCIES_HZ[i + 1])
        }
        assertEquals(20_000f, cutoffs.last())
    }

    @Test
    fun nearestBandMapsFrameworkCentresOntoOurBands() {
        assertEquals(0, EqMath.nearestBand(60f / 2))
        assertEquals(1, EqMath.nearestBand(60f))
        assertEquals(5, EqMath.nearestBand(910f))
        assertEquals(9, EqMath.nearestBand(14_000f))
    }

    @Test
    fun encodeDecodeRoundTripsAndRejectsBadInput() {
        val gains = EqMath.presetGains("Vocal")!!
        assertEquals(gains, EqMath.decode(EqMath.encode(gains)))
        assertEquals(List(10) { 0f }, EqMath.decode("1,2,3"))
        assertEquals(List(10) { 0f }, EqMath.decode(null))
        assertEquals(12f, EqMath.decode(List(10) { "99" }.joinToString(","))[0])
    }
}
