package com.churchmedia.intercom.session

import com.churchmedia.intercom.core.audio.AudioRouteManager
import com.churchmedia.intercom.core.audio.MemberAudioEngine
import com.churchmedia.intercom.core.audio.WebRtcCore
import com.churchmedia.intercom.core.model.FloorState
import com.churchmedia.intercom.core.model.LinkState
import com.churchmedia.intercom.core.model.Member
import com.churchmedia.intercom.core.signaling.ClientMessage
import com.churchmedia.intercom.core.signaling.ServerMessage
import com.churchmedia.intercom.core.signaling.SignalingClient
import com.churchmedia.intercom.core.signaling.toModel
import kotlinx.coroutines.CoroutineScope

/** Member role: one connection, one peer connection, one floor request at a time. */
class MemberSession(
    private val scope: CoroutineScope,
    private val core: WebRtcCore,
    private val audioRoute: AudioRouteManager,
    private val url: String,
    private val code: String,
    private val displayName: String,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onLink(state: LinkState, detail: String?)
        fun onRoster(members: List<Member>, floor: FloorState)
        fun onWelcome(hostName: String, myId: String)
        fun onRemoteLevel(level: Float)
        fun onNotice(message: String)
        fun onEnded(reason: String)
    }

    private var myId: String = ""
    private var members: List<Member> = emptyList()
    private var floor: FloorState = FloorState.Free

    private val engine = MemberAudioEngine(
        core = core,
        onAnswer = { sdp -> client.send(ClientMessage.Answer(sdp)) },
        onIce = { mid, mLine, candidate ->
            client.send(ClientMessage.Ice(mid = mid, mLine = mLine, candidate = candidate))
        },
        onPeerState = { detail -> callbacks.onNotice(detail) },
        onRemoteLevel = { level -> callbacks.onRemoteLevel(level) },
    )

    private val client = SignalingClient(
        scope = scope,
        onMessage = ::handleMessage,
        onState = { state, detail -> callbacks.onLink(state, detail) },
    )

    fun start() {
        audioRoute.enterCommunicationMode()
        core.ensureInitialized()
        engine.prepare()
        client.connect(url, ClientMessage.Hello(code = code, displayName = displayName))
    }

    fun stop() {
        runCatching { client.send(ClientMessage.Bye) }
        runCatching { client.close() }
        runCatching { engine.close() }
        runCatching { audioRoute.exitCommunicationMode() }
    }

    /** Tap the mic: take the floor if it is free, otherwise give it back. */
    fun toggleMic() {
        val held = myId.isNotEmpty() && (floor as? FloorState.Member)?.id == myId
        client.send(if (held) ClientMessage.FloorRelease else ClientMessage.FloorRequest)
    }

    fun setVolume(value: Float) {
        engine.setVolume(value)
    }

    private fun handleMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.Welcome -> {
                myId = message.youId
                members = message.members.map { it.toModel() }
                floor = message.floor.toModel()
                // Rebuild the peer connection for a new host connection instead of
                // reusing a stale one.
                engine.reset()
                engine.prepare()
                callbacks.onWelcome(message.hostName, message.youId)
                callbacks.onRoster(members, floor)
                applyFloor()
            }

            is ServerMessage.Roster -> {
                members = message.members.map { it.toModel() }
                floor = message.floor.toModel()
                callbacks.onRoster(members, floor)
                applyFloor()
            }

            is ServerMessage.Offer -> engine.handleOffer(message.sdp)

            is ServerMessage.Ice -> engine.handleIce(message.mid, message.mLine, message.candidate)

            is ServerMessage.Floor -> {
                floor = message.floor.toModel()
                callbacks.onRoster(members, floor)
                applyFloor()
            }

            is ServerMessage.Pong -> Unit

            is ServerMessage.Reject -> {
                callbacks.onEnded(message.reason)
                stop()
            }

            is ServerMessage.Bye -> {
                callbacks.onEnded(message.reason)
                stop()
            }

            is ServerMessage.Error -> callbacks.onNotice(message.message)
        }
    }

    private fun applyFloor() {
        val mine = myId.isNotEmpty() && (floor as? FloorState.Member)?.id == myId
        engine.setMicLive(mine)
    }
}
