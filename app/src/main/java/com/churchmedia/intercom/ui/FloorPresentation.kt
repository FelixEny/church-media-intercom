package com.churchmedia.intercom.ui

import com.churchmedia.intercom.core.model.FloorState
import com.churchmedia.intercom.core.model.LinkState
import com.churchmedia.intercom.core.model.Member
import com.churchmedia.intercom.core.model.Mode
import com.churchmedia.intercom.core.model.Role
import com.churchmedia.intercom.session.AppState
import com.churchmedia.intercom.ui.components.Tone

/**
 * Pure presentation helpers for the member dashboard's floor state.
 *
 * Kept free of Compose so the floor-state behaviour is unit testable.
 */
object FloorPresentation {

    /**
     * The banner text and tone for the current floor state.
     *
     * The holder is always named when it is known; the director is identified by role
     * rather than by display name so the message is unambiguous.
     */
    fun banner(state: AppState): Pair<String, Tone> = when {
        state.myMicLive ->
            "You are live — the director hears you" to Tone.Live

        state.floor is FloorState.Director ->
            "Director is speaking" to Tone.Accent

        state.floor is FloorState.Member -> {
            val name = state.holderName ?: "A member"
            "$name is currently speaking to the Director." to Tone.Warning
        }

        else -> "The channel is quiet" to Tone.Neutral
    }

    /**
     * Whether the member's microphone button should be enabled. It is unavailable
     * while the director or another participant holds the floor.
     */
    fun canSpeak(state: AppState): Boolean {
        val connected = state.link == LinkState.CONNECTED
        return connected && (state.floor is FloorState.Free || state.myMicLive)
    }

    /** Caption shown under the microphone button. */
    fun micCaption(state: AppState): String = when {
        state.myMicLive -> "Tap to stop"
        !canSpeak(state) -> "Waiting for the microphone"
        else -> "Tap to reply privately"
    }
}
