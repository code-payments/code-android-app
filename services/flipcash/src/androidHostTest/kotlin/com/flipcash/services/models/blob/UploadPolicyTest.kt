package com.flipcash.services.models.blob

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class UploadPolicyTest {

    private fun constraint(pattern: String, maxSize: Long = 1_000) =
        MimeTypeConstraints(mimeTypePattern = pattern, maxSizeBytes = maxSize, image = null)

    private fun policy(vararg constraints: MimeTypeConstraints) =
        UploadPolicy(version = "v1", ttl = 1.hours, mimeTypeConstraints = constraints.toList())

    @Test
    fun `exact pattern matches only that type, case-insensitively`() {
        val c = constraint("image/jpeg")
        assertTrue(c.matches("image/jpeg"))
        assertTrue(c.matches("IMAGE/JPEG"))
        assertFalse(c.matches("image/png"))
    }

    @Test
    fun `subtype wildcard matches same top-level type only`() {
        val c = constraint("image/*")
        assertTrue(c.matches("image/png"))
        assertTrue(c.matches("image/jpeg"))
        assertFalse(c.matches("video/mp4"))
    }

    @Test
    fun `catch-all matches anything`() {
        val c = constraint("*/*")
        assertTrue(c.matches("image/png"))
        assertTrue(c.matches("application/pdf"))
    }

    @Test
    fun `constraintsFor returns the first matching entry (most-specific-first)`() {
        val exact = constraint("image/png", maxSize = 100)
        val wildcard = constraint("image/*", maxSize = 999)
        val p = policy(exact, wildcard)

        assertSame(exact, p.constraintsFor("image/png"))
        assertSame(wildcard, p.constraintsFor("image/jpeg"))
    }

    @Test
    fun `constraintsFor is null for an unsupported type`() {
        val p = policy(constraint("image/*"))
        assertNull(p.constraintsFor("video/mp4"))
    }

    @Test
    fun `accepts reflects constraintsFor`() {
        val p = policy(constraint("image/*"))
        assertTrue(p.accepts("image/png"))
        assertFalse(p.accepts("text/plain"))
    }

    @Test
    fun `explicitMimeTypes excludes wildcard patterns`() {
        val p = policy(
            constraint("image/jpeg"),
            constraint("image/png"),
            constraint("image/*"),
            constraint("*/*"),
        )
        assertEquals(listOf("image/jpeg", "image/png"), p.explicitMimeTypes)
    }
}
