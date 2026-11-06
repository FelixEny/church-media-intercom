package com.churchmedia.intercom.core.signaling

import com.churchmedia.intercom.core.model.FloorState
import com.churchmedia.intercom.core.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {

    @Test
    fun `client messages round trip`() {
        val messages = listOf(
            ClientMessage.Hello(code = "4821", displayName = "Ada"),
            ClientMessage.Answer("v=0\r\nanswer"),
            ClientMessage.Ice(mid = "0", mLine = 0, candidate = "candidate:1 1 UDP"),
            ClientMessage.FloorRequest,
            ClientMessage.FloorRelease,
            ClientMessage.Mic(true),
            ClientMessage.Ping(1234L),
            ClientMessage.Bye,
            ClientMessage.ThumbsUp,
        )

        messages.forEach { original ->
            val decoded = decodeClientMessage(original.encode())
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `server messages round trip`() {
        val roster = listOf(
            MemberDto(id = "director", name = "Sam", role = ROLE_DIRECTOR, micLive = true),
            MemberDto(id = "m-1", name = "Ada", role = ROLE_MEMBER),
        )

        val messages = listOf(
            ServerMessage.Welcome("m-1", "4821", "Sam", roster, FloorDto(HOLDER_DIRECTOR)),
            ServerMessage.Roster(roster, FloorDto(null)),
            ServerMessage.Offer("v=0\r\noffer"),
            ServerMessage.Ice("0", 0, "candidate"),
            ServerMessage.Floor(FloorDto("m-1")),
            ServerMessage.Pong(1L, 2L),
            ServerMessage.Bye("Session ended"),
            ServerMessage.Error("Busy"),
            ServerMessage.Reject("Wrong session code"),
            ServerMessage.ThumbsUp("director", "Felix", "m-1"),
        )

        messages.forEach { original ->
            val decoded = decodeServerMessage(original.encode())
            assertEquals(original, decoded)
        }
    }

    @Test
    fun `unknown message types are dropped rather than crashing`() {
        assertNull(decodeClientMessage("""{"t":"something-new","x":1}"""))
        assertNull(decodeServerMessage("""{"t":"something-new","x":1}"""))
        assertNull(decodeClientMessage("not json at all"))
    }

    @Test
    fun `extra fields from a newer client are ignored`() {
        val decoded = decodeClientMessage(
            """{"t":"hello","protocol":1,"code":"4821","displayName":"Ada","futureField":true}""",
        )
        assertTrue(decoded is ClientMessage.Hello)
        assertEquals("4821", (decoded as ClientMessage.Hello).code)
    }

    @Test
    fun `floor state maps to and from the wire format`() {
        assertEquals(FloorDto(null), FloorState.Free.toDto())
        assertEquals(FloorDto(HOLDER_DIRECTOR), FloorState.Director.toDto())
        assertEquals(FloorDto("m-9"), FloorState.Member("m-9").toDto())

        assertEquals(FloorState.Free, FloorDto(null).toModel())
        assertEquals(FloorState.Director, FloorDto(HOLDER_DIRECTOR).toModel())
        assertEquals(FloorState.Member("m-9"), FloorDto("m-9").toModel())
    }

    @Test
    fun `member dto maps to the domain model`() {
        val member = MemberDto(id = "m-1", name = "Ada", role = ROLE_MEMBER, micLive = true).toModel()
        assertEquals("m-1", member.id)
        assertEquals(Role.MEMBER, member.role)
        assertTrue(member.micLive)
    }
}
