package com.flipcash.shared.common.ui

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BlurHashTest {

    // A valid 4x3-component hash from the BlurHash reference test vectors.
    private val validHash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj"

    @Test
    fun `decodes a valid hash to a bitmap of the requested size`() {
        val bitmap = BlurHash.decode(validHash, width = 32, height = 24)
        assertNotNull(bitmap)
        assertEquals(32, bitmap.width)
        assertEquals(24, bitmap.height)
    }

    @Test
    fun `returns null for null or blank hashes`() {
        assertNull(BlurHash.decode(null, 16, 16))
        assertNull(BlurHash.decode("", 16, 16))
    }

    @Test
    fun `returns null for a hash whose declared component count doesn't match its length`() {
        // Truncated hash: the size flag promises more components than the string carries.
        assertNull(BlurHash.decode(validHash.substring(0, validHash.length - 4), 16, 16))
    }

    @Test
    fun `returns null for non-positive dimensions`() {
        assertNull(BlurHash.decode(validHash, width = 0, height = 16))
        assertNull(BlurHash.decode(validHash, width = 16, height = -1))
    }

    @Test
    fun `returns null for hashes containing characters outside the base-83 alphabet`() {
        // 'é' is not part of the BlurHash alphabet.
        val invalid = "L" + "é".repeat(validHash.length - 1)
        assertNull(BlurHash.decode(invalid, 16, 16))
    }
}
