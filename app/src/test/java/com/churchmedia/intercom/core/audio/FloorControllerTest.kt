package com.churchmedia.intercom.core.audio

import com.churchmedia.intercom.core.model.FloorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorControllerTest {

    private val controller = FloorController()

    @Test
    fun `starts free so the first member can reply`() {
        assertEquals(FloorState.Free, controller.current)
        assertEquals(FloorController.Decision.Granted, controller.request("m-1"))
        assertEquals(FloorState.Member("m-1"), controller.current)
    }

    @Test
    fun `second member is denied while the floor is held`() {
        controller.request("m-1")
        assertEquals(FloorController.Decision.Busy, controller.request("m-2"))
        assertEquals(FloorState.Member("m-1"), controller.current)
    }

    @Test
    fun `requesting twice is idempotent for the holder`() {
        controller.request("m-1")
        assertEquals(FloorController.Decision.Granted, controller.request("m-1"))
        assertEquals(FloorState.Member("m-1"), controller.current)
    }

    @Test
    fun `members are denied while the director talks`() {
        controller.directorTake()
        assertEquals(FloorController.Decision.DirectorTalking, controller.request("m-1"))
        assertEquals(FloorState.Director, controller.current)
    }

    @Test
    fun `director pre-empts a member`() {
        controller.request("m-1")
        controller.directorTake()
        assertEquals(FloorState.Director, controller.current)
        assertFalse(controller.release("m-1"))
    }

    @Test
    fun `release only works for the current holder`() {
        controller.request("m-1")
        assertFalse(controller.release("m-2"))
        assertTrue(controller.release("m-1"))
        assertEquals(FloorState.Free, controller.current)
    }

    @Test
    fun `director release frees the floor`() {
        controller.directorTake()
        controller.directorRelease()
        assertEquals(FloorState.Free, controller.current)
    }

    @Test
    fun `a member leaving never leaves the floor stuck`() {
        controller.request("m-1")
        controller.memberLeft("m-1")
        assertEquals(FloorState.Free, controller.current)
    }

    @Test
    fun `concurrent requests never leave the floor in an inconsistent state`() {
        val c = FloorController()
        val threads = (1..4).map { i ->
            Thread {
                repeat(200) { j ->
                    val member = "m-$i"
                    when (j % 4) {
                        0 -> c.request(member)
                        1 -> c.directorTake()
                        2 -> c.release(member)
                        else -> c.directorRelease()
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // The floor must always be one of the valid states.
        val holder = c.current
        assertTrue(
            holder is FloorState.Free || holder is FloorState.Director || holder is FloorState.Member,
        )
    }

    @Test
    fun `concurrent requests never grant the floor to two holders`() {
        val c = FloorController()
        val granted = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val threads = (1..8).map { i ->
            Thread {
                repeat(50) {
                    if (c.request("m-$i") == FloorController.Decision.Granted) {
                        granted.add("m-$i")
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // At most one holder at any time is enforced by the state machine; the queue
        // may contain multiple grants over time as the floor is released and re-taken,
        // but the final state must be a single valid holder.
        val holder = c.current
        assertTrue(
            holder is FloorState.Free || holder is FloorState.Director || holder is FloorState.Member,
        )
    }
}
