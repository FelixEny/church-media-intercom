package com.churchmedia.intercom.core.audio

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RtpSender
import org.webrtc.SessionDescription

/**
 * Host-side audio: one peer connection per member, all fed from a single microphone
 * capture, each with its own local audio track so recipient selection is per member.
 *
 * ICE candidates that arrive before the remote description is set are queued and
 * drained afterwards; offers/answers are only sent once setLocalDescription succeeds.
 */
class DirectorAudioEngine(
    private val core: WebRtcCore,
    private val onOffer: (memberId: String, sdp: String) -> Unit,
    private val onIce: (memberId: String, mid: String?, mLine: Int, candidate: String) -> Unit,
    private val onPeerState: (memberId: String, detail: String) -> Unit,
) {
    private class Link(
        val memberId: String,
        val pc: PeerConnection,
        val track: AudioTrack,
        val sender: RtpSender,
        val stats: AudioStatsPoller = AudioStatsPoller("DIR[$memberId]"),
        var remote: AudioTrack? = null,
        var selected: Boolean = false,
        var micLive: Boolean = false,
        var remoteDescriptionSet: Boolean = false,
        val iceQueue: MutableList<IceCandidate> = mutableListOf(),
        var iceRestarting: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val links = ConcurrentHashMap<String, Link>()

    /** Members that should hear the director right now. */
    @Volatile
    private var recipients: Set<String> = emptySet()

    /**
     * The dashboard defaults to "everyone". Without this flag the engine would start
     * with an empty recipient set and silently encode to nobody while the UI showed
     * every member ticked.
     */
    @Volatile
    private var everyone: Boolean = true

    @Volatile
    private var micLive: Boolean = false

    @Volatile
    private var volume: Float = 1f

    /** Id of the member whose inbound audio is currently enabled (floor holder). */
    @Volatile
    private var audibleMemberId: String? = null

    fun addMember(memberId: String) {
        removeMember(memberId)

        val pc = core.createPeerConnection(
            object : PeerObserver(
                onIce = { candidate -> onIce(memberId, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp) },
                onRemoteTrack = { transceiver ->
                    val track = transceiver.receiver?.track() as? AudioTrack
                    if (track != null) {
                        track.setEnabled(memberId == audibleMemberId)
                        track.setVolume(volume.toDouble())
                        synchronized(lock) { links[memberId]?.remote = track }
                    }
                },
                onState = { state -> onPeerState(memberId, state.name) },
                onIceConnectionChange = { state ->
                    when (state) {
                        PeerConnection.IceConnectionState.FAILED ->
                            scheduleIceRestart(memberId, 0L)
                        PeerConnection.IceConnectionState.DISCONNECTED ->
                            scheduleIceRestart(memberId, 2000L)
                        else -> Unit
                    }
                },
            ) {},
        )

        val track = core.createLocalTrack("mic-$memberId")
        val sender = pc.addTrack(track, listOf("intercom"))

        val link = Link(memberId = memberId, pc = pc, track = track, sender = sender)
        link.selected = everyone || recipients.contains(memberId)
        link.micLive = micLive
        links[memberId] = link
        link.stats.start(pc)

        applyRouting(link)

        pc.createOffer(
            SdpAdapter(
                onCreateSuccess = { offer ->
                    pc.setLocalDescription(
                        SdpAdapter(
                            onSetSuccess = { onOffer(memberId, offer.description) },
                            onFailure = { error -> onPeerState(memberId, "setLocalDescription failed: $error") },
                        ),
                        offer,
                    )
                },
                onFailure = { error -> onPeerState(memberId, "offer failed: $error") },
            ),
            MediaConstraints(),
        )
    }

    fun removeMember(memberId: String) {
        val link = links.remove(memberId) ?: return
        link.stats.stop()
        runCatching { link.pc.close() }
        runCatching { link.pc.dispose() }
        runCatching { link.track.dispose() }
    }

    fun handleAnswer(memberId: String, sdp: String) {
        val link = links[memberId] ?: return
        link.pc.setRemoteDescription(
            SdpAdapter(
                onSetSuccess = {
                    synchronized(lock) {
                        link.remoteDescriptionSet = true
                        link.iceQueue.forEach { queued ->
                            if (!link.pc.addIceCandidate(queued)) {
                                onPeerState(memberId, "Queued ICE candidate rejected")
                            }
                        }
                        link.iceQueue.clear()
                    }
                },
                onFailure = { error -> onPeerState(memberId, "answer failed: $error") },
            ),
            SessionDescription(SessionDescription.Type.ANSWER, sdp),
        )
    }

    fun handleIce(memberId: String, mid: String?, mLine: Int, candidate: String) {
        val link = links[memberId] ?: return
        val ice = IceCandidate(mid ?: "", mLine, candidate)
        synchronized(lock) {
            if (!link.remoteDescriptionSet) {
                link.iceQueue.add(ice)
                return
            }
        }
        if (!link.pc.addIceCandidate(ice)) {
            onPeerState(memberId, "ICE candidate rejected")
        }
    }

    /** Recipient selection from the dashboard. [everyone] overrides the explicit set. */
    fun setRecipients(selected: Set<String>, everyone: Boolean) {
        this.everyone = everyone
        recipients = if (everyone) links.keys.toSet() else selected
        synchronized(lock) {
            links.values.forEach {
                it.selected = everyone || recipients.contains(it.memberId)
                applyRouting(it)
            }
        }
    }

    fun setMicLive(live: Boolean) {
        micLive = live
        synchronized(lock) {
            links.values.forEach { link ->
                // Per-link copy must be refreshed too: applyRouting() reads link.micLive,
                // and links created while the mic was off would otherwise stay inactive
                // forever even after the director goes live.
                link.micLive = live
                applyRouting(link)
            }
        }
    }

    /** Only the floor holder is audible; everyone else is muted at the receiver. */
    fun setAudible(memberId: String?) {
        audibleMemberId = memberId
        synchronized(lock) {
            links.forEach { (id, link) ->
                runCatching { link.remote?.setEnabled(id == memberId) }
            }
        }
    }

    fun setVolume(value: Float) {
        volume = value
        synchronized(lock) {
            links.values.forEach { runCatching { it.remote?.setVolume(value.toDouble()) } }
        }
    }

    fun closeAll() {
        synchronized(lock) {
            links.values.forEach { link ->
                link.stats.stop()
                runCatching { link.pc.close() }
                runCatching { link.pc.dispose() }
                runCatching { link.track.dispose() }
            }
            links.clear()
        }
    }

    /**
     * A sender only encodes when the member is selected *and* the director's mic is on.
     * Deactivating the encoding is what saves CPU with large recipient lists.
     */
    private fun applyRouting(link: Link) {
        val active = link.selected && link.micLive
        runCatching {
            val params = link.sender.parameters
            params.encodings.forEach { encoding ->
                encoding.active = active
                if (encoding.maxBitrateBps == null) encoding.maxBitrateBps = AUDIO_BITRATE_BPS
            }
            val ok = link.sender.setParameters(params)
            if (!ok) {
                link.micLive = false
                onPeerState(link.memberId, "RTP parameter update failed; transmission disabled")
            }
        }
    }

    private fun scheduleIceRestart(memberId: String, delayMs: Long) {
        val link = links[memberId] ?: return
        if (link.iceRestarting) return
        link.iceRestarting = true
        scope.launch {
            delay(delayMs)
            val current = links[memberId]
            if (current == null || current !== link) {
                link.iceRestarting = false
                return@launch
            }
            if (current.pc.connectionState() == PeerConnection.PeerConnectionState.CLOSED) {
                link.iceRestarting = false
                return@launch
            }
            runCatching { current.pc.restartIce() }
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
            current.pc.createOffer(
                SdpAdapter(
                    onCreateSuccess = { offer ->
                        current.pc.setLocalDescription(
                            SdpAdapter(
                                onSetSuccess = {
                                    onOffer(memberId, offer.description)
                                    current.iceRestarting = false
                                },
                                onFailure = { error ->
                                    onPeerState(memberId, "ice-restart setLocalDescription failed: $error")
                                    current.iceRestarting = false
                                },
                            ),
                            offer,
                        )
                    },
                    onFailure = { error ->
                        onPeerState(memberId, "ice-restart createOffer failed: $error")
                        current.iceRestarting = false
                    },
                ),
                constraints,
            )
        }
    }

    private companion object {
        const val AUDIO_BITRATE_BPS = 32_000
    }
}
