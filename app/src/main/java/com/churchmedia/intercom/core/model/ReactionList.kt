package com.churchmedia.intercom.core.model

/**
 * Pure bookkeeping for transient reactions. Scheduling the expiry is the caller's
 * job (coroutine delay); this object only manipulates the list.
 */
object ReactionList {

    /** Adds [reaction] to the list, keeping the most recent first. */
    fun add(current: List<Reaction>, reaction: Reaction): List<Reaction> =
        listOf(reaction) + current

    /** Drops reactions whose expiry is at or before [now]. */
    fun prune(current: List<Reaction>, now: Long): List<Reaction> =
        current.filter { it.expiresAt > now }

    /** Active reactions for a given side/recipient. */
    fun active(current: List<Reaction>, now: Long): List<Reaction> = prune(current, now)

    /** Reactions addressed to [recipientId] (or broadcasts). */
    fun forRecipient(current: List<Reaction>, recipientId: String, now: Long): List<Reaction> =
        prune(current, now).filter { it.toId == recipientId || it.toId == null }

    /** Reactions sent by [senderId]. */
    fun fromSender(current: List<Reaction>, senderId: String, now: Long): List<Reaction> =
        prune(current, now).filter { it.fromId == senderId }
}
