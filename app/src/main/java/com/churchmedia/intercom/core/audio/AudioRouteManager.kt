package com.churchmedia.intercom.core.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.churchmedia.intercom.core.model.OutputDevice

/**
 * Audio routing for a communication session.
 *
 * Modern devices (API 31+) get explicit device selection through
 * `setCommunicationDevice`, which is what makes USB-C and Bluetooth predictable.
 * Older devices fall back to speakerphone/SCO flags.
 */
class AudioRouteManager(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager: AudioManager =
        appContext.getSystemService(AudioManager::class.java)

    fun enterCommunicationMode() {
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                audioManager.isSpeakerphoneOn = false
            }
        }
    }

    fun exitCommunicationMode() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }

    fun outputs(): List<OutputDevice> {
        val infos: List<AudioDeviceInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }

        return infos.mapNotNull { info ->
            val kind = info.kind() ?: return@mapNotNull null
            OutputDevice(info.id, label(info, kind), kind)
        }.distinctBy { it.id }
    }

    fun currentOutputId(): Int? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.id
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull()?.id
        }
    }.getOrNull()

    fun select(deviceId: Int): Boolean = runCatching {
        val infos: List<AudioDeviceInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }
        val target = infos.firstOrNull { it.id == deviceId } ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.setCommunicationDevice(target)
        } else {
            when (target.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                }
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                -> {
                    @Suppress("DEPRECATION")
                    audioManager.startBluetoothSco()
                    @Suppress("DEPRECATION")
                    audioManager.isBluetoothScoOn = true
                }
                else -> {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = false
                }
            }
        }
        true
    }.getOrDefault(false)

    fun selectSpeaker(): Boolean {
        val speaker = outputs().firstOrNull { it.kind == OutputDevice.Kind.SPEAKER } ?: return false
        return select(speaker.id)
    }

    private fun label(info: AudioDeviceInfo, kind: OutputDevice.Kind): String {
        val kindLabel = when (kind) {
            OutputDevice.Kind.SPEAKER -> "speaker"
            OutputDevice.Kind.EARPIECE -> "earpiece"
            OutputDevice.Kind.WIRED -> "wired"
            OutputDevice.Kind.USB -> "USB"
            OutputDevice.Kind.BLUETOOTH -> "Bluetooth"
            OutputDevice.Kind.OTHER -> "audio"
        }
        val product = runCatching { info.productName?.toString() }.getOrNull()
        return if (product.isNullOrBlank()) {
            kindLabel.replaceFirstChar { it.uppercase() }
        } else {
            "$product · $kindLabel"
        }
    }

    private fun AudioDeviceInfo.kind(): OutputDevice.Kind? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputDevice.Kind.SPEAKER
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> OutputDevice.Kind.EARPIECE
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        -> OutputDevice.Kind.WIRED
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        -> OutputDevice.Kind.USB
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        -> OutputDevice.Kind.BLUETOOTH
        else -> null
    }
}
