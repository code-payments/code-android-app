package com.getcode.opencode.client

import com.getcode.opencode.transport.GrpcTransport

/** No gRPC transport on Apple yet; the live tests skip. */
internal actual fun liveTransport(host: String): GrpcTransport? = null

internal actual fun liveEnv(name: String): String? = null
