package com.churchmedia.intercom.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionListTest {

    private fun reaction(id: String, from: String, to: String?, expiresAt: Long): Reaction =
        Reaction(id, from, from, to, createdAt = 0L, expiresAt = expiresAt)

    @Test
    fun `add increases the list size`() {
        val r = reaction("r1", "m-1", "director", 100L)
        val list = ReactionList.add(emptyList(), r)
        assertEquals(1, list.size)
        assertEquals(r, list.first())
    }

    @Test
    fun `prune drops expired reactions`() {
        val now = 1000L
        val expired = reaction("r1", "m-1", "director", 500L)
        val fresh = reaction("r2", "m-2", "director", 2000L)
        val pruned = ReactionList.prune(listOf(expired, fresh), now)
        assertEquals(1, pruned.size)
        assertEquals("r2", pruned.first().id)
    }

    @Test
    fun `prune keeps reactions that expire later`() {
        val now = 1000L
        val r = reaction("r1", "m-1", "director", 1001L)
        assertEquals(1, ReactionList.prune(listOf(r), now).size)
    }

    @Test
    fun `empty list stays empty`() {
        assertTrue(ReactionList.prune(emptyList(), 1000L).isEmpty())
    }

    @Test
    fun `forRecipient filters by recipient and expiry`() {
        val now = 1000L
        val r1 = reaction("r1", "director", "m-1", 2000L)
        val r2 = reaction("r2", "director", "m-2", 2000L)
        val r3 = reaction("r3", "director", "m-1", 500L) // expired
        val list = listOf(r1, r2, r3)
        val result = ReactionList.forRecipient(list, "m-1", now)
        assertEquals(1, result.size)
        assertEquals("r1", result.first().id)
    }

    @Test
    fun `fromSender filters by sender`() {
        val now = 1000L
        val r1 = reaction("r1", "m-1", "director", 2000L)
        val r2 = reaction("r2", "m-2", "director", 2000L)
        val result = ReactionList.fromSender(listOf(r1, r2), "m-1", now)
        assertEquals(1, result.size)
        assertEquals("r1", result.first().id)
    }

    @Test
    fun `multiple reactions from the same sender are preserved`() {
        val now = 1000L
        val r1 = reaction("r1", "m-1", "director", 2000L)
        val r2 = reaction("r2", "m-1", "director", 3000L)
        val result = ReactionList.fromSender(listOf(r1, r2), "m-1", now)
        assertEquals(2, result.size)
    }
}
