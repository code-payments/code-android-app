package com.getcode.opencode.client

import com.getcode.opencode.transport.GrpcTransport
import com.getcode.opencode.transport.JvmGrpcTransport

internal actual fun liveTransport(host: String): GrpcTransport? = JvmGrpcTransport(host)

internal actual fun liveEnv(name: String): String? = System.getenv(name)
