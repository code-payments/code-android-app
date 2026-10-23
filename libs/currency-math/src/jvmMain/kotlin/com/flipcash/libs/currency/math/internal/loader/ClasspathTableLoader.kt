package com.flipcash.libs.currency.math.internal.loader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ClasspathTableLoader : TableByteLoader {
    override suspend fun loadTableBytes(name: String): ByteArray = withContext(Dispatchers.IO) {
        val stream = ClasspathTableLoader::class.java.getResourceAsStream("/$name.bin")
            ?: error("Missing curve table $name.bin on the classpath")
        stream.use { it.readBytes() }
    }
}
