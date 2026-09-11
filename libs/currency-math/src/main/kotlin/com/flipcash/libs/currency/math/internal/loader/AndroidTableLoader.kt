package com.flipcash.libs.currency.math.internal.loader

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads a curve table's raw `.bin` bytes for [com.flipcash.libs.currency.math.internal.curves.DiscreteBondingCurve.initialize]
 * to hand to the shared KMP engine. Kept as an interface (rather than the concrete
 * [AndroidTableLoader]) so the JVM-only unit test suite -- which has no Android [Context] -- can
 * satisfy the same initialization path via `FileTableLoader`/`ClasspathTableLoader`.
 */
internal interface TableByteLoader {
    suspend fun loadTableBytes(name: String): ByteArray
}

internal class AndroidTableLoader(private val context: Context) : TableByteLoader {
    override suspend fun loadTableBytes(name: String): ByteArray = withContext(Dispatchers.IO) {
        context.assets.open("$name.bin").use { it.readBytes() }
    }
}
