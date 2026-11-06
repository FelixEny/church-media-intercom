package com.churchmedia.intercom.core.model

/** Default TCP port for the director's signalling server. */
const val DEFAULT_PORT: Int = 47811

/** Protocol version carried in the `hello` handshake. */
const val PROTOCOL_VERSION: Int = 1

/** Hard ceiling for one session, including the director. */
const val MAX_MEMBERS: Int = 10

enum class Role { DIRECTOR, MEMBER }

/**
 * Who currently owns the talking token. Exactly one holder at a time; everything
 * else is enforced against this value on the host and mirrored on clients.
 */
sealed interface FloorState {
    /** Nobody is transmitting; a member may request the floor. */
    data object Free : FloorState

    /** The director holds the floor. */
    data object Director : FloorState

    /** A specific member holds the floor and is audible to the director only. */
    data class Member(val id: String) : FloorState
}

data class Member(
    val id: String,
    val name: String,
    val role: Role,
    val micLive: Boolean = false,
    val connected: Boolean = true,
    val rttMs: Int? = null,
)

/** A local-network audio output the user can route to. */
data class OutputDevice(
    val id: Int,
    val label: String,
    val kind: Kind,
) {
    enum class Kind { SPEAKER, EARPIECE, WIRED, USB, BLUETOOTH, OTHER }
}

enum class Mode { IDLE, DIRECTOR, MEMBER }

/** Coarse connection state used by the status pills. */
enum class LinkState { IDLE, CONNECTING, CONNECTED, RECONNECTING, FAILED }

/** A transient thumbs-up reaction. Auto-clears after [expiresAt]. */
data class Reaction(
    val id: String,
    val fromId: String,
    val fromName: String,
    val toId: String?,
    val createdAt: Long,
    val expiresAt: Long,
)

data class SessionInfo(
    val code: String,
    val hostName: String,
    val hostAddress: String,
    val port: Int = DEFAULT_PORT,
)
