package com.churchmedia.intercom.core.signaling

/**
 * Reconnection backoff and give-up policy for the member's signalling client.
 *
 * The member cannot distinguish "the director's Wi-Fi blipped" from "the session
 * ended", so it retries a bounded number of times before concluding the director is
 * offline. A successful connection resets the counter, so a short blip still
 * reconnects automatically.
 */
object ReconnectPolicy {
    const val MAX_ATTEMPTS: Int = 6

    fun shouldRetry(attempt: Int): Boolean = attempt < MAX_ATTEMPTS

    fun backoffMillis(attempt: Int): Long =
        (500L shl (attempt.coerceIn(1, 5) - 1)).coerceAtMost(8_000L)
}
