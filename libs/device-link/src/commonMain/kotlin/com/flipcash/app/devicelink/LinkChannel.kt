package com.flipcash.app.devicelink

import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readInt
import io.ktor.utils.io.writeByteArray
import io.ktor.utils.io.writeInt

/** Length-prefixed frames over a TCP socket. The first byte of each frame is its [Frame] type. */
internal class LinkChannel(private val socket: Socket) {
    private val input = socket.openReadChannel()
    private val output = socket.openWriteChannel(autoFlush = false)

    suspend fun send(type: Byte, body: ByteArray = ByteArray(0)) {
        output.writeInt(body.size + 1)
        output.writeByteArray(byteArrayOf(type) + body)
        output.flush()
    }

    /** The next frame, or null when the peer closed the connection or sent something malformed. */
    suspend fun receive(): Pair<Byte, ByteArray>? = runCatching {
        val size = input.readInt()
        if (size !in 1..MAX_FRAME) return null
        val frame = input.readByteArray(size)
        frame[0] to frame.copyOfRange(1, frame.size)
    }.getOrNull()

    fun close() = socket.close()

    private companion object {
        const val MAX_FRAME = 4096
    }
}

internal object Frame {
    const val HELLO: Byte = 1
    const val SEED: Byte = 2
    const val ACK: Byte = 3
    const val CANCEL: Byte = 4
    const val PROTOCOL_VERSION: Byte = 1
}
