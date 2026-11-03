package com.euphony.player.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.euphony.player.model.BitPerfectStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bit-perfect output to a USB DAC (Android 14+). Android then sends the decoded samples to the DAC
 * untouched: no resampling, no mixing, and no effects or software volume. It has to be set up per
 * format before playback opens its audio output, so the service calls [prepare] for each song.
 */
class BitPerfectRouter(context: Context, private val outputs: AudioOutputMonitor) {

    private val audio = context.getSystemService(AudioManager::class.java)

    private val _status = MutableStateFlow<BitPerfectStatus>(BitPerfectStatus.Off)
    val status: StateFlow<BitPerfectStatus> = _status.asStateFlow()

    val isActive: Boolean get() = _status.value is BitPerfectStatus.Active

    /** The DAC that currently has a bit-perfect mode set by us, to undo it later. */
    private var configured: AudioDeviceInfo? = null
    private var configuredFormat: AudioFormat? = null

    /**
     * Sets up bit-perfect output for the format the player just opened its audio output with
     * ([encoding] and [channelMask] use android.media.AudioFormat values). Returns true when the
     * output mode changed, meaning the player must reopen its audio output for it to apply.
     */
    fun prepare(enabled: Boolean, sampleRate: Int, channelMask: Int, encoding: Int): Boolean {
        if (!enabled) return clear(BitPerfectStatus.Off)
        if (Build.VERSION.SDK_INT < 34) return clear(BitPerfectStatus.NeedsAndroid14)
        val dac = outputs.usbDac.value ?: return clear(BitPerfectStatus.NoUsbDac)
        if (sampleRate <= 0) {
            _status.value = BitPerfectStatus.Ready(dacName(dac))
            return false
        }
        return Api34.apply(this, dac, sampleRate, channelMask, encoding)
    }

    /** Re-checks after a DAC was plugged in or out. */
    fun onDevicesChanged(enabled: Boolean) {
        val dac = outputs.usbDac.value
        when {
            !enabled -> clear(BitPerfectStatus.Off)
            Build.VERSION.SDK_INT < 34 -> clear(BitPerfectStatus.NeedsAndroid14)
            dac == null -> clear(BitPerfectStatus.NoUsbDac)
            configured != null && configured?.id != dac.id -> clear(BitPerfectStatus.Ready(dacName(dac)))
            _status.value !is BitPerfectStatus.Active && _status.value !is BitPerfectStatus.NoMatch ->
                _status.value = BitPerfectStatus.Ready(dacName(dac))
        }
    }

    /** Undo any bit-perfect mode we set; true if one was set. */
    private fun clear(status: BitPerfectStatus): Boolean {
        _status.value = status
        val dac = configured ?: return false
        configured = null
        configuredFormat = null
        if (Build.VERSION.SDK_INT >= 34) {
            runCatching { audio.clearPreferredMixerAttributes(MEDIA, dac) }.onFailure { Log.w(TAG, "Could not clear bit-perfect mode", it) }
        }
        return true
    }

    private fun dacName(dac: AudioDeviceInfo) = dac.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "USB DAC"

    @RequiresApi(34)
    private object Api34 {
        fun apply(r: BitPerfectRouter, dac: AudioDeviceInfo, sampleRate: Int, channelMask: Int, encoding: Int): Boolean {
            val formatLabel = "${encodingName(encoding)} · ${FormatLabel.sampleRate(sampleRate)}"
            val supported = runCatching { r.audio.getSupportedMixerAttributes(dac) }.getOrDefault(emptyList())
            val match = supported.firstOrNull {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                    it.format.sampleRate == sampleRate &&
                    it.format.encoding == encoding &&
                    it.format.channelMask == channelMask
            }
            if (match == null) {
                val changed = r.clear(BitPerfectStatus.NoMatch(r.dacName(dac), formatLabel))
                Log.i(TAG, "No bit-perfect mode for $formatLabel; DAC offers ${supported.map { it.format }}")
                return changed
            }
            if (r.configured?.id == dac.id && r.configuredFormat == match.format) {
                r._status.value = BitPerfectStatus.Active(r.dacName(dac), formatLabel)
                return false
            }
            val ok = runCatching { r.audio.setPreferredMixerAttributes(MEDIA, dac, match) }
                .onFailure { Log.w(TAG, "Could not set bit-perfect mode", it) }
                .getOrDefault(false)
            if (!ok) return r.clear(BitPerfectStatus.NoMatch(r.dacName(dac), formatLabel))
            r.configured = dac
            r.configuredFormat = match.format
            r._status.value = BitPerfectStatus.Active(r.dacName(dac), formatLabel)
            return true
        }

        private fun encodingName(encoding: Int) = when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> "16-bit"
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit"
            AudioFormat.ENCODING_PCM_32BIT -> "32-bit"
            AudioFormat.ENCODING_PCM_FLOAT -> "32-bit float"
            else -> "encoding $encoding"
        }
    }

    private companion object {
        const val TAG = "BitPerfect"
        val MEDIA: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
