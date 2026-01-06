package com.flipcash.libs.currency.math.internal.loader

import android.content.Context
import com.flipcash.libs.currency.math.loader.TableLoader
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AndroidTableLoader(private val context: Context) : TableLoader {
    override fun loadTable(name: String): List<BigDecimal> {
        return context.assets.open("$name.bin").use { stream ->
            val bytes = stream.readBytes()
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