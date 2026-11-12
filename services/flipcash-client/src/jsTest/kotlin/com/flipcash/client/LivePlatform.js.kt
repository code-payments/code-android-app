package com.flipcash.client

import com.getcode.opencode.transport.GrpcTransport
import com.getcode.opencode.transport.NodeGrpcTransport

internal actual fun liveEnv(name: String): String? = js("process.env[name]") as String?

internal actual fun liveTransport(host: String): GrpcTransport? = NodeGrpcTransport(host)

internal actual fun writeLiveFile(path: String, bytes: ByteArray) {
    val fs = js("require('fs')")
    val pathModule = js("require('path')")
    fs.mkdirSync(pathModule.dirname(path), js("({ recursive: true })"))
    fs.writeFileSync(path, js("Buffer").from(bytes.unsafeCast<org.khronos.webgl.Int8Array>()))
}

internal actual val livePlatform: String = "js"
