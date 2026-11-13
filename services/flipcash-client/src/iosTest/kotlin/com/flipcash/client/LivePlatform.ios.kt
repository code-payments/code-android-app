package com.flipcash.client

import com.getcode.opencode.transport.GrpcTransport
import platform.posix.getenv
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString

@OptIn(ExperimentalForeignApi::class)
internal actual fun liveEnv(name: String): String? = getenv(name)?.toKString()

/** No Apple transport yet: the live test skips here. */
internal actual fun liveTransport(host: String): GrpcTransport? = null

internal actual fun liveReadFile(path: String): String = error("no live run on iOS")

internal actual fun writeLiveFile(path: String, bytes: ByteArray) = Unit

internal actual val livePlatform: String = "ios"
