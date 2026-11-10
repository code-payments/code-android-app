package com.getcode.utils

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Pins the JS base64 actual to RFC 4648, which android.util.Base64 and java.util.Base64 already
 * follow. JS-only: android.util.Base64 is a stub in host tests.
 */
class Base64VectorTest {
    // RFC 4648 section 10.
    private val rfc4648 = listOf(
        "" to "", "f" to "Zg==", "fo" to "Zm8=", "foo" to "Zm9v",
        "foob" to "Zm9vYg==", "fooba" to "Zm9vYmE=", "foobar" to "Zm9vYmFy",
    )

    @Test
    fun encodes_rfc4648_vectors_with_padding() {
        rfc4648.forEach { (plain, encoded) ->
            assertEquals(encoded, plain.encodeToByteArray().encodeBase64())
        }
    }

    @Test
    fun decodes_rfc4648_vectors() {
        rfc4648.forEach { (plain, encoded) ->
            assertEquals(plain, encoded.decodeBase64().decodeToString())
        }
    }

    @Test
    fun decodes_without_padding() {
        assertEquals("f", "Zg".decodeBase64().decodeToString())
        assertEquals("fooba", "Zm9vYmE".decodeBase64().decodeToString())
    }

    @Test
    fun url_safe_uses_dash_and_underscore() {
        val bytes = byteArrayOf(0xfb.toByte(), 0xff.toByte(), 0xbf.toByte())
        assertEquals("+/+/", bytes.encodeBase64())
        assertEquals("-_-_", bytes.encodeBase64(urlSafe = true))
        assertContentEquals(bytes, "-_-_".decodeBase64UrlSafe())
    }
}
