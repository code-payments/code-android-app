package com.flipcash.app.devicelink

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [SeedVault] in the OS keyring: Keychain on macOS, Credential Manager on Windows, Secret Service on
 * Linux, through one library. jvmMain because each of those is a native API with no common surface.
 */
class KeyringSeedVault(
    private val service: String = "com.flipcash.desktop",
    private val account: String = "seed",
) : SeedVault {
    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        Keyring.create().use { keyring ->
            try {
                keyring.getPassword(service, account)
            } catch (_: PasswordAccessException) {
                null
            }
        }
    }

    override suspend fun write(entropyB64: String) = withContext(Dispatchers.IO) {
        Keyring.create().use { it.setPassword(service, account, entropyB64) }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        Keyring.create().use { keyring ->
            try {
                keyring.deletePassword(service, account)
            } catch (_: PasswordAccessException) {
            }
        }
    }
}
