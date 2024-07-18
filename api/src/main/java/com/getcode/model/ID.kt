package com.getcode.model

import com.getcode.network.repository.hexEncodedString
import java.nio.ByteBuffer
import java.util.UUID

typealias ID = List<Byte>

val ID.uuid: UUID?
    get() {
        if (size != 16) return null

        val byteBuffer = ByteBuffer.wrap(this.toByteArray())
        val high = byteBuffer.getLong()
        val low = byteBuffer.getLong()
        return UUID(high, low)
    }

val ID.description: String
    get() = uuid?.toString() ?: hexEncodedString()



