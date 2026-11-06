package com.churchmedia.intercom.session

import com.churchmedia.intercom.core.audio.AudioRouteManager
import com.churchmedia.intercom.core.audio.DirectorAudioEngine
import com.churchmedia.intercom.core.audio.FloorController
import com.churchmedia.intercom.core.audio.WebRtcCore
import com.churchmedia.intercom.core.model.FloorState
import com.churchmedia.intercom.core.model.MAX_MEMBERS
import com.churchmedia.intercom.core.model.Member
import com.churchmedia.intercom.core.model.PROTOCOL_VERSION
import com.churchmedia.intercom.core.model.Role
import com.churchmedia.intercom.core.model.Reaction
import com.churchmedia.intercom.core.net.LanNetwork
import com.churchmedia.intercom.core.signaling.ClientMessage
import com.churchmedia.intercom.core.signaling.MemberDto
import com.churchmedia.intercom.core.signaling.ROLE_DIRECTOR
import com.churchmedia.intercom.core.signaling.ROLE_MEMBER
import com.churchmedia.intercom.core.signaling.ServerMessage
import com.churchmedia.intercom.core.signaling.SignalingServer
import com.churchmedia.intercom.core.signaling.toDto
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Host role: owns the signalling server, the roster, the floor, and the N peer
 * connections. See docs/ARCHITECTURE.md for the flow.
 */
class DirectorSession(
    private val core: WebRtcCore,
    private val audioRoute: AudioRouteManager,
    val code: String,
    private val hostName: String,
    val port: Int,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onRoster(members: List<Member>, floor: FloorState)
        fun onNotice(message: String)
        fun onReaction(reaction: Reaction)
        fun onReactionExpired(id: String)
        fun onEnded(reason: String)
    }

    private class ClientEntry(
        val clientId: String,
        val memberId: String,
        val name: String,
    )

    private val clients = ConcurrentHashMap<String, ClientEntry>()
    private val floor = FloorController()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var started = false

    private val engine = DirectorAudioEngine(
        core = core,
        onOffer = { memberId, sdp -> sendToMember(memberId, ServerMessage.Offer(sdp)) },
        onIce = { memberId, mid, mLine, candidate ->
            sendToMember(memberId, ServerMessage.Ice(mid = mid, mLine = mLine, candidate = candidate))
        },
        onPeerState = { memberId, detail -> callbacks.onNotice("$memberId · $detail") },
    )

    private val server = SignalingServer(
        port = port,
        onClientMessage = ::handleMessage,
        onClientDisconnected = ::handleDisconnected,
    )

    val localAddress: String get() = LanNetwork.ipv4() ?: "127.0.0.1"

    fun start() {
        if (started) return
        audioRoute.enterCommunicationMode()
        core.ensureInitialized()
        server.start()
        started = true
        callbacks.onRoster(roster(), floor.current)
    }

    fun stop(reason: String = "The director ended the session") {
        if (!started) return
        started = false
        runCatching { server.broadcast(ServerMessage.Bye(reason)) }
        runCatching { server.stop() }
        runCatching { engine.closeAll() }
        floor.reset()
        clients.clear()
    }

    // ---------------------------------------------------------------- controls

    /** Director mic press/release. Pre-empts any member who holds the floor. */
    fun setDirectorMicLive(live: Boolean) {
        if (live) floor.directorTake() else floor.directorRelease()
        applyFloor()
    }

    fun setRecipients(selected: Set<String>, everyone: Boolean) {
        engine.setRecipients(selected, everyone)
        broadcastRoster()
    }

    fun setVolume(value: Float) {
        engine.setVolume(value)
    }

    fun setNoiseSuppression(enabled: Boolean): Boolean = core.setNoiseSuppression(enabled)

    fun describeProcessing(): String = core.describeProcessing()

    fun sendThumbsUp(memberId: String) {
        val entry = clients.values.firstOrNull { it.memberId == memberId } ?: return
        val reaction = Reaction(
            id = java.util.UUID.randomUUID().toString().take(8),
            fromId = DIRECTOR_ID,
            fromName = hostName,
            toId = memberId,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + REACTION_TTL_MS,
        )
        server.send(entry.clientId, ServerMessage.ThumbsUp(reaction.fromId, reaction.fromName, reaction.toId))
    }

    // --------------------------------------------------------------- signalling

    private fun handleMessage(clientId: String, message: ClientMessage) {
        when (message) {
            is ClientMessage.Hello -> handleHello(clientId, message)
            is ClientMessage.Answer -> clients[clientId]?.let { engine.handleAnswer(it.memberId, message.sdp) }
            is ClientMessage.Ice -> clients[clientId]?.let {
                engine.handleIce(it.memberId, message.mid, message.mLine, message.candidate)
            }
            ClientMessage.FloorRequest -> handleFloorRequest(clientId)
            ClientMessage.FloorRelease -> handleFloorRelease(clientId)
            is ClientMessage.Mic -> broadcastRoster()
            ClientMessage.ThumbsUp -> clients[clientId]?.let { handleThumbsUp(it) }
            is ClientMessage.Ping -> server.send(
                clientId,
                ServerMessage.Pong(message.sentAt, System.currentTimeMillis()),
            )
            ClientMessage.Bye -> handleDisconnected(clientId)
        }
    }

    private fun handleHello(clientId: String, hello: ClientMessage.Hello) {
        if (hello.protocol != PROTOCOL_VERSION) {
            server.send(clientId, ServerMessage.Reject("Update the app: this session needs protocol $PROTOCOL_VERSION"))
            return
        }
        if (hello.code != code) {
            server.send(clientId, ServerMessage.Reject("Wrong session code"))
            return
        }
        if (clients.size >= MAX_MEMBERS - 1) {
            server.send(clientId, ServerMessage.Reject("Session is full (${MAX_MEMBERS - 1} members)"))
            return
        }

        // A reconnect arrives on a fresh socket with a fresh clientId, so this is
        // normally a brand new member. Only a duplicate `hello` on the same socket
        // reuses the identity.
        val existing = clients[clientId]
        val memberId = existing?.memberId ?: ("m-" + randomId())
        val name = hello.displayName.trim().take(24).ifBlank { "Member" }
        val entry = ClientEntry(clientId, memberId, name)
        clients[clientId] = entry

        server.send(
            clientId,
            ServerMessage.Welcome(
                youId = memberId,
                sessionCode = code,
                hostName = hostName,
                members = rosterDtos(),
                floor = floor.current.toDto(),
            ),
        )

        // addMember() replaces any stale link for this id before offering.
        engine.addMember(memberId)

        broadcastRoster()
        callbacks.onNotice("$name joined")
    }

    private fun handleDisconnected(clientId: String) {
        val entry = clients.remove(clientId) ?: return
        engine.removeMember(entry.memberId)
        floor.memberLeft(entry.memberId)
        applyFloor()
        broadcastRoster()
        callbacks.onNotice("${entry.name} left")
    }

    private fun handleFloorRequest(clientId: String) {
        val entry = clients[clientId] ?: return
        when (floor.request(entry.memberId)) {
            FloorController.Decision.Granted -> applyFloor()
            FloorController.Decision.Busy -> {
                val holder = (floor.current as? FloorState.Member)?.id
                val name = clients.values.firstOrNull { it.memberId == holder }?.name
                server.send(clientId, ServerMessage.Error("${name ?: "Someone"} is talking"))
            }
            FloorController.Decision.DirectorTalking ->
                server.send(clientId, ServerMessage.Error("The director is talking"))
        }
        // Re-sync the requester even when denied so its UI cannot get stuck.
        server.send(clientId, ServerMessage.Floor(floor.current.toDto()))
    }

    private fun handleFloorRelease(clientId: String) {
        val entry = clients[clientId] ?: return
        if (floor.release(entry.memberId)) applyFloor()
        server.send(clientId, ServerMessage.Floor(floor.current.toDto()))
    }

    private fun handleThumbsUp(entry: ClientEntry) {
        val reaction = Reaction(
            id = java.util.UUID.randomUUID().toString().take(8),
            fromId = entry.memberId,
            fromName = entry.name,
            toId = DIRECTOR_ID,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + REACTION_TTL_MS,
        )
        callbacks.onReaction(reaction)
        scope.launch {
            kotlinx.coroutines.delay(REACTION_TTL_MS)
            callbacks.onReactionExpired(reaction.id)
        }
    }

    private fun applyFloor() {
        val holder = floor.current
        engine.setMicLive(holder is FloorState.Director)
        engine.setAudible((holder as? FloorState.Member)?.id)

        val dto = holder.toDto()
        clients.keys.forEach { server.send(it, ServerMessage.Floor(dto)) }
        callbacks.onRoster(roster(), holder)
    }

    // ------------------------------------------------------------------ roster

    private fun roster(): List<Member> {
        val holder = floor.current
        val director = Member(
            id = DIRECTOR_ID,
            name = hostName,
            role = Role.DIRECTOR,
            micLive = holder is FloorState.Director,
            connected = true,
        )
        val members = clients.values.map { entry ->
            Member(
                id = entry.memberId,
                name = entry.name,
                role = Role.MEMBER,
                micLive = holder is FloorState.Member && holder.id == entry.memberId,
                connected = true,
            )
        }
        return listOf(director) + members
    }

    private fun rosterDtos(): List<MemberDto> = roster().map {
        MemberDto(
            id = it.id,
            name = it.name,
            role = if (it.role == Role.DIRECTOR) ROLE_DIRECTOR else ROLE_MEMBER,
            micLive = it.micLive,
            connected = it.connected,
            rttMs = it.rttMs,
        )
    }

    private fun broadcastRoster() {
        val message = ServerMessage.Roster(rosterDtos(), floor.current.toDto())
        clients.keys.forEach { server.send(it, message) }
        callbacks.onRoster(roster(), floor.current)
    }

    private fun sendToMember(memberId: String, message: ServerMessage) {
        val entry = clients.values.firstOrNull { it.memberId == memberId } ?: return
        server.send(entry.clientId, message)
    }

    private fun randomId(): String = java.util.UUID.randomUUID().toString().take(6)

    companion object {
        const val DIRECTOR_ID = "director"
        const val REACTION_TTL_MS = 3000L
    }
}
