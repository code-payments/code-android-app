package com.getcode.utils

import com.getcode.vendor.Base58
import org.kin.sdk.base.tools.intToByteArray
import java.nio.ByteBuffer
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

val List<Byte>.toUuid: UUID
    get() {
        val byteBuffer = ByteBuffer.wrap(this.toByteArray())
        val high = byteBuffer.getLong()
        val low = byteBuffer.getLong()
        return UUID(high, low)
    }