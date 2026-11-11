package com.flipcash.libs.currency.math.internal.loader

internal interface TableByteLoader {
    suspend fun loadTableBytes(name: String): ByteArray
}

/**
 * Reads the tables from this repo's source tree (`libs/currency-math/src/androidMain/assets`),
 * found by walking up from the working directory to `settings.gradle.kts`. For tests.
 */
internal expect fun sourceTreeTableLoader(): TableByteLoader
