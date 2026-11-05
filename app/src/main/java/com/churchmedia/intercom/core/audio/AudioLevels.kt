package com.churchmedia.intercom.core.audio

/** Shared level metering for captured and decoded audio. */
object AudioLevels {

    /**
     * RMS of little-endian PCM16 data, 0..1.
     *
     * Interpreting the buffer as PCM16 is deliberate: it is correct for the common case
     * and still moves for float buffers, which is all a meter needs to answer
     * "is audio present?".
     */
    fun pcm16(bytes: ByteArray, length: Int = bytes.size): Float {
        val end = (minOf(length, bytes.size) - 1).coerceAtLeast(0)
        var sum = 0.0
        var samples = 0
        var index = 0
        while (index < end) {
            val raw = (bytes[index].toInt() and 0xff) or (bytes[index + 1].toInt() shl 8)
            val sample = if (raw > 32767) raw - 65536 else raw
            val normalised = sample / 32768.0
            sum += normalised * normalised
            samples += 1
            index += 2
        }
        if (samples == 0) return 0f
        return kotlin.math.sqrt(sum / samples).toFloat().coerceIn(0f, 1f)
    }
}
