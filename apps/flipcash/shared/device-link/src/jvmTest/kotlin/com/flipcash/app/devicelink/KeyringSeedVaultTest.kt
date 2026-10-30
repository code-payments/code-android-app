package com.flipcash.app.devicelink

import kotlinx.coroutines.runBlocking
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Writes a random throwaway value to the real OS keyring under a test-only service name and deletes
 * it. Opt-in (`-Pflipcash.keyringTest`), because CI machines may have no unlocked keyring and a
 * locked one can block on a prompt.
 */
class KeyringSeedVaultTest {
    @Test
    fun `round-trips through the OS keyring`() = runBlocking {
        if (System.getProperty("flipcash.keyringTest") != "true") return@runBlocking
        val vault = KeyringSeedVault(service = "com.flipcash.desktop.test", account = "throwaway")
        val value = Base64.encode(Random.nextBytes(16))
        try {
            vault.write(value)
            assertEquals(value, vault.read())
        } finally {
            vault.clear()
        }
        assertNull(vault.read())
    }
}
