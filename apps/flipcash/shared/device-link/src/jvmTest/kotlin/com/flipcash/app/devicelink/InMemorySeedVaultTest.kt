package com.flipcash.app.devicelink

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemorySeedVaultTest {
    private val seed = "c2VlZC1mb3ItdGVzdHMtb25seQ=="

    @Test
    fun `vault round-trips through the fake`() = runBlocking {
        val vault = InMemorySeedVault()
        assertNull(vault.read())
        vault.write(seed)
        assertEquals(seed, vault.read())
        vault.clear()
        assertNull(vault.read())
    }
}

internal class InMemorySeedVault : SeedVault {
    private var value: String? = null
    override suspend fun read() = value
    override suspend fun write(entropyB64: String) { value = entropyB64 }
    override suspend fun clear() { value = null }
}
