package com.flipcash.app.internal.time

import android.os.SystemClock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Minimal, dependency-free SNTP (RFC 4330) client used purely for diagnostics — measuring how far
 * the device wall clock has drifted from real time.
 *
 * NTP is unauthenticated and has no timestamp/replay check, so this works even when the device clock
 * is wrong enough that backend auth rejects it with `INVALID_TIMESTAMP` — which is exactly the case
 * we want to detect. Best-effort: returns `null` on any failure. Must be called off the main thread.
 */
internal object SntpClient {

    private const val NTP_PORT = 123
    private const val NTP_PACKET_SIZE = 48
    private const val NTP_MODE_CLIENT = 3
    private const val NTP_VERSION = 3
    private const val NTP_MODE_SERVER = 4
    private const val NTP_LEAP_NOSYNC = 3
    private const val NTP_STRATUM_MAX = 15

    // Seconds between the NTP epoch (1900) and the Unix epoch (1970).
    private const val OFFSET_1900_TO_1970 = 2_208_988_800L

    private const val INDEX_ORIGINATE_TIME = 24
    private const val INDEX_RECEIVE_TIME = 32
    private const val INDEX_TRANSMIT_TIME = 40

    /**
     * Returns the NTP clock offset in milliseconds (`trueTime - deviceTime`): positive means the
     * device clock is BEHIND real time. Returns `null` on any failure (no network, timeout,
     * malformed response).
     */
    fun queryClockOffsetMillis(host: String = "time.google.com", timeoutMs: Int = 3_000): Long? {
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val address = InetAddress.getByName(host)
                val buffer = ByteArray(NTP_PACKET_SIZE)
                buffer[0] = (NTP_MODE_CLIENT or (NTP_VERSION shl 3)).toByte()

                val requestTime = System.currentTimeMillis()
                val requestTicks = SystemClock.elapsedRealtime()
                writeTimestamp(buffer, INDEX_TRANSMIT_TIME, requestTime)

                socket.send(DatagramPacket(buffer, buffer.size, address, NTP_PORT))
                socket.receive(DatagramPacket(buffer, buffer.size))
                val responseTicks = SystemClock.elapsedRealtime()

                // t4: estimated local time the response arrived, measured via the monotonic clock so
                // a mid-flight wall-clock change can't corrupt the round trip.
                val responseTime = requestTime + (responseTicks - requestTicks)

                val leap = (buffer[0].toInt() shr 6) and 0x3
                val mode = buffer[0].toInt() and 0x7
                val stratum = buffer[1].toInt() and 0xff
                if (leap == NTP_LEAP_NOSYNC || mode != NTP_MODE_SERVER || stratum < 1 || stratum > NTP_STRATUM_MAX) {
                    return null
                }

                val originateTime = readTimestamp(buffer, INDEX_ORIGINATE_TIME) // t1 (echoed)
                val receiveTime = readTimestamp(buffer, INDEX_RECEIVE_TIME)     // t2
                val transmitTime = readTimestamp(buffer, INDEX_TRANSMIT_TIME)   // t3

                // Clock offset = ((t2 - t1) + (t3 - t4)) / 2
                ((receiveTime - originateTime) + (transmitTime - responseTime)) / 2
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeTimestamp(buffer: ByteArray, offset: Int, timeMillis: Long) {
        val seconds = timeMillis / 1_000L + OFFSET_1900_TO_1970
        val milliseconds = timeMillis % 1_000L
        buffer[offset] = (seconds shr 24).toByte()
        buffer[offset + 1] = (seconds shr 16).toByte()
        buffer[offset + 2] = (seconds shr 8).toByte()
        buffer[offset + 3] = seconds.toByte()
        val fraction = milliseconds * 0x1_0000_0000L / 1_000L
        buffer[offset + 4] = (fraction shr 24).toByte()
        buffer[offset + 5] = (fraction shr 16).toByte()
        buffer[offset + 6] = (fraction shr 8).toByte()
        buffer[offset + 7] = fraction.toByte()
    }

    private fun readTimestamp(buffer: ByteArray, offset: Int): Long {
        val seconds = readUnsigned32(buffer, offset)
        val fraction = readUnsigned32(buffer, offset + 4)
        return (seconds - OFFSET_1900_TO_1970) * 1_000L + (fraction * 1_000L) / 0x1_0000_0000L
    }

    private fun readUnsigned32(buffer: ByteArray, offset: Int): Long {
        val b0 = buffer[offset].toLong() and 0xff
        val b1 = buffer[offset + 1].toLong() and 0xff
        val b2 = buffer[offset + 2].toLong() and 0xff
        val b3 = buffer[offset + 3].toLong() and 0xff
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }
}
