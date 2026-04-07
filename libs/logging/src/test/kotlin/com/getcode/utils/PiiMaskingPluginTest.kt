package com.getcode.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PiiMaskingPluginTest {
    private val plugin = PiiMaskingPlugin()

    @Test
    fun `masks email addresses`() {
        val line = "User logged in: john.doe@example.com"
        val result = plugin.process(line)
        assertNotNull(result)
        assertEquals("User logged in: [EMAIL]", result)
    }

    @Test
    fun `masks multiple emails in one line`() {
        val line = "From alice@foo.com to bob@bar.org"
        val result = plugin.process(line)
        assertNotNull(result)
        assertEquals("From [EMAIL] to [EMAIL]", result)
    }

    @Test
    fun `masks JWT tokens`() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abc123_signature"
        val line = "Auth token: $jwt"
        val result = plugin.process(line)
        assertNotNull(result)
        assertEquals("Auth token: [JWT]", result)
    }

    @Test
    fun `masks Solana public keys with first and last 4 chars`() {
        val key = "7EcDhSYGxXyscszYEp35KHN8vvw3svAuLKTzXwCFLtV"
        val line = "Sending to $key"
        val result = plugin.process(line)
        assertNotNull(result)
        assertEquals("Sending to [KEY:7EcD...FLtV]", result)
    }

    @Test
    fun `masks phone numbers`() {
        val line = "Phone: +14155551234"
        val result = plugin.process(line)
        assertNotNull(result)
        assertEquals("Phone: [PHONE]", result)
    }

    @Test
    fun `masks formatted phone numbers`() {
        val line = "Call (415) 555-1234 now"
        val result = plugin.process(line)
        assertNotNull(result)
        assertEquals("Call [PHONE] now", result)
    }

    @Test
    fun `preserves lines without PII`() {
        val line = "[2025-01-01T12:00:00] [D] [Navigation] Screen opened"
        val result = plugin.process(line)
        assertEquals(line, result)
    }

    @Test
    fun `masks multiple PII types in one line`() {
        val line = "User john@test.com called +14155551234"
        val result = plugin.process(line)
        assertNotNull(result)
        assertEquals("User [EMAIL] called [PHONE]", result)
    }

    @Test
    fun `never returns null`() {
        val result = plugin.process("")
        assertNotNull(result)
    }

    // --- Edge cases ---

    @Test
    fun `does not mask short base58 strings that are not keys`() {
        val line = "balance=500 token=abc"
        val result = plugin.process(line)
        assertEquals(line, result)
    }

    @Test
    fun `does not mask base58 strings under 32 chars`() {
        // 31 chars of valid base58
        val shortKey = "1A2B3C4D5E6F7G8H9J0K1L2M3N4P5Q"
        val line = "ref=$shortKey"
        val result = plugin.process(line)
        // Should not be masked — too short to be a Solana key
        assertEquals(line, result)
    }

    @Test
    fun `masks line that is entirely an email`() {
        val result = plugin.process("user@example.com")
        assertEquals("[EMAIL]", result)
    }

    @Test
    fun `masks PII in metadata key-value format`() {
        val line = "Login attempt | email=alice@corp.io, phone=+12025551234"
        val result = plugin.process(line)
        assertNotNull(result)
        assertEquals("Login attempt | email=[EMAIL], phone=[PHONE]", result)
    }

    @Test
    fun `does not mask timestamps as phone numbers`() {
        val line = "2025-01-15T08:30:00Z request completed"
        val result = plugin.process(line)
        assertEquals(line, result)
    }

    @Test
    fun `does not mask large numeric amounts as phone numbers`() {
        val line = "amount=1000000 kin"
        val result = plugin.process(line)
        assertEquals(line, result)
    }

    @Test
    fun `does not mask comma-formatted amounts as phone numbers`() {
        val line = "Total: 1,000,000.00 USD"
        val result = plugin.process(line)
        assertEquals(line, result)
    }

    @Test
    fun `masks international phone number with plus prefix`() {
        val line = "Sent SMS to +447911123456"
        val result = plugin.process(line)
        assertNotNull(result)
        assertEquals("Sent SMS to [PHONE]", result)
    }

    @Test
    fun `handles line with all PII types`() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig_here"
        val key = "7EcDhSYGxXyscszYEp35KHN8vvw3svAuLKTzXwCFLtV"
        val line = "user=test@x.com phone=+14155550000 key=$key jwt=$jwt"
        val result = plugin.process(line)
        assertNotNull(result)
        assertTrue(!result.contains("test@x.com"))
        assertTrue(!result.contains("+14155550000"))
        assertTrue(!result.contains(key))
        assertTrue(!result.contains("eyJ"))
        assertTrue(result.contains("[EMAIL]"))
        assertTrue(result.contains("[PHONE]"))
        assertTrue(result.contains("[KEY:"))
        assertTrue(result.contains("[JWT]"))
    }

    @Test
    fun `preserves log structure around masked values`() {
        val line = "[2025-04-07T10:30:00] [D] [Auth] Login for user@test.com succeeded"
        val result = plugin.process(line)
        assertNotNull(result)
        assertTrue(result.startsWith("["))
        assertTrue(result.contains("[D]"))
        assertTrue(result.contains("[Auth]"))
        assertTrue(result.contains("[EMAIL]"))
        assertTrue(result.endsWith("succeeded"))
    }

    @Test
    fun `handles emails with plus addressing`() {
        val line = "user+tag@example.com"
        val result = plugin.process(line)
        assertEquals("[EMAIL]", result)
    }

    @Test
    fun `handles consecutive Solana keys`() {
        val key1 = "7EcDhSYGxXyscszYEp35KHN8vvw3svAuLKTzXwCFLtV"
        val key2 = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM"
        val line = "from=$key1 to=$key2"
        val result = plugin.process(line)
        assertNotNull(result)
        assertTrue(result.contains("[KEY:7EcD...FLtV]"))
        assertTrue(result.contains("[KEY:9WzD...AWWM]"))
    }
}
