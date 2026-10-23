package com.flipcash.libs.currency.math.loader

import com.flipcash.libs.currency.math.internal.loader.TableByteLoader

class ClasspathTableLoader : TableByteLoader {
    override suspend fun loadTableBytes(name: String): ByteArray {
        val stream = javaClass.classLoader?.getResourceAsStream("$name.bin")
            ?: throw IllegalStateException("Could not find $name.bin in test resources")
        return stream.use { it.readBytes() }
    }
}
