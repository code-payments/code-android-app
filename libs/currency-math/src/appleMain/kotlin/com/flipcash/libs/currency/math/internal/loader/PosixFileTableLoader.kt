package com.flipcash.libs.currency.math.internal.loader

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.access
import platform.posix.F_OK
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.getcwd
import platform.posix.getenv

/**
 * [FileTableLoader] for Kotlin/Native. The iOS simulator runs tests with an unrelated working
 * directory, so the build passes the assets directory in `FLIPCASH_CURVE_ASSETS`; without it this
 * walks up from the working directory to `settings.gradle.kts`, as the JVM loader does.
 */
@OptIn(ExperimentalForeignApi::class)
internal class PosixFileTableLoader : TableByteLoader {
    private val assetsDir: String by lazy {
        getenv("FLIPCASH_CURVE_ASSETS")?.toKString()?.let { return@lazy it }
        var dir = ByteArray(4096).usePinned { buf -> getcwd(buf.addressOf(0), 4096u)?.toKString() }
            ?: error("getcwd failed")
        while (true) {
            if (access("$dir/settings.gradle.kts", F_OK) == 0 || access("$dir/settings.gradle", F_OK) == 0) {
                return@lazy "$dir/libs/currency-math/src/androidMain/assets"
            }
            val parent = dir.substringBeforeLast('/', "").ifEmpty { "/" }
            check(parent != dir) { "Could not locate project root (no settings.gradle found)" }
            dir = parent
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    override suspend fun loadTableBytes(name: String): ByteArray {
        val path = "$assetsDir/$name.bin"
        val file = fopen(path, "rb") ?: throw IllegalArgumentException("Could not find $path")
        try {
            fseek(file, 0, SEEK_END)
            val size = ftell(file).toInt()
            fseek(file, 0, SEEK_SET)
            val bytes = ByteArray(size)
            val read = bytes.usePinned { fread(it.addressOf(0), 1u, size.toULong(), file) }
            check(read.toInt() == size) { "short read of $path: $read of $size bytes" }
            return bytes
        } finally {
            fclose(file)
        }
    }
}

internal actual fun sourceTreeTableLoader(): TableByteLoader = PosixFileTableLoader()
