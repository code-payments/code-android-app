package com.getcode.opencode.client

import com.getcode.opencode.transport.GrpcTransport
import com.getcode.opencode.transport.NodeGrpcTransport

internal actual fun liveTransport(host: String): GrpcTransport? = NodeGrpcTransport(host)

internal actual fun liveEnv(name: String): String? = js("process.env[name]") as String?
