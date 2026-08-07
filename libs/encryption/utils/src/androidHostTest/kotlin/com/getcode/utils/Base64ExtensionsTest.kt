package com.getcode.utils

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Base64ExtensionsTest {

    // --- ByteArray.base64 ---

    @Test
    fun byteArrayBase64Roundtrip() {
        val data = byteArrayOf(1, 2, 3, 4, 5, 127, -128)
        val encoded = data.base64
        val decoded = encoded.decodeBase64()
        assertTrue(data.contentEquals(decoded))
    }

    @Test
    fun byteArrayBase64EmptyArray() {
        val data = byteArrayOf()
        val encoded = data.base64
        val decoded = encoded.decodeBase64()
        assertTrue(data.contentEquals(decoded))
    }

    // --- List<Byte>.base64 ---

    @Test
    fun byteListBase64Roundtrip() {
        val data = listOf<Byte>(10, 20, 30, -1, -128)
        val encoded = data.base64
        val decoded = encoded.decodeBase64().toList()
        assertEquals(data, decoded)
    }

    // --- encodeBase64 / decodeBase64 ---

    @Test
    fun encodeBase64DefaultRoundtrip() {
        val data = "Hello, World!".toByteArray()
        val encoded = data.encodeBase64()
        val decoded = encoded.decodeBase64()
        assertTrue(data.contentEquals(decoded))
    }

    @Test
    fun encodeBase64UrlSafe() {
        // URL-safe base64 uses - and _ instead of + and /
        val data = byteArrayOf(-1, -2, -3, -4) // bytes that produce + and / in standard base64
        val urlSafe = data.encodeBase64(urlSafe = true)
        assertTrue(!urlSafe.contains('+') && !urlSafe.contains('/'))
    }

    @Test
    fun encodeBase64UrlSafeRoundtrip() {
        val data = byteArrayOf(0, 127, -128, 63, -1)
        val encoded = data.encodeBase64(urlSafe = true)
        // URL-safe decode: replace back and decode
        val standard = encoded.replace('-', '+').replace('_', '/')
        val decoded = standard.decodeBase64()
        assertTrue(data.contentEquals(decoded))
    }

    // --- encodeBase64ToArray ---

    @Test
    fun encodeBase64ToArrayRoundtrip() {
        val data = byteArrayOf(10, 20, 30, 40, 50)
        val encodedBytes = data.encodeBase64ToArray()
        val decoded = encodedBytes.decodeBase64()
        assertTrue(data.contentEquals(decoded))
    }

    // --- String.decodeBase64 ---

    @Test
    fun stringDecodeBase64() {
        val original = "Test string for Base64"
        val encoded = original.toByteArray().base64
        val decoded = encoded.decodeBase64()
        assertEquals(original, String(decoded, Charsets.UTF_8))
    }

    // --- ByteArray.decodeBase64 (from ByteArray input) ---

    @Test
    fun byteArrayDecodeBase64() {
        val data = byteArrayOf(1, 2, 3, 4)
        val encoded = data.encodeBase64ToArray()
        val decoded = encoded.decodeBase64()
        assertTrue(data.contentEquals(decoded))
    }

    // --- Large data roundtrip ---

    @Test
    fun largeDataRoundtrip() {
        val data = ByteArray(1024) { it.toByte() }
        val encoded = data.base64
        val decoded = encoded.decodeBase64()
        assertTrue(data.contentEquals(decoded))
    }

    // --- base58 on List<Byte> and ByteArray ---

    @Test
    fun byteListBase58Roundtrip() {
        val data = listOf<Byte>(1, 2, 3, 4, 5)
        val encoded = data.base58
        val decoded = encoded.decodeBase58()
        assertTrue(data.toByteArray().contentEquals(decoded))
    }

    @Test
    fun byteArrayBase58Roundtrip() {
        val data = byteArrayOf(10, 20, 30)
        val encoded = data.base58
        val decoded = encoded.decodeBase58()
        assertTrue(data.contentEquals(decoded))
    }
}
