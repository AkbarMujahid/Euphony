package com.euphony.player.playback

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.util.Log
import com.euphony.player.model.EQ_BAND_FREQUENCIES_HZ
import com.euphony.player.model.EqPreset
import com.euphony.player.model.EqualizerControl
import com.euphony.player.model.EqualizerState
import com.euphony.player.model.OutputDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 10-band equalizer applied to the player's audio session. Uses DynamicsProcessing, which allows
 * arbitrary band counts plus a limiter that keeps boosted bands from clipping. Falls back to the
 * framework Equalizer (usually 5 bands, mapped to the nearest of our 10) if that is unavailable.
 * The same effect carries ReplayGain boosts, and settings can be kept per output device.
 */
class EqualizerController(context: Context) : EqualizerControl {

    private val prefs = context.getSharedPreferences("equalizer", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadState())
    override val state: StateFlow<EqualizerState> = _state.asStateFlow()
    override val presets: List<EqPreset> = EqMath.PRESETS

    private var sessionId = 0
    private var dynamics: DynamicsProcessing? = null
    private var fallback: Equalizer? = null

    private var replayGainBoostDb = 0f
    private var deviceKey: String? = prefs.getString(KEY_DEVICE, null)

    override fun setEnabled(enabled: Boolean) = change { it.copy(enabled = enabled) }

    override fun setBandGain(index: Int, gainDb: Float) = change { s ->
        val gains = s.bandGainsDb.toMutableList().also { it[index] = EqMath.clampGain(gainDb) }
        s.copy(bandGainsDb = gains, presetName = EqMath.matchPreset(gains))
    }

    override fun setPreamp(gainDb: Float) = change { it.copy(preampDb = EqMath.clampGain(gainDb)) }

    override fun applyPreset(name: String) {
        val gains = EqMath.presetGains(name) ?: return
        change { it.copy(bandGainsDb = gains, presetName = name) }
    }

    override fun attachToSession(audioSessionId: Int) {
        // Session 0 is the global output mix; attaching there is deprecated and affects other apps.
        if (audioSessionId <= 0 || audioSessionId == sessionId) return
        releaseEffects()
        sessionId = audioSessionId
        val supported = createDynamics() || createFallback()
        _state.update { it.copy(supported = supported) }
        applyToEffects()
    }

    override fun release() {
        releaseEffects()
        sessionId = 0
    }

    override fun setReplayGainBoost(gainDb: Float): Boolean {
        if (gainDb != replayGainBoostDb) {
            replayGainBoostDb = gainDb
            applyToEffects()
        }
        return gainDb <= 0f || dynamics != null
    }

    override fun setBypassed(bypassed: Boolean) {
        if (bypassed == _state.value.bypassed) return
        _state.update { it.copy(bypassed = bypassed) }
        applyToEffects()
    }

    override fun onOutputChanged(device: OutputDevice, perDevice: Boolean) {
        if (!perDevice) {
            _state.update { it.copy(deviceName = null) }
            return
        }
        if (device.key == deviceKey) {
            _state.update { it.copy(deviceName = device.name) }
            return
        }
        val (profiles, next) = EqMath.switchProfile(loadProfiles(), deviceKey, _state.value, device.key)
        deviceKey = device.key
        prefs.edit().apply {
            profiles.forEach { (key, profile) -> putString(PROFILE_PREFIX + key, EqMath.encodeProfile(profile)) }
            putString(KEY_DEVICE, device.key)
        }.apply()
        _state.value = next.copy(deviceName = device.name)
        saveState(_state.value)
        applyToEffects()
    }

    private fun loadProfiles(): Map<String, EqualizerState> = prefs.all
        .filterKeys { it.startsWith(PROFILE_PREFIX) }
        .mapNotNull { (key, value) -> EqMath.decodeProfile(value as? String)?.let { key.removePrefix(PROFILE_PREFIX) to it } }
        .toMap()

    private fun change(transform: (EqualizerState) -> EqualizerState) {
        _state.update(transform)
        saveState(_state.value)
        applyToEffects()
    }

    private fun createDynamics(): Boolean = try {
        val bands = EQ_BAND_FREQUENCIES_HZ.size
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2, // channels
            true, bands, // pre-EQ
            false, 0, // multi-band compressor
            false, 0, // post-EQ
            true, // limiter
        ).build()
        dynamics = DynamicsProcessing(0, sessionId, config)
        true
    } catch (e: Exception) {
        Log.w(TAG, "DynamicsProcessing unavailable, trying Equalizer", e)
        dynamics = null
        false
    }

    private fun createFallback(): Boolean = try {
        fallback = Equalizer(0, sessionId)
        true
    } catch (e: Exception) {
        Log.w(TAG, "No equalizer effect available on this device", e)
        fallback = null
        false
    }

    private fun applyToEffects() {
        val s = _state.value
        val eqOn = s.enabled && !s.bypassed
        val boost = if (s.bypassed) 0f else replayGainBoostDb
        dynamics?.let { dp ->
            runCatching {
                val cutoffs = EqMath.bandCutoffsHz()
                s.bandGainsDb.forEachIndexed { i, gain ->
                    dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cutoffs[i], if (eqOn) gain else 0f))
                }
                dp.setInputGainAllChannelsTo((if (eqOn) s.preampDb else 0f) + boost)
                // Fast brick-wall limiter just under 0 dBFS so boosts never clip.
                dp.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f))
                dp.enabled = eqOn || boost > 0f
            }.onFailure { Log.w(TAG, "Failed to apply DynamicsProcessing", it) }
        }
        fallback?.let { eq ->
            runCatching {
                val (minMb, maxMb) = eq.bandLevelRange.let { it[0] to it[1] }
                for (band in 0 until eq.numberOfBands) {
                    val centreHz = eq.getCenterFreq(band.toShort()) / 1000f
                    val gainDb = s.bandGainsDb[EqMath.nearestBand(centreHz)] + s.preampDb
                    val mb = (gainDb * 100).toInt().coerceIn(minMb.toInt(), maxMb.toInt())
                    eq.setBandLevel(band.toShort(), mb.toShort())
                }
                eq.enabled = eqOn
            }.onFailure { Log.w(TAG, "Failed to apply Equalizer", it) }
        }
    }

    private fun releaseEffects() {
        runCatching { dynamics?.release() }
        runCatching { fallback?.release() }
        dynamics = null
        fallback = null
    }

    private fun loadState(): EqualizerState {
        val gains = EqMath.decode(prefs.getString(KEY_GAINS, null))
        return EqualizerState(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            preampDb = EqMath.clampGain(prefs.getFloat(KEY_PREAMP, 0f)),
            bandGainsDb = gains,
            presetName = prefs.getString(KEY_PRESET, null) ?: EqMath.matchPreset(gains),
        )
    }

    private fun saveState(s: EqualizerState) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, s.enabled)
            .putFloat(KEY_PREAMP, s.preampDb)
            .putString(KEY_GAINS, EqMath.encode(s.bandGainsDb))
            .putString(KEY_PRESET, s.presetName)
            .apply()
    }

    private companion object {
        const val TAG = "EqualizerController"
        const val KEY_ENABLED = "enabled"
        const val KEY_PREAMP = "preamp"
        const val KEY_GAINS = "gains"
        const val KEY_PRESET = "preset"
        const val KEY_DEVICE = "device"
        const val PROFILE_PREFIX = "profile:"
    }
}
