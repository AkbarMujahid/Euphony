package com.euphony.player.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.euphony.player.model.OutputDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Naming and ranking of audio outputs. Pure (device types are plain ints), so it is unit tested. */
object OutputDevices {

    private val USB = setOf(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY)
    private val WIRED = setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_LINE_ANALOG)
    private val BLUETOOTH = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_HEARING_AID,
    )
    private val SPEAKER = setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)

    fun isUsb(type: Int) = type in USB

    /**
     * The device for per-device equalizer settings. Bluetooth and USB devices are told apart by name,
     * so two different headphones keep separate settings; all wired headphones share one.
     */
    fun describe(type: Int, productName: String?): OutputDevice {
        val name = productName?.trim()?.takeIf { it.isNotEmpty() }
        return when (type) {
            in USB -> OutputDevice("usb:${name ?: "dac"}", name?.let { "$it (USB)" } ?: "USB audio", OutputDevice.Kind.USB)
            in WIRED -> OutputDevice("wired", "Wired headphones", OutputDevice.Kind.WIRED)
            in BLUETOOTH -> OutputDevice("bt:${name ?: "bluetooth"}", name ?: "Bluetooth", OutputDevice.Kind.BLUETOOTH)
            in SPEAKER -> OutputDevice.SPEAKER
            else -> OutputDevice("other:$type", name ?: "Other output", OutputDevice.Kind.OTHER)
        }
    }

    /** Android sends media to the most recently connected accessory; this ranking matches that for one at a time. */
    fun rank(type: Int): Int = when (type) {
        in USB -> 4
        in WIRED -> 3
        in BLUETOOTH -> 2
        in SPEAKER -> 1
        else -> 0
    }
}

/** Watches which output media plays through, and whether a USB DAC is plugged in. */
class AudioOutputMonitor(context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)

    private val _current = MutableStateFlow(OutputDevice.SPEAKER)
    val current: StateFlow<OutputDevice> = _current.asStateFlow()

    private val _usbDac = MutableStateFlow<AudioDeviceInfo?>(null)
    val usbDac: StateFlow<AudioDeviceInfo?> = _usbDac.asStateFlow()

    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    init {
        audio.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
        }, Handler(Looper.getMainLooper()))
        refresh()
    }

    /** Re-reads the routing; also worth calling when playback starts, as routing can change without a device event. */
    fun refresh() {
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        _usbDac.value = outputs.firstOrNull { OutputDevices.isUsb(it.type) }
        val routed = if (Build.VERSION.SDK_INT >= 33) {
            runCatching { audio.getAudioDevicesForAttributes(mediaAttributes).firstOrNull() }.getOrNull()
        } else {
            null
        }
        val device = routed ?: outputs.maxByOrNull { OutputDevices.rank(it.type) }
        _current.value = device?.let { OutputDevices.describe(it.type, it.productName?.toString()) } ?: OutputDevice.SPEAKER
    }
}
