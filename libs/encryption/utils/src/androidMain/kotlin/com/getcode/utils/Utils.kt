package com.getcode.utils

/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** A collection of various utility methods that are helpful for working with the Bitcoin protocol. */
object Utils {
    const val TAG = "Api Utils"

    /** Hex encoder for use throughout the framework. */
    @JvmField
    val HEX = HexEncoder()

    class HexEncoder {
        private val hexChars = "0123456789abcdef".toCharArray()

        fun encode(data: ByteArray): String {
            val result = CharArray(data.size * 2)
            for (i in data.indices) {
                result[i * 2] = hexChars[(data[i].toInt() shr 4) and 0xF]
                result[i * 2 + 1] = hexChars[data[i].toInt() and 0xF]
            }
            return String(result)
        }
    }

    /** Joins an iterable of objects with a space separator. */
    @JvmStatic
    fun spaceJoin(parts: Iterable<*>): String = parts.joinToString(" ")

    /** Max initial size of variable-length arrays to guard against memory exhaustion attacks. */
    const val MAX_INITIAL_ARRAY_LENGTH = 20

    /**
     * Encodes a positive BigInteger as a fixed-length big-endian byte array.
     * This is the antagonist to [bytesToBigInteger].
     */
    @JvmStatic
    fun bigIntegerToBytes(b: BigInteger, numBytes: Int): ByteArray {
        require(b.signum() >= 0) { "b must be positive or zero" }
        require(numBytes > 0) { "numBytes must be positive" }
        val src = b.toByteArray()
        val dest = ByteArray(numBytes)
        val isFirstByteOnlyForSign = src[0] == 0.toByte()
        val length = if (isFirstByteOnlyForSign) src.size - 1 else src.size
        require(length <= numBytes) { "The given number does not fit in $numBytes" }
        val srcPos = if (isFirstByteOnlyForSign) 1 else 0
        val destPos = numBytes - length
        System.arraycopy(src, srcPos, dest, destPos, length)
        return dest
    }

    /** Converts a big-endian byte array to a positive BigInteger. Antagonist of [bigIntegerToBytes]. */
    @JvmStatic
    fun bytesToBigInteger(bytes: ByteArray): BigInteger = BigInteger(1, bytes)

    /** Writes 2 bytes as an unsigned 16-bit integer in little-endian format. */
    @JvmStatic
    fun uint16ToByteArrayLE(value: Int, out: ByteArray, offset: Int) {
        out[offset] = (value and 0xFF).toByte()
        out[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    /** Writes 4 bytes as an unsigned 32-bit integer in little-endian format. */
    @JvmStatic
    fun uint32ToByteArrayLE(value: Long, out: ByteArray, offset: Int) {
        out[offset] = (value and 0xFF).toByte()
        out[offset + 1] = ((value shr 8) and 0xFF).toByte()
        out[offset + 2] = ((value shr 16) and 0xFF).toByte()
        out[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    /** Writes 4 bytes as an unsigned 32-bit integer in big-endian format. */
    @JvmStatic
    fun uint32ToByteArrayBE(value: Long, out: ByteArray, offset: Int) {
        out[offset] = ((value shr 24) and 0xFF).toByte()
        out[offset + 1] = ((value shr 16) and 0xFF).toByte()
        out[offset + 2] = ((value shr 8) and 0xFF).toByte()
        out[offset + 3] = (value and 0xFF).toByte()
    }

    /** Writes 8 bytes as a signed 64-bit integer in little-endian format. */
    @JvmStatic
    fun int64ToByteArrayLE(value: Long, out: ByteArray, offset: Int) {
        out[offset] = (value and 0xFF).toByte()
        out[offset + 1] = ((value shr 8) and 0xFF).toByte()
        out[offset + 2] = ((value shr 16) and 0xFF).toByte()
        out[offset + 3] = ((value shr 24) and 0xFF).toByte()
        out[offset + 4] = ((value shr 32) and 0xFF).toByte()
        out[offset + 5] = ((value shr 40) and 0xFF).toByte()
        out[offset + 6] = ((value shr 48) and 0xFF).toByte()
        out[offset + 7] = ((value shr 56) and 0xFF).toByte()
    }

    /** Writes 2 bytes as an unsigned 16-bit integer in little-endian format to a stream. */
    @JvmStatic
    @Throws(IOException::class)
    fun uint16ToByteStreamLE(value: Int, stream: OutputStream) {
        stream.write(value and 0xFF)
        stream.write((value shr 8) and 0xFF)
    }

    /** Writes 2 bytes as an unsigned 16-bit integer in big-endian format to a stream. */
    @JvmStatic
    @Throws(IOException::class)
    fun uint16ToByteStreamBE(value: Int, stream: OutputStream) {
        stream.write((value shr 8) and 0xFF)
        stream.write(value and 0xFF)
    }

    /** Writes 4 bytes as an unsigned 32-bit integer in little-endian format to a stream. */
    @JvmStatic
    @Throws(IOException::class)
    fun uint32ToByteStreamLE(value: Long, stream: OutputStream) {
        stream.write((value and 0xFF).toInt())
        stream.write(((value shr 8) and 0xFF).toInt())
        stream.write(((value shr 16) and 0xFF).toInt())
        stream.write(((value shr 24) and 0xFF).toInt())
    }

    /** Writes 4 bytes as an unsigned 32-bit integer in big-endian format to a stream. */
    @JvmStatic
    @Throws(IOException::class)
    fun uint32ToByteStreamBE(value: Long, stream: OutputStream) {
        stream.write(((value shr 24) and 0xFF).toInt())
        stream.write(((value shr 16) and 0xFF).toInt())
        stream.write(((value shr 8) and 0xFF).toInt())
        stream.write((value and 0xFF).toInt())
    }

    /** Writes 8 bytes as a signed 64-bit integer in little-endian format to a stream. */
    @JvmStatic
    @Throws(IOException::class)
    fun int64ToByteStreamLE(value: Long, stream: OutputStream) {
        stream.write((value and 0xFF).toInt())
        stream.write(((value shr 8) and 0xFF).toInt())
        stream.write(((value shr 16) and 0xFF).toInt())
        stream.write(((value shr 24) and 0xFF).toInt())
        stream.write(((value shr 32) and 0xFF).toInt())
        stream.write(((value shr 40) and 0xFF).toInt())
        stream.write(((value shr 48) and 0xFF).toInt())
        stream.write(((value shr 56) and 0xFF).toInt())
    }

    /** Writes 8 bytes as an unsigned 64-bit integer in little-endian format to a stream. */
    @JvmStatic
    @Throws(IOException::class)
    fun uint64ToByteStreamLE(value: BigInteger, stream: OutputStream) {
        var bytes = value.toByteArray()
        require(bytes.size <= 8) { "Input too large to encode into a uint64" }
        bytes = reverseBytes(bytes)
        stream.write(bytes)
        repeat(8 - bytes.size) { stream.write(0) }
    }

    /** Parses 2 bytes as an unsigned 16-bit integer in little-endian format. */
    @JvmStatic
    fun readUint16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    /** Parses 4 bytes as an unsigned 32-bit integer in little-endian format. */
    @JvmStatic
    fun readUint32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFFL) or
                ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
                ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
                ((bytes[offset + 3].toLong() and 0xFFL) shl 24)

    /** Parses 8 bytes as a signed 64-bit integer in little-endian format. */
    @JvmStatic
    fun readInt64(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFFL) or
                ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
                ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
                ((bytes[offset + 3].toLong() and 0xFFL) shl 24) or
                ((bytes[offset + 4].toLong() and 0xFFL) shl 32) or
                ((bytes[offset + 5].toLong() and 0xFFL) shl 40) or
                ((bytes[offset + 6].toLong() and 0xFFL) shl 48) or
                ((bytes[offset + 7].toLong() and 0xFFL) shl 56)

    /** Parses 4 bytes as an unsigned 32-bit integer in big-endian format. */
    @JvmStatic
    fun readUint32BE(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xFFL) shl 24) or
                ((bytes[offset + 1].toLong() and 0xFFL) shl 16) or
                ((bytes[offset + 2].toLong() and 0xFFL) shl 8) or
                (bytes[offset + 3].toLong() and 0xFFL)

    /** Parses 2 bytes as an unsigned 16-bit integer in big-endian format. */
    @JvmStatic
    fun readUint16BE(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    /** Parses 2 bytes from a stream as an unsigned 16-bit integer in little-endian format. */
    @JvmStatic
    fun readUint16FromStream(stream: InputStream): Int =
        try {
            (stream.read() and 0xFF) or ((stream.read() and 0xFF) shl 8)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    /** Parses 4 bytes from a stream as an unsigned 32-bit integer in little-endian format. */
    @JvmStatic
    fun readUint32FromStream(stream: InputStream): Long =
        try {
            (stream.read().toLong() and 0xFFL) or
                    ((stream.read().toLong() and 0xFFL) shl 8) or
                    ((stream.read().toLong() and 0xFFL) shl 16) or
                    ((stream.read().toLong() and 0xFFL) shl 24)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    /** Returns a copy of the given byte array in reverse order. */
    @JvmStatic
    fun reverseBytes(bytes: ByteArray): ByteArray {
        val buf = ByteArray(bytes.size)
        for (i in bytes.indices) buf[i] = bytes[bytes.size - 1 - i]
        return buf
    }

    /**
     * Decodes an MPI-encoded number. MPI encoded numbers consist of a 4-byte big-endian length field
     * followed by the number in big-endian format with a sign bit.
     * @param hasLength if false, the 4-byte length prefix is absent
     */
    @JvmStatic
    fun decodeMPI(mpi: ByteArray, hasLength: Boolean): BigInteger {
        val buf: ByteArray = if (hasLength) {
            val length = readUint32BE(mpi, 0).toInt()
            mpi.copyOfRange(4, 4 + length)
        } else {
            mpi
        }
        if (buf.isEmpty()) return BigInteger.ZERO
        val isNegative = (buf[0].toInt() and 0x80) == 0x80
        if (isNegative) buf[0] = (buf[0].toInt() and 0x7F).toByte()
        val result = BigInteger(buf)
        return if (isNegative) result.negate() else result
    }

    /**
     * Encodes a BigInteger in MPI format. MPI encoded numbers consist of a 4-byte big-endian
     * length field followed by the number in big-endian format with a sign bit.
     * @param includeLength if true, prepends the 4-byte length field
     */
    @JvmStatic
    fun encodeMPI(value: BigInteger, includeLength: Boolean): ByteArray {
        if (value == BigInteger.ZERO) {
            return if (!includeLength) ByteArray(0) else byteArrayOf(0, 0, 0, 0)
        }
        val isNegative = value.signum() < 0
        var array = if (isNegative) value.negate().toByteArray() else value.toByteArray()
        var length = array.size
        if ((array[0].toInt() and 0x80) == 0x80) length++
        return if (includeLength) {
            val result = ByteArray(length + 4)
            System.arraycopy(array, 0, result, length - array.size + 3, array.size)
            uint32ToByteArrayBE(length.toLong(), result, 0)
            if (isNegative) result[4] = (result[4].toInt() or 0x80).toByte()
            result
        } else {
            if (length != array.size) {
                val result = ByteArray(length)
                System.arraycopy(array, 0, result, 1, array.size)
                array = result
            }
            if (isNegative) array[0] = (array[0].toInt() or 0x80).toByte()
            array
        }
    }

    /**
     * Decodes the Bitcoin "compact bits" difficulty encoding.
     * @see encodeCompactBits
     */
    @JvmStatic
    fun decodeCompactBits(compact: Long): BigInteger {
        val size = ((compact shr 24) and 0xFF).toInt()
        val bytes = ByteArray(4 + size)
        bytes[3] = size.toByte()
        if (size >= 1) bytes[4] = ((compact shr 16) and 0xFF).toByte()
        if (size >= 2) bytes[5] = ((compact shr 8) and 0xFF).toByte()
        if (size >= 3) bytes[6] = (compact and 0xFF).toByte()
        return decodeMPI(bytes, true)
    }

    /**
     * Encodes a BigInteger in Bitcoin's "compact bits" difficulty format.
     * @see decodeCompactBits
     */
    @JvmStatic
    fun encodeCompactBits(value: BigInteger): Long {
        var result: Long
        var size = value.toByteArray().size
        result = if (size <= 3) value.toLong() shl (8 * (3 - size))
        else value.shiftRight(8 * (size - 3)).toLong()
        if ((result and 0x00800000L) != 0L) {
            result = result shr 8
            size++
        }
        result = result or (size.toLong() shl 24)
        result = result or (if (value.signum() == -1) 0x00800000L else 0L)
        return result
    }

    /** If non-null, overrides the return value of [now]. */
    @Volatile
    private var mockTime: Date? = null

    /** Advances (or rewinds) the mock clock by the given number of seconds. */
    @JvmStatic
    fun rollMockClock(seconds: Int): Date = rollMockClockMillis(seconds * 1000L)

    /** Advances (or rewinds) the mock clock by the given number of milliseconds. */
    @JvmStatic
    fun rollMockClockMillis(millis: Long): Date {
        val current = mockTime ?: throw IllegalStateException("You need to use setMockClock() first.")
        return Date(current.time + millis).also { mockTime = it }
    }

    /** Sets the mock clock to the current time. */
    @JvmStatic
    fun setMockClock() {
        mockTime = Date()
    }

    /** Sets the mock clock to the given time (in seconds since epoch). */
    @JvmStatic
    fun setMockClock(mockClockSeconds: Long) {
        mockTime = Date(mockClockSeconds * 1000)
    }

    /** Clears the mock clock. */
    @JvmStatic
    fun resetMocking() {
        mockTime = null
    }

    /** Returns the current time, or a mocked equivalent. */
    @JvmStatic
    fun now(): Date = mockTime ?: Date()

    /** Returns the current time in milliseconds since the epoch, or a mocked equivalent. */
    @JvmStatic
    fun currentTimeMillis(): Long = mockTime?.time ?: System.currentTimeMillis()

    /** Returns the current time in seconds since the epoch, or a mocked equivalent. */
    @JvmStatic
    fun currentTimeSeconds(): Long = currentTimeMillis() / 1000

    private val UTC = TimeZone.getTimeZone("UTC")

    /** Formats a date to an ISO 8601 string. */
    @JvmStatic
    fun dateTimeFormat(dateTime: Date): String {
        val iso8601 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        iso8601.timeZone = UTC
        return iso8601.format(dateTime)
    }

    /** Formats a unix time in milliseconds to an ISO 8601 string. */
    @JvmStatic
    fun dateTimeFormat(dateTime: Long): String {
        val iso8601 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        iso8601.timeZone = UTC
        return iso8601.format(dateTime)
    }

    private val bitMask = intArrayOf(0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80)

    /** Checks if the given bit is set in data using little-endian bit order. */
    @JvmStatic
    fun checkBitLE(data: ByteArray, index: Int): Boolean =
        (data[index ushr 3].toInt() and bitMask[7 and index]) != 0

    /** Sets the given bit in data to 1 using little-endian bit order. */
    @JvmStatic
    fun setBitLE(data: ByteArray, index: Int) {
        data[index ushr 3] = (data[index ushr 3].toInt() or bitMask[7 and index]).toByte()
    }

    private enum class Runtime { ANDROID, OPENJDK, ORACLE_JAVA }
    private enum class OS { LINUX, WINDOWS, MAC_OS }

    private val runtime: Runtime?
    private val os: OS?

    init {
        val runtimeProp = (System.getProperty("java.runtime.name") ?: "").lowercase(Locale.US)
        runtime = when {
            runtimeProp.isEmpty() -> null
            runtimeProp.contains("android") -> Runtime.ANDROID
            runtimeProp.contains("openjdk") -> Runtime.OPENJDK
            runtimeProp.contains("java(tm) se") -> Runtime.ORACLE_JAVA
            else -> { trace("Unknown java.runtime.name '$runtimeProp'", tag = TAG); null }
        }
        val osProp = (System.getProperty("os.name") ?: "").lowercase(Locale.US)
        os = when {
            osProp.isEmpty() -> null
            osProp.contains("linux") -> OS.LINUX
            osProp.contains("win") -> OS.WINDOWS
            osProp.contains("mac") -> OS.MAC_OS
            else -> { trace("Unknown os.name '$osProp'", tag = TAG); null }
        }
    }

    @JvmStatic fun isAndroidRuntime(): Boolean = runtime == Runtime.ANDROID
    @JvmStatic fun isOpenJDKRuntime(): Boolean = runtime == Runtime.OPENJDK
    @JvmStatic fun isOracleJavaRuntime(): Boolean = runtime == Runtime.ORACLE_JAVA
    @JvmStatic fun isLinux(): Boolean = os == OS.LINUX
    @JvmStatic fun isWindows(): Boolean = os == OS.WINDOWS
    @JvmStatic fun isMac(): Boolean = os == OS.MAC_OS

    /** Encodes a stack of byte arrays as a space-separated hex string surrounded by brackets. */
    @JvmStatic
    fun toString(stack: List<ByteArray>): String =
        stack.joinToString(" ") { "[${HEX.encode(it)}]" }
}
