package com.flipcash.client

import com.getcode.opencode.transport.GrpcTransport
import java.io.File

internal actual fun liveEnv(name: String): String? = System.getenv(name)

internal actual fun liveTransport(host: String): GrpcTransport? = JvmGrpcTransport(host)

internal actual fun writeLiveFile(path: String, bytes: ByteArray) {
    File(path).apply { parentFile?.mkdirs() }.writeBytes(bytes)
}

internal actual val livePlatform: String = "jvm"
