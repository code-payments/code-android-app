package com.getcode.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveSecretsTest {

    private val id = "4vJ9JU1bJJE96FWSJKvHsmmFADCg4gpZQff4P3bkLKi"

    @After
    fun tearDown() {
        LiveSecrets.release(id)
    }

    @Test
    fun `maskedSuffix keeps only the last six characters`() {
        assertEquals("…3bkLKi", id.maskedSuffix())
    }

    @Test
    fun `redact masks a held value and passes others through`() {
        LiveSecrets.hold(id)
        assertEquals("…3bkLKi", LiveSecrets.redact(id))
        assertEquals("other", LiveSecrets.redact("other"))
    }

    @Test
    fun `redact shows a released value in full`() {
        LiveSecrets.hold(id)
        LiveSecrets.release(id)
        assertEquals(id, LiveSecrets.redact(id))
    }
}
