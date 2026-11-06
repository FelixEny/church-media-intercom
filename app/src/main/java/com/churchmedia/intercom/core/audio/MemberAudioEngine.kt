package com.churchmedia.intercom.core.audio

import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.AudioTrack
import org.webrtc.AudioTrackSink
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RtpSender
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription

/**
 * Member-side audio: exactly one peer connection back to the director.
 *
 * The microphone track is created up front but attached to the transceiver created
 * by the director's offer, so the SDP never grows a second audio section.
 *
 * ICE candidates that arrive before the remote description is set are queued and
 * drained afterwards; the answer is only sent once setLocalDescription succeeds.
 */
class MemberAudioEngine(
    private val core: WebRtcCore,
    private val onAnswer: (sdp: String) -> Unit,
    private val onIce: (mid: String?, mLine: Int, candidate: String) -> Unit,
    private val onPeerState: (detail: String) -> Unit,
    private val onRemoteLevel: (Float) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    private val stats = AudioStatsPoller("MEMBER")
    private var pc: PeerConnection? = null
    private var localTrack: AudioTrack? = null
    private var sender: RtpSender? = null
    private var remoteTrack: AudioTrack? = null
    private var sink: AudioTrackSink? = null

    @Volatile
    private var micLive: Boolean = false

    @Volatile
    private var volume: Float = 1f

    @Volatile
    private var remoteDescriptionSet: Boolean = false

    @Volatile
    private var iceRestarting: Boolean = false

    private val pendingIce = mutableListOf<IceCandidate>()

    @Volatile
    private var lastLevelAt: Long = 0L

    /** Creates the peer connection eagerly so the answer can be produced instantly. */
    fun prepare() {
        synchronized(lock) {
            if (pc != null) return
            val connection = core.createPeerConnection(
                object : PeerObserver(
                    onIce = { candidate -> onIce(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp) },
                    onRemoteTrack = { transceiver ->
                        val track = transceiver.receiver?.track() as? AudioTrack
                        if (track != null) {
                            track.setEnabled(true)
                            track.setVolume(volume.toDouble())
                            attachLevelSink(track)
                        }
                    },
                    onState = { state ->
                        android.util.Log.i("COMMS_STATS", "MEMBER peer state: ${state.name}")
                        val message = when (state) {
                            PeerConnection.PeerConnectionState.CONNECTED -> "Audio connected"
                            PeerConnection.PeerConnectionState.FAILED -> "Audio connection problem"
                            else -> null
                        }
                        if (message != null) onPeerState(message)
                    },
                    onIceConnectionChange = { state ->
                        if (state == PeerConnection.IceConnectionState.FAILED ||
                            state == PeerConnection.IceConnectionState.DISCONNECTED
                        ) {
                            scheduleIceRecovery(state)
                        }
                    },
                ) {},
            )
            pc = connection
            localTrack = core.createLocalTrack("mic")
            stats.start(connection)
        }
    }

    fun handleOffer(sdp: String) {
        prepare()
        val connection = pc ?: return

        connection.setRemoteDescription(
            SdpAdapter(
                onSetSuccess = {
                    synchronized(lock) {
                        remoteDescriptionSet = true
                        pendingIce.forEach { queued ->
                            if (!connection.addIceCandidate(queued)) {
                                android.util.Log.w("COMMS_STATS", "Queued ICE candidate rejected")
                            }
                        }
                        pendingIce.clear()
                    }

                    val audio = connection.transceivers.firstOrNull {
                        it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO
                    }
                    if (audio == null) {
                        android.util.Log.w("COMMS_STATS", "Offer had no audio section")
                        onPeerState("Audio connection problem")
                        return@SdpAdapter
                    }
                    audio.setDirection(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
                    val local = localTrack
                    if (local != null && audio.sender.setTrack(local, false)) {
                        sender = audio.sender
                        applyMicState()
                    }

                    // Fallback: onTrack can fire before the receiver's track is ready;
                    // make sure the level sink is attached either way.
                    if (synchronized(lock) { remoteTrack == null }) {
                        connection.receivers.firstNotNullOfOrNull { it.track() as? AudioTrack }?.let { remote ->
                            remote.setEnabled(true)
                            remote.setVolume(volume.toDouble())
                            attachLevelSink(remote)
                        }
                    }
                    connection.createAnswer(
                        SdpAdapter(
                            onCreateSuccess = { answer ->
                                connection.setLocalDescription(
                                    SdpAdapter(
                                        onSetSuccess = { onAnswer(answer.description) },
                                        onFailure = { error ->
                                            android.util.Log.w("COMMS_STATS", "setLocalDescription failed: $error")
                                            onPeerState("Audio connection problem")
                                        },
                                    ),
                                    answer,
                                )
                            },
                            onFailure = { error ->
                                android.util.Log.w("COMMS_STATS", "createAnswer failed: $error")
                                onPeerState("Audio connection problem")
                            },
                        ),
                        MediaConstraints(),
                    )
                },
                onFailure = { error ->
                    android.util.Log.w("COMMS_STATS", "setRemoteDescription failed: $error")
                    onPeerState("Audio connection problem")
                },
            ),
            SessionDescription(SessionDescription.Type.OFFER, sdp),
        )
    }

    fun handleIce(mid: String?, mLine: Int, candidate: String) {
        val connection = pc ?: return
        val ice = IceCandidate(mid ?: "", mLine, candidate)
        synchronized(lock) {
            if (!remoteDescriptionSet) {
                pendingIce.add(ice)
                return
            }
        }
        val ok = connection.addIceCandidate(ice)
        if (!ok) android.util.Log.w("COMMS_STATS", "ICE candidate rejected")
    }

    /** Member only transmits while it holds the floor. */
    fun setMicLive(live: Boolean) {
        micLive = live
        applyMicState()
    }

    fun setVolume(value: Float) {
        volume = value
        synchronized(lock) { runCatching { remoteTrack?.setVolume(value.toDouble()) } }
    }

    fun close() {
        stats.stop()
        synchronized(lock) {
            runCatching { remoteTrack?.removeSink(sink) }
            runCatching { pc?.close() }
            runCatching { pc?.dispose() }
            runCatching { localTrack?.dispose() }
            pc = null
            localTrack = null
            sender = null
            remoteTrack = null
            sink = null
            remoteDescriptionSet = false
            pendingIce.clear()
            micLive = false
            iceRestarting = false
        }
    }

    /**
     * Recreate the peer connection for a new host connection. The host will send a fresh
     * offer after the client re-joins, so this only tears down the stale state.
     */
    fun reset() {
        close()
        prepare()
    }

    /**
     * Feeds decoded inbound audio to the UI.
     *
     * This is the only way to answer "is the director's voice actually arriving?" without
     * a human in the room: the meter moves only when frames are decoded and played.
     */
    private fun attachLevelSink(track: AudioTrack) {
        val levelSink = object : AudioTrackSink {
            override fun onData(
                data: ByteBuffer?,
                audioFormat: Int,
                sampleRate: Int,
                numberOfFrames: Int,
                numberOfChannels: Int,
                timeStampNs: Long,
            ) {
                val buffer = data ?: return
                val now = System.currentTimeMillis()
                if (now - lastLevelAt < 120) return
                lastLevelAt = now
                onRemoteLevel(incomingLevel(buffer))
            }
        }

        synchronized(lock) {
            runCatching { remoteTrack?.removeSink(sink) }
            runCatching { track.addSink(levelSink) }
            remoteTrack = track
            sink = levelSink
        }
    }

    /**
     * Peak level of a decoded buffer, 0..1.
     *
     * See [AudioLevels.pcm16] for why PCM16 interpretation is used for any format.
     */
    private fun incomingLevel(buffer: ByteBuffer): Float {
        val view = buffer.duplicate()
        val bytes = ByteArray(view.remaining())
        view.get(bytes)
        return AudioLevels.pcm16(bytes)
    }

    private fun applyMicState() {
        val active = micLive
        val current = sender ?: return
        val ok = runCatching {
            val params = current.parameters
            params.encodings.forEach { encoding ->
                encoding.active = active
                if (encoding.maxBitrateBps == null) encoding.maxBitrateBps = AUDIO_BITRATE_BPS
            }
            current.setParameters(params)
        }.getOrDefault(false)
        if (!ok) {
            micLive = false
            android.util.Log.w("COMMS_STATS", "RTP parameter update failed")
            onPeerState("Microphone problem — tap to retry")
        }
    }

    private fun scheduleIceRecovery(state: PeerConnection.IceConnectionState) {
        if (iceRestarting) return
        iceRestarting = true
        scope.launch {
            delay(if (state == PeerConnection.IceConnectionState.FAILED) 0L else 2000L)
            val connection = pc ?: run { iceRestarting = false; return@launch }
            if (connection.connectionState() == PeerConnection.PeerConnectionState.CLOSED) {
                iceRestarting = false
                return@launch
            }
            // Rebuild the peer connection; the host will re-offer after it detects
            // the member dropped, or the member re-joins.
            runCatching { connection.close() }
            runCatching { connection.dispose() }
            synchronized(lock) {
                pc = null
                localTrack = null
                sender = null
                remoteTrack = null
                sink = null
                remoteDescriptionSet = false
                pendingIce.clear()
                micLive = false
            }
            runCatching { localTrack?.dispose() }
            prepare()
            iceRestarting = false
            android.util.Log.i("COMMS_STATS", "ICE $state recovered; peer connection rebuilt")
            onPeerState("Audio connection restored")
        }
    }

    private companion object {
        const val AUDIO_BITRATE_BPS = 32_000
    }
}
