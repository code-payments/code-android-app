package com.getcode.opencode.model.core

import com.getcode.vendor.Base58
import org.kin.sdk.base.tools.intToByteArray
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID


val UUID.bytes: List<Byte>
    get() {
        val bb = ByteBuffer.wrap(ByteArray(16))
        bb.putLong(mostSignificantBits)
        bb.putLong(leastSignificantBits)

        return bb.array().toList()
    }

val UUID.blockchainMemo: String
    get() {
        val type: Byte = 1
        val version: Byte = 0
        val flags = 0

        val data = mutableListOf<Byte>()

        data.add(type)
        data.add(version)
        data.addAll(flags.intToByteArray().toList())

        data.addAll(this.bytes.toList())

        return Base58.encode(data.toByteArray())
    }


val UUID.timestamp: Long?
    get() {
        return try {
            timestamp()
        } catch (e: Exception) {
            try {
                timestampV7
            } catch (e: Exception) {
                null
            }
        }
    }

private val UUID.timestampV7: Long?
    get() {
        return runCatching {
            val byteBuffer = ByteBuffer.wrap(bytes.toByteArray())
            val timestampBytes = ByteArray(8) { 0 } // Initialize with zeros
            byteBuffer.get(timestampBytes, 2, 6) // Copy the first 6 bytes from the UUID

            ByteBuffer.wrap(timestampBytes)
                .order(ByteOrder.BIG_ENDIAN)
                .long
        }.getOrNull()
    }