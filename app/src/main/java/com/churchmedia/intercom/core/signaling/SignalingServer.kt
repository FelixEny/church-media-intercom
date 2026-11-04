package com.churchmedia.intercom.core.signaling

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch

/**
 * Embedded signalling server hosted on the director's phone.
 *
 * This class is deliberately a dumb transport: it assigns connection ids and moves
 * frames. All session policy (codes, roster, floor, peer connections) lives in
 * `DirectorSession`.
 */
class SignalingServer(
    private val port: Int,
    private val onClientMessage: (clientId: String, message: ClientMessage) -> Unit,
    private val onClientDisconnected: (clientId: String) -> Unit,
) {
    private val outboxes = ConcurrentHashMap<String, SendChannel<ServerMessage>>()

    @Volatile
    private var server: EmbeddedServer<*, *>? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) return
        val engine = embeddedServer(CIO, port = port) {
            install(WebSockets) {
                maxFrameSize = 512L * 1024L
                masking = false
            }
            routing {
                webSocket("/ws") {
                    val clientId = "c-" + UUID.randomUUID().toString().take(8)
                    val outbox = Channel<ServerMessage>(capacity = 64)
                    outboxes[clientId] = outbox

                    val writer = launch {
                        for (message in outbox) {
                            send(Frame.Text(message.encode()))
                        }
                    }

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                decodeClientMessage(frame.readText())?.let { message ->
                                    onClientMessage(clientId, message)
                                }
                            }
                        }
                    } finally {
                        writer.cancel()
                        outboxes.remove(clientId)
                        onClientDisconnected(clientId)
                    }
                }
            }
        }
        engine.start(wait = false)
        server = engine
        isRunning = true
    }

    fun stop() {
        isRunning = false
        outboxes.values.forEach { it.close() }
        outboxes.clear()
        server?.stop(0, 500)
        server = null
    }

    fun send(clientId: String, message: ServerMessage) {
        outboxes[clientId]?.trySend(message)
    }

    fun broadcast(message: ServerMessage) {
        outboxes.values.forEach { it.trySend(message) }
    }

    fun isConnected(clientId: String): Boolean = outboxes.containsKey(clientId)
}
