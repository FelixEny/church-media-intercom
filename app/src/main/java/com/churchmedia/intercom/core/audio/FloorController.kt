package com.churchmedia.intercom.core.audio

import com.churchmedia.intercom.core.model.FloorState

/**
 * Single-token floor. Pure logic, no Android dependencies, fully unit tested.
 *
 * The host is the only authority: members can request and release, never grant.
 */
class FloorController {

    var current: FloorState = FloorState.Free
        private set

    fun request(memberId: String): Decision = when (val holder = current) {
        FloorState.Free -> {
            current = FloorState.Member(memberId)
            Decision.Granted
        }
        FloorState.Director -> Decision.DirectorTalking
        is FloorState.Member -> if (holder.id == memberId) Decision.Granted else Decision.Busy
    }

    fun release(memberId: String): Boolean {
        val holder = current
        if (holder is FloorState.Member && holder.id == memberId) {
            current = FloorState.Free
            return true
        }
        return false
    }

    /** Director presses the mic. Pre-empts any member, by design. */
    fun directorTake(): FloorState {
        current = FloorState.Director
        return current
    }

    fun directorRelease(): FloorState {
        if (current is FloorState.Director) current = FloorState.Free
        return current
    }

    /** A member left or dropped; never leave the floor stuck. */
    fun memberLeft(memberId: String) {
        val holder = current
        if (holder is FloorState.Member && holder.id == memberId) current = FloorState.Free
    }

    fun reset() {
        current = FloorState.Free
    }

    sealed interface Decision {
        data object Granted : Decision
        data object Busy : Decision
        data object DirectorTalking : Decision
    }
}
