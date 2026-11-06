package com.churchmedia.intercom.ui

import com.churchmedia.intercom.core.model.FloorState
import com.churchmedia.intercom.core.model.LinkState
import com.churchmedia.intercom.core.model.Member
import com.churchmedia.intercom.core.model.Mode
import com.churchmedia.intercom.core.model.Role
import com.churchmedia.intercom.session.AppState
import com.churchmedia.intercom.ui.components.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorPresentationTest {

    private fun state(
        floor: FloorState,
        myId: String = "m-1",
        connected: Boolean = true,
        holderName: String? = null,
        members: List<Member> = listOf(
            Member("director", "Director", Role.DIRECTOR, connected = true),
            Member("m-1", "Camera B", Role.MEMBER, connected = true),
            Member("m-2", "Camera A", Role.MEMBER, connected = true),
        ),
    ): AppState = AppState(
        mode = Mode.MEMBER,
        link = if (connected) LinkState.CONNECTED else LinkState.IDLE,
        myId = myId,
        members = members,
        floor = floor,
    )

    @Test
    fun `director holding the floor says Director is speaking and disables the mic`() {
        val s = state(FloorState.Director)
        assertEquals("Director is speaking" to Tone.Accent, FloorPresentation.banner(s))
        assertFalse(FloorPresentation.canSpeak(s))
        assertEquals("Waiting for the microphone", FloorPresentation.micCaption(s))
    }

    @Test
    fun `another member holding the floor names them and disables the mic`() {
        val s = state(FloorState.Member("m-2"))
        assertEquals(
            "Camera A is currently speaking to the Director." to Tone.Warning,
            FloorPresentation.banner(s),
        )
        assertFalse(FloorPresentation.canSpeak(s))
        assertEquals("Waiting for the microphone", FloorPresentation.micCaption(s))
    }

    @Test
    fun `free floor is quiet and the mic is available`() {
        val s = state(FloorState.Free)
        assertEquals("The channel is quiet" to Tone.Neutral, FloorPresentation.banner(s))
        assertTrue(FloorPresentation.canSpeak(s))
        assertEquals("Tap to reply privately", FloorPresentation.micCaption(s))
    }

    @Test
    fun `self holding the floor shows live and allows stopping`() {
        val s = state(FloorState.Member("m-1"))
        assertEquals("You are live — the director hears you" to Tone.Live, FloorPresentation.banner(s))
        assertTrue(FloorPresentation.canSpeak(s))
        assertEquals("Tap to stop", FloorPresentation.micCaption(s))
    }

    @Test
    fun `disconnected member cannot speak regardless of floor`() {
        val s = state(FloorState.Free, connected = false)
        assertFalse(FloorPresentation.canSpeak(s))
        assertEquals("Waiting for the microphone", FloorPresentation.micCaption(s))
    }

    @Test
    fun `unknown holder falls back to a generic message`() {
        val s = state(FloorState.Member("m-99"), members = listOf(
            Member("director", "Director", Role.DIRECTOR, connected = true),
        ))
        val (text, _) = FloorPresentation.banner(s)
        assertTrue(text.contains("currently speaking to the Director."))
    }
}
