package com.flipcash.libs.currency.math.loader

import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ClasspathTableLoader : TableLoader {
    override fun loadTable(name: String): List<BigDecimal> {
        val stream = javaClass.classLoader?.getResourceAsStream("$name.bin")
            ?: throw IllegalStateException("Could not find $name.bin in test resources")

        return stream.use {
            val bytes = it.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val count = bytes.size / 16

            (0 until count).map {
                val low = buffer.long
                val high = buffer.long
                bigDecimalFromParts(low, high)
            }
        }
    }
}