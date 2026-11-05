package com.churchmedia.intercom.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/**
 * Binds the app's sockets to the Wi-Fi network.
 *
 * Android routes an app's traffic over the *default* network, which is usually the one
 * with validated internet. A church LAN often has no internet at all, so mobile data
 * wins the default and every LAN socket gets marked to cellular: TCP sits in SYN-SENT
 * forever, and UDP media has no route. Binding the process to the Wi-Fi network is what
 * makes the app work on an internet-less LAN.
 */
class LanNetworkBinder(context: Context) {

    private val connectivity: ConnectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private var bound: Network? = null

    /** @return the Wi-Fi network now in use, or null when no Wi-Fi network exists. */
    fun bindToWifi(): Network? {
        val wifi = connectivity.allNetworks.firstOrNull { network ->
            val capabilities: NetworkCapabilities =
                connectivity.getNetworkCapabilities(network) ?: return@firstOrNull false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } ?: return null

        val applied = runCatching { connectivity.bindProcessToNetwork(wifi) }.getOrDefault(false)
        if (applied) bound = wifi
        return if (applied) wifi else null
    }

    fun unbind() {
        if (bound == null) return
        runCatching { connectivity.bindProcessToNetwork(null) }
        bound = null
    }

    fun isBound(): Boolean = bound != null
}
