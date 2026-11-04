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
    fun `a member leaving does not clear someone else`() {
        controller.request("m-1")
        controller.memberLeft("m-2")
        assertEquals(FloorState.Member("m-1"), controller.current)
    }
}
