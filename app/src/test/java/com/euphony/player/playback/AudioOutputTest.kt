package com.euphony.player.playback

import android.media.AudioDeviceInfo
import com.euphony.player.model.EqualizerState
import com.euphony.player.model.OutputDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-device equalizer profiles, output naming and the volume/fade maths. */
class AudioOutputTest {

    private val flat = EqualizerState(enabled = false, bandGainsDb = List(10) { 0f }, presetName = "Flat")
    private val bass = EqualizerState(enabled = true, preampDb = -3f, bandGainsDb = EqMath.presetGains("Bass Boost")!!, presetName = "Bass Boost")

    @Test
    fun aNewDeviceStartsWithTheCurrentSettings() {
        val (profiles, next) = EqMath.switchProfile(emptyMap(), "speaker", bass, "bt:Buds")
        assertEquals(bass, next)
        assertEquals(bass, profiles["speaker"])
        assertEquals(bass, profiles["bt:Buds"])
    }

    @Test
    fun eachDeviceGetsItsOwnSettingsBack() {
        val saved = mapOf("speaker" to flat, "bt:Buds" to bass)
        val (profiles, onBuds) = EqMath.switchProfile(saved, "speaker", flat.copy(preampDb = 2f), "bt:Buds")
        assertEquals(bass, onBuds)
        assertEquals(2f, profiles.getValue("speaker").preampDb, 0f)

        val (_, backOnSpeaker) = EqMath.switchProfile(profiles, "bt:Buds", onBuds, "speaker")
        assertEquals(2f, backOnSpeaker.preampDb, 0f)
        assertEquals(false, backOnSpeaker.enabled)
    }

    @Test
    fun switchingKeepsTheMomentaryFields() {
        val current = flat.copy(supported = false, deviceName = "Phone speaker")
        val (profiles, next) = EqMath.switchProfile(mapOf("wired" to bass), "speaker", current, "wired")
        assertEquals(false, next.supported)
        assertTrue(profiles.values.all { it.supported && it.deviceName == null })
        assertEquals(bass.bandGainsDb, next.bandGainsDb)
    }

    @Test
    fun profilesSurviveEncoding() {
        assertEquals(bass, EqMath.decodeProfile(EqMath.encodeProfile(bass)))
        assertNull(EqMath.decodeProfile("nonsense"))
        assertNull(EqMath.decodeProfile(null))
    }

    @Test
    fun outputsAreNamedAndRanked() {
        assertEquals(OutputDevice("bt:WH-1000XM4", "WH-1000XM4", OutputDevice.Kind.BLUETOOTH), OutputDevices.describe(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "WH-1000XM4"))
        assertEquals("wired", OutputDevices.describe(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "anything").key)
        assertEquals(OutputDevice.SPEAKER, OutputDevices.describe(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "sdk_gphone64"))
        assertEquals("usb:FiiO KA3", OutputDevices.describe(AudioDeviceInfo.TYPE_USB_HEADSET, " FiiO KA3 ").key)
        assertTrue(OutputDevices.rank(AudioDeviceInfo.TYPE_USB_DEVICE) > OutputDevices.rank(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertTrue(OutputDevices.rank(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) > OutputDevices.rank(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
    }

    @Test
    fun volumeIsTheProductOfItsParts() {
        var applied = -1f
        val mixer = VolumeMixer { applied = it }
        mixer.replayGain = 0.5f
        mixer.fade = 0.5f
        assertEquals(0.25f, applied, 0.0001f)
        mixer.bypassed = true
        assertEquals(1f, applied, 0f)
    }

    @Test
    fun crossfadeCurvesKeepPowerSteady() {
        for (t in listOf(0f, 0.25f, 0.5f, 0.9f, 1f)) {
            val a = Fades.equalPowerOut(t)
            val b = Fades.equalPowerIn(t)
            assertEquals(1f, a * a + b * b, 0.0001f)
        }
        assertEquals(1f, Fades.equalPowerOut(-1f), 0f)
        assertEquals(1f, Fades.equalPowerIn(2f), 0f)
    }
}
