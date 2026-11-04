package com.churchmedia.intercom.core.signaling

import com.churchmedia.intercom.core.model.FloorState
import com.churchmedia.intercom.core.model.Member
import com.churchmedia.intercom.core.model.PROTOCOL_VERSION
import com.churchmedia.intercom.core.model.Role
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire protocol v1. See docs/PROTOCOL.md.
 *
 * The director never dials itself: the host consumes [ClientMessage] directly from
 * its embedded server and emits [ServerMessage] through it.
 */
@Serializable
sealed interface ClientMessage {

    @Serializable
    @SerialName("hello")
    data class Hello(
        val protocol: Int = PROTOCOL_VERSION,
        val code: String,
        val displayName: String,
        val role: String = ROLE_MEMBER,
    ) : ClientMessage

    @Serializable
    @SerialName("answer")
    data class Answer(val sdp: String) : ClientMessage

    @Serializable
    @SerialName("ice")
    data class Ice(
        val mid: String? = null,
        val mLine: Int = 0,
        val candidate: String,
    ) : ClientMessage

    @Serializable
    @SerialName("floor.request")
    data object FloorRequest : ClientMessage

    @Serializable
    @SerialName("floor.release")
    data object FloorRelease : ClientMessage

    @Serializable
    @SerialName("mic")
    data class Mic(val live: Boolean) : ClientMessage

    @Serializable
    @SerialName("ping")
    data class Ping(val sentAt: Long) : ClientMessage

    @Serializable
    @SerialName("bye")
    data object Bye : ClientMessage
}

@Serializable
sealed interface ServerMessage {

    @Serializable
    @SerialName("welcome")
    data class Welcome(
        val youId: String,
        val sessionCode: String,
        val hostName: String,
        val members: List<MemberDto>,
        val floor: FloorDto,
    ) : ServerMessage

    @Serializable
    @SerialName("reject")
    data class Reject(val reason: String) : ServerMessage

    @Serializable
    @SerialName("roster")
    data class Roster(
        val members: List<MemberDto>,
        val floor: FloorDto,
    ) : ServerMessage

    @Serializable
    @SerialName("offer")
    data class Offer(val sdp: String) : ServerMessage

    @Serializable
    @SerialName("ice")
    data class Ice(
        val mid: String? = null,
        val mLine: Int = 0,
        val candidate: String,
    ) : ServerMessage

    @Serializable
    @SerialName("floor")
    data class Floor(val floor: FloorDto) : ServerMessage

    @Serializable
    @SerialName("pong")
    data class Pong(val sentAt: Long, val serverAt: Long) : ServerMessage

    @Serializable
    @SerialName("bye")
    data class Bye(val reason: String) : ServerMessage

    @Serializable
    @SerialName("error")
    data class Error(val message: String) : ServerMessage
}

@Serializable
data class MemberDto(
    val id: String,
    val name: String,
    val role: String,
    val micLive: Boolean = false,
    val connected: Boolean = true,
    val rttMs: Int? = null,
)

@Serializable
data class FloorDto(
    /** `null` = free, [HOLDER_DIRECTOR], or a member id. */
    val holder: String? = null,
)

const val ROLE_DIRECTOR = "director"
const val ROLE_MEMBER = "member"
const val HOLDER_DIRECTOR = "director"

val CommsJson: Json = Json {
    classDiscriminator = "t"
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun FloorState.toDto(): FloorDto = when (this) {
    FloorState.Free -> FloorDto(null)
    FloorState.Director -> FloorDto(HOLDER_DIRECTOR)
    is FloorState.Member -> FloorDto(id)
}

fun FloorDto.toModel(): FloorState = when (val h = holder) {
    null -> FloorState.Free
    HOLDER_DIRECTOR -> FloorState.Director
    else -> FloorState.Member(h)
}

fun MemberDto.toModel(): Member = Member(
    id = id,
    name = name,
    role = if (role == ROLE_DIRECTOR) Role.DIRECTOR else Role.MEMBER,
    micLive = micLive,
    connected = connected,
    rttMs = rttMs,
)

fun ClientMessage.encode(): String = CommsJson.encodeToString(ClientMessage.serializer(), this)

fun ServerMessage.encode(): String = CommsJson.encodeToString(ServerMessage.serializer(), this)

fun decodeClientMessage(raw: String): ClientMessage? = runCatching {
    CommsJson.decodeFromString(ClientMessage.serializer(), raw)
}.getOrNull()

fun decodeServerMessage(raw: String): ServerMessage? = runCatching {
    CommsJson.decodeFromString(ServerMessage.serializer(), raw)
}.getOrNull()
