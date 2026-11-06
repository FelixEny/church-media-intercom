package com.churchmedia.intercom.core.audio

import com.churchmedia.intercom.core.model.OutputDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioRoutingPolicyTest {

    private fun device(id: Int, kind: OutputDevice.Kind, label: String = "dev$id"): OutputDevice =
        OutputDevice(id, label, kind)

    @Test
    fun `empty device list yields no route`() {
        assertNull(AudioRoutingPolicy.bestRoute(emptyList(), null, false))
    }

    @Test
    fun `wired wins over USB, Bluetooth and speaker`() {
        val devices = listOf(
            device(1, OutputDevice.Kind.BLUETOOTH),
            device(2, OutputDevice.Kind.USB),
            device(3, OutputDevice.Kind.SPEAKER),
            device(4, OutputDevice.Kind.WIRED),
        )
        assertEquals(4, AudioRoutingPolicy.bestRoute(devices, null, false))
    }

    @Test
    fun `USB wins over Bluetooth and speaker when no wired is present`() {
        val devices = listOf(
            device(1, OutputDevice.Kind.BLUETOOTH),
            device(2, OutputDevice.Kind.SPEAKER),
            device(3, OutputDevice.Kind.USB),
        )
        assertEquals(3, AudioRoutingPolicy.bestRoute(devices, null, false))
    }

    @Test
    fun `Bluetooth wins over speaker when no wired or USB is present`() {
        val devices = listOf(
            device(1, OutputDevice.Kind.SPEAKER),
            device(2, OutputDevice.Kind.BLUETOOTH),
        )
        assertEquals(2, AudioRoutingPolicy.bestRoute(devices, null, false))
    }

    @Test
    fun `speaker is the fallback when nothing external is connected`() {
        val devices = listOf(device(1, OutputDevice.Kind.SPEAKER))
        assertEquals(1, AudioRoutingPolicy.bestRoute(devices, null, false))
    }

    @Test
    fun `user override wins while the preferred device is still connected`() {
        val devices = listOf(
            device(1, OutputDevice.Kind.WIRED),
            device(2, OutputDevice.Kind.SPEAKER),
        )
        assertEquals(2, AudioRoutingPolicy.bestRoute(devices, preferredId = 2, userOverride = true))
    }

    @Test
    fun `user override is dropped when the preferred device disconnects`() {
        val devices = listOf(
            device(1, OutputDevice.Kind.WIRED),
            device(2, OutputDevice.Kind.SPEAKER),
        )
        // preferred id 99 is gone, so we fall back to the priority order
        assertEquals(1, AudioRoutingPolicy.bestRoute(devices, preferredId = 99, userOverride = true))
    }

    @Test
    fun `no override means priority order even with a preferred id present`() {
        val devices = listOf(
            device(1, OutputDevice.Kind.WIRED),
            device(2, OutputDevice.Kind.SPEAKER),
        )
        assertEquals(1, AudioRoutingPolicy.bestRoute(devices, preferredId = 2, userOverride = false))
    }
}
