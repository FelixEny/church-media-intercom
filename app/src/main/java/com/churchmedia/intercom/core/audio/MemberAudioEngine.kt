package com.churchmedia.intercom.core.audio

import org.webrtc.AudioTrack
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
 */
class MemberAudioEngine(
    private val core: WebRtcCore,
    private val onAnswer: (sdp: String) -> Unit,
    private val onIce: (mid: String?, mLine: Int, candidate: String) -> Unit,
    private val onPeerState: (detail: String) -> Unit,
) {
    private val lock = Any()

    private var pc: PeerConnection? = null
    private var localTrack: AudioTrack? = null
    private var sender: RtpSender? = null
    private var remoteTrack: AudioTrack? = null

    @Volatile
    private var micLive: Boolean = false

    @Volatile
    private var volume: Float = 1f

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
                            synchronized(lock) { remoteTrack = track }
                        }
                    },
                    onState = { state -> onPeerState(state.name) },
                ) {},
            )
            pc = connection
            localTrack = core.createLocalTrack("mic")
        }
    }

    fun handleOffer(sdp: String) {
        prepare()
        val connection = pc ?: return

        connection.setRemoteDescription(
            SdpAdapter(
                onSetSuccess = {
                    val audio = connection.transceivers.firstOrNull {
                        it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO
                    }
                    if (audio == null) {
                        onPeerState("Offer had no audio section")
                        return@SdpAdapter
                    }
                    audio.setDirection(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
                    val local = localTrack
                    if (local != null && audio.sender.setTrack(local, false)) {
                        sender = audio.sender
                        applyMicState()
                    }
                    connection.createAnswer(
                        SdpAdapter(
                            onCreateSuccess = { answer ->
                                connection.setLocalDescription(SdpAdapter(), answer)
                                onAnswer(answer.description)
                            },
                            onFailure = { error -> onPeerState("answer failed: $error") },
                        ),
                        MediaConstraints(),
                    )
                },
                onFailure = { error -> onPeerState("offer failed: $error") },
            ),
            SessionDescription(SessionDescription.Type.OFFER, sdp),
        )
    }

    fun handleIce(mid: String?, mLine: Int, candidate: String) {
        val connection = pc ?: return
        runCatching { connection.addIceCandidate(IceCandidate(mid ?: "", mLine, candidate)) }
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
        synchronized(lock) {
            runCatching { pc?.close() }
            runCatching { pc?.dispose() }
            runCatching { localTrack?.dispose() }
            pc = null
            localTrack = null
            sender = null
            remoteTrack = null
        }
    }

    private fun applyMicState() {
        val active = micLive
        val current = sender ?: return
        runCatching {
            val params = current.parameters
            params.encodings.forEach { encoding ->
                encoding.active = active
                if (encoding.maxBitrateBps == null) encoding.maxBitrateBps = AUDIO_BITRATE_BPS
            }
            current.setParameters(params)
        }
    }

    private companion object {
        const val AUDIO_BITRATE_BPS = 32_000
    }
}
