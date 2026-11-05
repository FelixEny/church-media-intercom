package com.churchmedia.intercom.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaRecorder
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Owns the single libwebrtc factory and audio device module for the whole process.
 *
 * Exactly one microphone capture exists; every outbound connection piggybacks on the
 * same [AudioSource] with its own local [AudioTrack] so recipient routing stays
 * independent per connection.
 */
class WebRtcCore(private val context: Context) {

    private val lock = Any()

    private var factory: PeerConnectionFactory? = null
    private var adm: JavaAudioDeviceModule? = null
    private var source: AudioSource? = null

    /** Level of the captured microphone, 0..1. Throttled by this class. */
    @Volatile
    var onMicLevel: ((Float) -> Unit)? = null

    @Volatile
    private var lastMicLevelAt: Long = 0L

    /** True when the platform reports a hardware noise suppressor we can toggle live. */
    var hardwareNoiseSuppressionSupported: Boolean = false
        private set

    fun ensureInitialized() {
        synchronized(lock) {
            if (factory != null) return

            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions(),
            )

            val hasHardwareAec = JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported()
            hardwareNoiseSuppressionSupported = JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()

            val module = JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(hasHardwareAec)
                // Hardware NS stays off by default: the app exposes its own toggle and
                // must not stack two suppression stages. See docs/ARCHITECTURE.md.
                .setUseHardwareNoiseSuppressor(false)
                .setUseLowLatency(true)
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setSamplesReadyCallback(
                    object : JavaAudioDeviceModule.SamplesReadyCallback {
                        override fun onWebRtcAudioRecordSamplesReady(samples: JavaAudioDeviceModule.AudioSamples) {
                            val now = System.currentTimeMillis()
                            if (now - lastMicLevelAt < 120) return
                            lastMicLevelAt = now
                            onMicLevel?.invoke(AudioLevels.pcm16(samples.data))
                        }
                    },
                )
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .createAudioDeviceModule()

            adm = module
            factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(module)
                .createPeerConnectionFactory()
            source = factory!!.createAudioSource(MediaConstraints())
        }
    }

    fun factory(): PeerConnectionFactory {
        ensureInitialized()
        return factory!!
    }

    fun audioSource(): AudioSource {
        ensureInitialized()
        return source!!
    }

    fun createLocalTrack(name: String): AudioTrack = factory().createAudioTrack(name, audioSource())

    /**
     * LAN-only configuration: no ICE servers at all, so gathering is host candidates
     * only and never waits on an unreachable STUN server.
     */
    fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection {
        val config = PeerConnection.RTCConfiguration(emptyList<PeerConnection.IceServer>()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        return factory().createPeerConnection(config, observer)!!
    }

    /**
     * @return true when the platform actually applied the change. When false the
     * caller should fall back to restarting capture with different processing options.
     */
    fun setNoiseSuppression(enabled: Boolean): Boolean {
        val module = adm ?: return false
        return runCatching { module.setNoiseSuppressorEnabled(enabled) }.getOrDefault(false)
    }

    /** Human-readable summary of what the platform really enabled. */
    fun describeProcessing(): String {
        val module = adm ?: return "Not initialised"
        return runCatching {
            val state = module.getPlatformAudioProcessingState() ?: return "Unknown"
            val ns = state.noiseSuppression
            val aec = state.echoCancellation
            buildString {
                append("AEC: ")
                append(if (aec.isActive == true) "hardware" else "WebRTC software")
                append(" · NS: ")
                append(if (ns.isActive == true) "hardware" else "WebRTC software")
            }
        }.getOrDefault("Unknown")
    }

    fun setMicrophoneMute(muted: Boolean) {
        runCatching { adm?.setMicrophoneMute(muted) }
    }

    fun setSpeakerMute(muted: Boolean) {
        runCatching { adm?.setSpeakerMute(muted) }
    }

    fun release() {
        synchronized(lock) {
            runCatching { source?.dispose() }
            runCatching { factory?.dispose() }
            runCatching { adm?.release() }
            source = null
            factory = null
            adm = null
        }
    }
}
