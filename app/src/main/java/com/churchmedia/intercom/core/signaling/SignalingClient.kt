package com.churchmedia.intercom.core.signaling

import com.churchmedia.intercom.core.model.LinkState
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Member-side signalling client. Reconnects with exponential backoff and re-sends
 * the `hello` handshake on every new connection, which makes the host rebuild the
 * peer connection for us.
 */
class SignalingClient(
    private val scope: CoroutineScope,
    private val onMessage: (ServerMessage) -> Unit,
    private val onState: (LinkState, String?) -> Unit,
) {
    private val client = HttpClient(OkHttp) {
        install(WebSockets)
    }

    @Volatile
    private var outbox: SendChannel<ClientMessage>? = null

    @Volatile
    private var stopped = false

    private var job: Job? = null

    fun connect(url: String, hello: ClientMessage.Hello) {
        stopped = false
        job?.cancel()
        job = scope.launch { runLoop(url, hello) }
    }

    fun send(message: ClientMessage) {
        outbox?.trySend(message)
    }

    fun close() {
        stopped = true
        outbox?.close()
        outbox = null
        job?.cancel()
        job = null
        runCatching { client.close() }
        onState(LinkState.IDLE, null)
    }

    private suspend fun runLoop(url: String, hello: ClientMessage.Hello) {
        var attempt = 0
        onState(LinkState.CONNECTING, null)

        while (!stopped) {
            try {
                client.webSocket(url) {
                    val box = Channel<ClientMessage>(capacity = 64)
                    outbox = box
                    val writer = launch {
                        for (message in box) {
                            send(Frame.Text(message.encode()))
                        }
                    }

                    // Handshake must be the first frame on every connection.
                    send(Frame.Text(hello.encode()))
                    onState(LinkState.CONNECTED, null)
                    attempt = 0

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                decodeServerMessage(frame.readText())?.let(onMessage)
                            }
                        }
                    } finally {
                        writer.cancel()
                        outbox = null
                    }
                }
                if (!stopped) onState(LinkState.RECONNECTING, "Connection lost")
            } catch (t: Throwable) {
                if (stopped) break
                onState(LinkState.RECONNECTING, t.message ?: "Connection failed")
            }

            if (stopped) break
            attempt++
            if (!ReconnectPolicy.shouldRetry(attempt)) {
                onState(LinkState.FAILED, "The director is offline. The session may have ended.")
                return
            }
            delay(ReconnectPolicy.backoffMillis(attempt))
        }
    }
}
