package com.churchmedia.intercom.core.audio

import com.churchmedia.intercom.core.model.FloorState

/**
 * Single-token floor. Pure logic, no Android dependencies, fully unit tested.
 *
 * The host is the only authority: members can request and release, never grant.
 */
class FloorController {

    private val lock = Any()

    @Volatile
    private var _current: FloorState = FloorState.Free

    var current: FloorState
        get() = synchronized(lock) { _current }
        private set(value) {
            synchronized(lock) { _current = value }
        }

    fun request(memberId: String): Decision = synchronized(lock) {
        when (val holder = _current) {
            FloorState.Free -> {
                _current = FloorState.Member(memberId)
                Decision.Granted
            }
            FloorState.Director -> Decision.DirectorTalking
            is FloorState.Member -> if (holder.id == memberId) Decision.Granted else Decision.Busy
        }
    }

    fun release(memberId: String): Boolean = synchronized(lock) {
        val holder = _current
        if (holder is FloorState.Member && holder.id == memberId) {
            _current = FloorState.Free
            true
        } else {
            false
        }
    }

    /** Director presses the mic. Pre-empts any member, by design. */
    fun directorTake(): FloorState = synchronized(lock) {
        _current = FloorState.Director
        _current
    }

    fun directorRelease(): FloorState = synchronized(lock) {
        if (_current is FloorState.Director) _current = FloorState.Free
        _current
    }

    /** A member left or dropped; never leave the floor stuck. */
    fun memberLeft(memberId: String) = synchronized(lock) {
        val holder = _current
        if (holder is FloorState.Member && holder.id == memberId) _current = FloorState.Free
    }

    fun reset() = synchronized(lock) {
        _current = FloorState.Free
    }

    sealed interface Decision {
        data object Granted : Decision
        data object Busy : Decision
        data object DirectorTalking : Decision
    }
}
