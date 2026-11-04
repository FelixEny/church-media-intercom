package com.churchmedia.intercom.core.audio

import java.util.concurrent.ConcurrentHashMap
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.RtpSender
import org.webrtc.SessionDescription

/**
 * Host-side audio: one peer connection per member, all fed from a single microphone
 * capture, each with its own local audio track so recipient selection is per member.
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
        var remote: AudioTrack? = null,
        var selected: Boolean = false,
        var micLive: Boolean = false,
    )

    private val lock = Any()
    private val links = ConcurrentHashMap<String, Link>()

    /** Members that should hear the director right now. */
    @Volatile
    private var recipients: Set<String> = emptySet()

    @Volatile
    private var micLive: Boolean = false

    @Volatile
    private var volume: Float = 1f

    fun addMember(memberId: String) {
        removeMember(memberId)

        val pc = core.createPeerConnection(
            object : PeerObserver(
                onIce = { candidate -> onIce(memberId, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp) },
                onRemoteTrack = { transceiver ->
                    val track = transceiver.receiver?.track() as? AudioTrack
                    if (track != null) {
                        track.setEnabled(false)
                        track.setVolume(volume.toDouble())
                        synchronized(lock) { links[memberId]?.remote = track }
                    }
                },
                onState = { state -> onPeerState(memberId, state.name) },
            ) {},
        )

        val track = core.createLocalTrack("mic-$memberId")
        val sender = pc.addTrack(track, listOf("intercom"))

        val link = Link(memberId = memberId, pc = pc, track = track, sender = sender)
        link.selected = recipients.contains(memberId)
        link.micLive = micLive
        links[memberId] = link

        applyRouting(link)

        pc.createOffer(
            SdpAdapter(
                onCreateSuccess = { offer ->
                    pc.setLocalDescription(SdpAdapter(), offer)
                    onOffer(memberId, offer.description)
                },
                onFailure = { error -> onPeerState(memberId, "offer failed: $error") },
            ),
            MediaConstraints(),
        )
    }

    fun removeMember(memberId: String) {
        val link = links.remove(memberId) ?: return
        runCatching { link.pc.close() }
        runCatching { link.pc.dispose() }
        runCatching { link.track.dispose() }
    }

    fun handleAnswer(memberId: String, sdp: String) {
        val link = links[memberId] ?: return
        link.pc.setRemoteDescription(
            SdpAdapter(onFailure = { error -> onPeerState(memberId, "answer failed: $error") }),
            SessionDescription(SessionDescription.Type.ANSWER, sdp),
        )
    }

    fun handleIce(memberId: String, mid: String?, mLine: Int, candidate: String) {
        val link = links[memberId] ?: return
        runCatching { link.pc.addIceCandidate(IceCandidate(mid ?: "", mLine, candidate)) }
    }

    /** Recipient selection from the dashboard. [everyone] overrides the explicit set. */
    fun setRecipients(selected: Set<String>, everyone: Boolean) {
        recipients = if (everyone) links.keys.toSet() else selected
        synchronized(lock) {
            links.values.forEach {
                it.selected = recipients.contains(it.memberId)
                applyRouting(it)
            }
        }
    }

    fun setMicLive(live: Boolean) {
        micLive = live
        synchronized(lock) { links.values.forEach { applyRouting(it) } }
    }

    /** Only the floor holder is audible; everyone else is muted at the receiver. */
    fun setAudible(memberId: String?) {
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
            link.sender.setParameters(params)
        }
    }

    private companion object {
        const val AUDIO_BITRATE_BPS = 32_000
    }
}
