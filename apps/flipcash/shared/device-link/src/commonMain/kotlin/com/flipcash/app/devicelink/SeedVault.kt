package com.flipcash.app.devicelink

/**
 * Where a signed-in device keeps its account seed (base64 entropy, as `UserManager.establish` takes
 * it). Desktop backs it with the OS keyring; Android still keeps the seed in `AuthManager`.
 */
interface SeedVault {
    suspend fun read(): String?
    suspend fun write(entropyB64: String)
    suspend fun clear()
}
