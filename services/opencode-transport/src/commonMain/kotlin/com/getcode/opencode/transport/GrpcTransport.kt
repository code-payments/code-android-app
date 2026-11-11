package com.getcode.opencode.transport

import kotlinx.coroutines.flow.Flow

/**
 * The gRPC calls the shared client code makes, in terms of method paths and serialized messages.
 *
 * Message encoding stays in common code (Wire); each platform supplies the wire transport. Node uses
 * `@grpc/grpc-js` ([NodeGrpcTransport] in jsMain). Android keeps its grpc-kotlin stubs for now; a
 * JVM actual would wrap a `ManagedChannel` with `ClientCalls` and a byte-array marshaller.
 */
interface GrpcTransport {
    /** Sends one request to [method] (`/package.Service/Method`) and returns the one response. */
    suspend fun unary(method: String, request: ByteArray): ByteArray

    /**
     * Opens a bidirectional stream on [method]. Each element of [requests] is written to the
     * stream; each server message is emitted. Cancelling collection cancels the call.
     */
    fun bidiStream(method: String, requests: Flow<ByteArray>): Flow<ByteArray>

    /** Releases the underlying connection. */
    fun close()
}

/** A call that ended with a non-OK gRPC status. [code] is the numeric `grpc.Status` code. */
class GrpcStatusException(val code: Int, message: String) : Exception(message)
