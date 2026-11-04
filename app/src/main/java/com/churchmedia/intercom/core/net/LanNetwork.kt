package com.churchmedia.intercom.core.net

import java.net.Inet4Address
import java.net.NetworkInterface

/** Local-network helpers. No internet access is used or required. */
object LanNetwork {

    /**
     * Best guess at this device's LAN IPv4 address. Prefers a Wi-Fi interface and
     * ignores loopback, cellular, and virtual tunnel addresses.
     */
    fun ipv4(): String? {
        val candidates = runCatching {
            NetworkInterface.getNetworkInterfaces()
                .asSequence()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { nic ->
                    nic.inetAddresses.asSequence().map { nic.name to it }
                }
                .filter { (_, address) -> address is Inet4Address && address.isSiteLocalAddress }
                .toList()
        }.getOrNull() ?: return null

        val preferred = candidates.firstOrNull { (name, _) ->
            name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan")
        }
        return (preferred ?: candidates.firstOrNull())?.second?.hostAddress
    }

    /** 4-digit session code. Short enough to read out loud, long enough to stop accidents. */
    fun newSessionCode(): String = (1000..9999).random().toString()

    fun normaliseHost(input: String): String = input
        .trim()
        .removePrefix("ws://")
        .removePrefix("http://")
        .substringBefore("/")
        .substringBefore(":")
        .ifBlank { "" }
}
