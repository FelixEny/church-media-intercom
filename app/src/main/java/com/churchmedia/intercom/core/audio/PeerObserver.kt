package com.churchmedia.intercom.core.audio

import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/** `SdpObserver` with lambdas, so the engines stay readable. */
class SdpAdapter(
    private val onCreateSuccess: ((SessionDescription) -> Unit)? = null,
    private val onSetSuccess: (() -> Unit)? = null,
    private val onFailure: ((String) -> Unit)? = null,
) : SdpObserver {

    override fun onCreateSuccess(sdp: SessionDescription?) {
        if (sdp != null) onCreateSuccess?.invoke(sdp)
    }

    override fun onSetSuccess() {
        onSetSuccess?.invoke()
    }

    override fun onCreateFailure(error: String?) {
        onFailure?.invoke(error ?: "createOffer failed")
    }

    override fun onSetFailure(error: String?) {
        onFailure?.invoke(error ?: "setDescription failed")
    }
}

/** `PeerConnection.Observer` with only the callbacks this app cares about. */
open class PeerObserver(
    private val onIce: (IceCandidate) -> Unit,
    private val onRemoteTrack: (RtpTransceiver) -> Unit,
    private val onState: (PeerConnection.PeerConnectionState) -> Unit,
    private val onIceConnectionChange: (PeerConnection.IceConnectionState) -> Unit = {},
) : PeerConnection.Observer {

    override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit

    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
        newState?.let(onIceConnectionChange)
    }

    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = Unit

    override fun onIceCandidate(candidate: IceCandidate?) {
        candidate?.let(onIce)
    }

    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit

    override fun onAddStream(stream: MediaStream?) = Unit

    override fun onRemoveStream(stream: MediaStream?) = Unit

    override fun onDataChannel(channel: DataChannel?) = Unit

    override fun onRenegotiationNeeded() = Unit

    override fun onTrack(transceiver: RtpTransceiver?) {
        transceiver?.let(onRemoteTrack)
    }

    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
        newState?.let(onState)
    }
}
