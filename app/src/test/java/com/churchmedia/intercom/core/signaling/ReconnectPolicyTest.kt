package com.churchmedia.intercom.core.signaling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {

    @Test
    fun `early attempts retry`() {
        assertTrue(ReconnectPolicy.shouldRetry(1))
        assertTrue(ReconnectPolicy.shouldRetry(3))
        assertTrue(ReconnectPolicy.shouldRetry(ReconnectPolicy.MAX_ATTEMPTS - 1))
    }

    @Test
    fun `retries stop after the cap`() {
        assertFalse(ReconnectPolicy.shouldRetry(ReconnectPolicy.MAX_ATTEMPTS))
        assertFalse(ReconnectPolicy.shouldRetry(ReconnectPolicy.MAX_ATTEMPTS + 5))
    }

    @Test
    fun `backoff grows then caps at 8 seconds`() {
        assertEquals(500L, ReconnectPolicy.backoffMillis(1))
        assertEquals(1_000L, ReconnectPolicy.backoffMillis(2))
        assertEquals(2_000L, ReconnectPolicy.backoffMillis(3))
        assertEquals(8_000L, ReconnectPolicy.backoffMillis(5))
        assertEquals(8_000L, ReconnectPolicy.backoffMillis(50))
    }

    @Test
    fun `backoff never exceeds 8 seconds for out-of-range attempts`() {
        assertEquals(500L, ReconnectPolicy.backoffMillis(0))
        assertEquals(500L, ReconnectPolicy.backoffMillis(-3))
    }
}
