@file:JsModule("@grpc/grpc-js")
@file:JsNonModule

package com.getcode.opencode.transport

// Minimal external declarations for the parts of @grpc/grpc-js the transport uses.

external interface ChannelCredentials

external object credentials {
    fun createSsl(): ChannelCredentials
}

external class Metadata

external class Client(address: String, credentials: ChannelCredentials, options: dynamic = definedExternally) {
    fun makeUnaryRequest(
        method: String,
        serialize: (dynamic) -> dynamic,
        deserialize: (dynamic) -> dynamic,
        argument: dynamic,
        metadata: Metadata,
        options: dynamic,
        callback: (error: dynamic, value: dynamic) -> Unit,
    ): dynamic

    fun makeBidiStreamRequest(
        method: String,
        serialize: (dynamic) -> dynamic,
        deserialize: (dynamic) -> dynamic,
        metadata: Metadata,
        options: dynamic,
    ): dynamic

    fun close()
}
