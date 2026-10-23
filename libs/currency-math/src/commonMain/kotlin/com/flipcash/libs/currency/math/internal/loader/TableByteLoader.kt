package com.flipcash.libs.currency.math.internal.loader

internal interface TableByteLoader {
    suspend fun loadTableBytes(name: String): ByteArray
}
