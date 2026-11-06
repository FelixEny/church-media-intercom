package com.churchmedia.intercom.core.audio

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection

/**
 * Polls WebRTC statistics and logs the audio flow direction.
 *
 * This is the diagnostic that separates "the director is not sending" from "the member
 * is not receiving": outbound bytes on the host and inbound bytes on the member tell
 * you exactly where the audio stops.
 */
class AudioStatsPoller(private val tag: String) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /** Bytes seen on the last poll, used to log a delta instead of a cumulative total. */
    private var lastOutbound: Long = -1L
    private var lastInbound: Long = -1L

    fun start(connection: PeerConnection) {
        stop()
        job = scope.launch {
            while (true) {
                delay(2_000)
                runCatching {
                    connection.getStats { report ->
                        var outbound: Long = -1
                        var inbound: Long = -1
                        var outLevel = "n/a"
                        var inLevel = "n/a"

                        report.statsMap.values.forEach { stats ->
                            when (stats.type) {
                                "outbound-rtp" -> {
                                    (stats.members["bytesSent"] as? Number)?.let { outbound = it.toLong() }
                                    (stats.members["audioLevel"] as? Number)?.let { outLevel = "%.3f".format(it.toFloat()) }
                                }
                                "inbound-rtp" -> {
                                    (stats.members["bytesReceived"] as? Number)?.let { inbound = it.toLong() }
                                    (stats.members["audioLevel"] as? Number)?.let { inLevel = "%.3f".format(it.toFloat()) }
                                }
                            }
                        }

                        val outDelta = if (lastOutbound < 0 || outbound < 0) "?" else (outbound - lastOutbound).toString()
                        val inDelta = if (lastInbound < 0 || inbound < 0) "?" else (inbound - lastInbound).toString()
                        lastOutbound = outbound
                        lastInbound = inbound

                        Log.i(
                            "COMMS_STATS",
                            "$tag out=${if (outbound < 0) "?" else outbound.toString()}(+${outDelta}) " +
                                "in=${if (inbound < 0) "?" else inbound.toString()}(+${inDelta}) " +
                                "outLvl=$outLevel inLvl=$inLevel",
                        )
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        lastOutbound = -1
        lastInbound = -1
    }
}
