package com.churchmedia.intercom.core.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.churchmedia.intercom.core.model.OutputDevice

/**
 * Audio routing for a communication session.
 *
 * Priority order, applied automatically and on every device change:
 *   1. Wired headsets (including USB-C)
 *   2. Bluetooth headsets (SCO preferred for duplex audio)
 *   3. Phone loudspeaker, only when nothing external is connected
 *
 * A user selection overrides the automatic choice until that device disconnects,
 * at which point the manager falls back to the priority order again.
 *
 * Modern devices (API 31+) get explicit device selection through
 * `setCommunicationDevice`, which is what makes USB-C and Bluetooth predictable.
 * Older devices fall back to speakerphone/SCO flags.
 *
 * Device connect/disconnect is observed through [AudioDeviceCallback], so a headset
 * plugged in mid-session is picked up without restarting the call.
 */
class AudioRouteManager(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager: AudioManager =
        appContext.getSystemService(AudioManager::class.java)

    /** Called whenever the active output changes: (deviceId, label). */
    var onActiveDeviceChanged: ((Int?, String?) -> Unit)? = null

    private var userOverride: Boolean = false
    private var userPreferredId: Int? = null
    private var activeDeviceId: Int? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (addedDevices.any { it.isSink }) handleDeviceChange()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.isSink }) handleDeviceChange()
        }
    }

    private var callbackRegistered: Boolean = false

    fun enterCommunicationMode() {
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            registerCallback()
            applyRoute()
        }
    }

    fun exitCommunicationMode() {
        runCatching {
            unregisterCallback()
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
        userOverride = false
        userPreferredId = null
        activeDeviceId = null
        notifyActive()
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

    fun currentOutputId(): Int? = activeDeviceId ?: runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.id
        } else {
            null
        }
    }.getOrNull()

    /**
     * Label of the microphone that is actually capturing.
     *
     * There is no public API to query the routed input of the AudioRecord inside the
     * audio device module, so this derives it from the active output: in communication
     * mode the input follows the output device, and wired/USB headsets without a
     * microphone fall back to the phone mic.
     */
    fun currentInputLabel(): String {
        val activeKind = outputs().firstOrNull { it.id == activeDeviceId }?.kind
        val inputs = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        }.getOrDefault(emptyList())

        val hasWiredMic = inputs.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }

        return when (activeKind) {
            OutputDevice.Kind.BLUETOOTH -> "Bluetooth microphone"
            OutputDevice.Kind.WIRED, OutputDevice.Kind.USB ->
                if (hasWiredMic) "Headset microphone" else "Phone microphone"
            else -> "Phone microphone"
        }
    }

    /** User-initiated selection. Overrides automatic routing until that device disconnects. */
    fun select(deviceId: Int): Boolean {
        val available = outputs()
        if (available.none { it.id == deviceId }) return false
        userOverride = true
        userPreferredId = deviceId
        val ok = selectInternal(deviceId)
        notifyActive()
        return ok
    }

    fun selectSpeaker(): Boolean {
        val speaker = outputs().firstOrNull { it.kind == OutputDevice.Kind.SPEAKER } ?: return false
        return select(speaker.id)
    }

    private fun registerCallback() {
        if (callbackRegistered) return
        runCatching {
            audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
            callbackRegistered = true
        }
    }

    private fun unregisterCallback() {
        if (!callbackRegistered) return
        runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
        callbackRegistered = false
    }

    private fun handleDeviceChange() {
        applyRoute()
    }

    private fun applyRoute() {
        val available = outputs()

        // If the user picked a device and it is still connected, keep it.
        if (userOverride && userPreferredId != null && available.any { it.id == userPreferredId }) {
            if (activeDeviceId != userPreferredId) {
                selectInternal(userPreferredId!!)
            }
            notifyActive()
            return
        }

        // Preferred device gone: drop the override and go automatic.
        if (userOverride && userPreferredId != null && available.none { it.id == userPreferredId }) {
            userOverride = false
            userPreferredId = null
        }

        val targetId = AudioRoutingPolicy.bestRoute(available, null, false)

        if (targetId != null && targetId != activeDeviceId) {
            selectInternal(targetId)
        } else if (targetId == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // Pre-31: no communication device to query; use the speakerphone flag.
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
            activeDeviceId = available.firstOrNull { it.kind == OutputDevice.Kind.SPEAKER }?.id
        }

        notifyActive()
    }

    private fun selectInternal(deviceId: Int): Boolean {
        val infos: List<AudioDeviceInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }
        val target = infos.firstOrNull { it.id == deviceId } ?: return false

        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audioManager.setCommunicationDevice(target) }.getOrDefault(false)
        } else {
            runCatching {
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
                true
            }.getOrDefault(false)
        }

        if (ok) activeDeviceId = deviceId
        return ok
    }

    private fun notifyActive() {
        val label = outputs().firstOrNull { it.id == activeDeviceId }?.label
            ?: if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) "Speaker" else null
        onActiveDeviceChanged?.invoke(activeDeviceId, label)
    }

    private fun label(info: AudioDeviceInfo, kind: OutputDevice.Kind): String {
        val kindLabel = when (kind) {
            OutputDevice.Kind.SPEAKER -> "speaker"
            OutputDevice.Kind.EARPIECE -> "earpiece"
            OutputDevice.Kind.WIRED -> "wired"
            OutputDevice.Kind.USB -> "USB"
            OutputDevice.Kind.BLUETOOTH -> "Bluetooth headset"
            OutputDevice.Kind.BLUETOOTH_A2DP -> "Bluetooth (output only)"
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
        // SCO carries the mic and is the duplex voice path; A2DP is output-only, so it
        // must never be auto-selected for an intercom (see AudioRoutingPolicy).
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> OutputDevice.Kind.BLUETOOTH
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> OutputDevice.Kind.BLUETOOTH_A2DP
        else -> null
    }
}

/**
 * Pure routing policy. Tested independently of Android.
 *
 * Priority: wired > USB > Bluetooth SCO > speaker.
 *
 * `BLUETOOTH_A2DP` is deliberately excluded from the automatic choice: A2DP is
 * output-only (no microphone) and is often connected while not being worn, which
 * silently silences the session. It remains available for manual selection.
 *
 * A user override wins while the preferred device is still connected; otherwise we
 * fall back to the priority order.
 */
object AudioRoutingPolicy {
    fun bestRoute(
        devices: List<OutputDevice>,
        preferredId: Int?,
        userOverride: Boolean,
    ): Int? {
        if (userOverride && preferredId != null && devices.any { it.id == preferredId }) {
            return preferredId
        }
        return devices.firstOrNull { it.kind == OutputDevice.Kind.WIRED }?.id
            ?: devices.firstOrNull { it.kind == OutputDevice.Kind.USB }?.id
            ?: devices.firstOrNull { it.kind == OutputDevice.Kind.BLUETOOTH }?.id
            ?: devices.firstOrNull { it.kind == OutputDevice.Kind.SPEAKER }?.id
        // Note: BLUETOOTH_A2DP intentionally absent — output-only, manual select only.
    }
}
