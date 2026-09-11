package com.flipcash.libs.currency.math.loader

import com.flipcash.libs.currency.math.internal.loader.TableByteLoader
import java.io.File

class FileTableLoader(private val assetsDir: File) : TableByteLoader {

    constructor() : this(resolveAssetsDir())

    companion object {
        private fun resolveAssetsDir(): File {
            val projectRoot = findProjectRoot()
            return File(projectRoot, "libs/currency-math/src/main/assets")
        }

        private fun findProjectRoot(): File {
            var currentDir = File(System.getProperty("user.dir")!!).absoluteFile
            while (true) {
                val hasSettings = currentDir.listFiles { _, name ->
                    name == "settings.gradle" || name == "settings.gradle.kts"
                }?.isNotEmpty() == true
                if (hasSettings) {
                    return currentDir
                }
                val parent = currentDir.parentFile
                if (parent == null || parent == currentDir) {
                    throw IllegalStateException("Could not locate project root (no settings.gradle found)")
                }
                currentDir = parent
            }
        }
    }

    override suspend fun loadTableBytes(name: String): ByteArray {
        val file = File(assetsDir, "$name.bin")
        require(file.exists()) { "Could not find ${file.absolutePath}" }
        return file.readBytes()
    }
}
