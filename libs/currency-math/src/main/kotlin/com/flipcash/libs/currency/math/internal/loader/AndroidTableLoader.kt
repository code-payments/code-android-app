package com.flipcash.libs.currency.math.internal.loader

import android.content.Context
import com.flipcash.libs.currency.math.loader.Table
import com.flipcash.libs.currency.math.loader.TableLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AndroidTableLoader(private val context: Context) : TableLoader {
    override suspend fun loadTable(name: String): Table = withContext(Dispatchers.IO) {
        context.assets.open("$name.bin").use { stream ->
            val bytes = stream.readBytes()
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