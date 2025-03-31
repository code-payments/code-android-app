package com.getcode.model

import com.getcode.utils.bytes
import com.getcode.utils.hexEncodedString
import java.nio.ByteBuffer
import java.util.UUID

typealias ID = List<Byte>

val NoId: ID = emptyList()

val RandomId: ID = UUID.randomUUID().bytes

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


