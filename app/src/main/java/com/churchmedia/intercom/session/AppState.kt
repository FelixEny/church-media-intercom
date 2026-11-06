package com.churchmedia.intercom.session

import com.churchmedia.intercom.core.model.DEFAULT_PORT
import com.churchmedia.intercom.core.model.FloorState
import com.churchmedia.intercom.core.model.LinkState
import com.churchmedia.intercom.core.model.Member
import com.churchmedia.intercom.core.model.Mode
import com.churchmedia.intercom.core.model.OutputDevice
import com.churchmedia.intercom.core.model.Reaction
import com.churchmedia.intercom.core.model.Role

/** Everything the UI renders, as one immutable snapshot. */
data class AppState(
    val mode: Mode = Mode.IDLE,
    val link: LinkState = LinkState.IDLE,
    val linkDetail: String? = null,
    val connecting: Boolean = false,
    val sessionCode: String = "",
    val hostName: String = "",
    val hostAddress: String = "",
    val localAddress: String = "",
    val port: Int = DEFAULT_PORT,
    val myId: String = "",
    /** 0..1 level of audio arriving from the director; only meaningful in member mode. */
    val remoteLevel: Float = 0f,
    /** 0..1 level of this phone's own microphone. */
    val micLevel: Float = 0f,
    /** Label of the currently active audio output, or null when unknown. */
    val outputLabel: String? = null,
    val members: List<Member> = emptyList(),
    val floor: FloorState = FloorState.Free,
    val recipients: Set<String> = emptySet(),
    val everyone: Boolean = true,
    /** Transient thumbs-up reactions currently visible to this side. */
    val reactions: List<Reaction> = emptyList(),
    val noiseSuppression: Boolean = true,
    val processingSummary: String = "",
    val volume: Float = 1f,
    val outputs: List<OutputDevice> = emptyList(),
    val outputId: Int? = null,
    val displayName: String = "",
    val lastHost: String = "",
    val lastCode: String = "",
    val error: String? = null,
    val notice: String? = null,
) {
    val directorMicLive: Boolean get() = floor is FloorState.Director

    val myMicLive: Boolean
        get() = when (mode) {
            Mode.DIRECTOR -> floor is FloorState.Director
            Mode.MEMBER -> myId.isNotEmpty() && (floor as? FloorState.Member)?.id == myId
            Mode.IDLE -> false
        }

    /** Name of whoever is transmitting, for the "X is talking" banner. */
    val holderName: String?
        get() = when (val f = floor) {
            FloorState.Free -> null
            FloorState.Director -> members.firstOrNull { it.role == Role.DIRECTOR }?.name ?: "Director"
            is FloorState.Member -> members.firstOrNull { it.id == f.id }?.name ?: "Member"
        }

    val director: Member? get() = members.firstOrNull { it.role == Role.DIRECTOR }

    val listeners: List<Member> get() = members.filter { it.role == Role.MEMBER }
}
