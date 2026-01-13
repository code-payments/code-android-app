package com.flipcash.libs.currency.math.loader

import com.flipcash.libs.currency.math.divideWithHighPrecision
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FileTableLoader(private val assetsDir: File) : TableLoader {

    constructor() : this(resolveAssetsDir())

    companion object {
        private fun resolveAssetsDir(): File {
            val projectRoot = findProjectRoot()
            return File(projectRoot, "src/main/assets")  // Adjust if module name differs
        }

        private fun findProjectRoot(): File {
            var currentDir = File(System.getProperty("user.dir")!!).absoluteFile
            while (true) {
                val gradleFiles = currentDir.listFiles { _, name ->
                    name == "settings.gradle" || name == "settings.gradle.kts" ||
                            name == "build.gradle" || name == "build.gradle.kts"
                }
                if (!gradleFiles.isNullOrEmpty()) {
                    return currentDir
                }
                val parent = currentDir.parentFile
                if (parent == null || parent == currentDir) {
                    throw IllegalStateException("Could not locate project root (no Gradle files found)")
                }
                currentDir = parent
            }
        }
    }

    override suspend fun loadTable(name: String): Table {
        val file = File(assetsDir, "$name.bin")
        require(file.exists()) { "Could not find ${file.absolutePath}" }

        return file.inputStream().use { input ->
            val bytes = input.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val count = bytes.size / 16

            val lowBits = LongArray(count)
            val highBits = LongArray(count)

            repeat(count) { i ->
                lowBits[i] = buffer.long
                highBits[i] = buffer.long
            }

            Table(lowBits, highBits)
        }
    }
}