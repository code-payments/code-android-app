package com.flipcash.libs.currency.math.internal.loader

import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

/** [FileTableLoader] for Node: the same walk up from the working directory, through `fs` and `path`. */
internal class NodeFileTableLoader : TableByteLoader {
    private val fs: dynamic = js("require('fs')")
    private val path: dynamic = js("require('path')")

    private val assetsDir: String by lazy {
        var dir: String = path.resolve(js("process.cwd()")) as String
        while (true) {
            if (fs.existsSync(path.join(dir, "settings.gradle.kts")) as Boolean ||
                fs.existsSync(path.join(dir, "settings.gradle")) as Boolean
            ) {
                return@lazy path.join(dir, "libs/currency-math/src/androidMain/assets") as String
            }
            val parent = path.dirname(dir) as String
            check(parent != dir) { "Could not locate project root (no settings.gradle found)" }
            dir = parent
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    override suspend fun loadTableBytes(name: String): ByteArray {
        val file = path.join(assetsDir, "$name.bin") as String
        require(fs.existsSync(file) as Boolean) { "Could not find $file" }
        val buffer: Uint8Array = fs.readFileSync(file).unsafeCast<Uint8Array>()
        return Int8Array(buffer.buffer, buffer.byteOffset, buffer.length).unsafeCast<ByteArray>().copyOf()
    }
}

internal actual fun sourceTreeTableLoader(): TableByteLoader = NodeFileTableLoader()
