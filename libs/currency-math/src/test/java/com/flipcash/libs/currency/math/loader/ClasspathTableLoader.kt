package com.flipcash.libs.currency.math.loader

import java.nio.ByteBuffer
import java.nio.ByteOrder

class ClasspathTableLoader : TableLoader {
    override suspend fun loadTable(name: String): Table {
        val stream = javaClass.classLoader?.getResourceAsStream("$name.bin")
            ?: throw IllegalStateException("Could not find $name.bin in test resources")

        return stream.use {
            val bytes = it.readBytes()
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