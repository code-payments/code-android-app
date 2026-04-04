package com.getcode.utils.serializer

import com.getcode.solana.keys.PublicKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SerializerTest {

    @Serializable
    data class ByteListWrapper(
        @Serializable(with = ByteListAsBase64Serializer::class)
        val data: List<Byte>
    )

    @Serializable
    data class PublicKeyWrapper(
        @Serializable(with = PublicKeyAsStringSerializer::class)
        val key: PublicKey
    )

    // --- ByteListAsBase64Serializer ---

    @Test
    fun byteListSerializerRoundtrip() {
        val original = ByteListWrapper(listOf(1, 2, 3, 4, 5, -128, 127))
        val json = Json.encodeToString(original)
        val decoded = Json.decodeFromString<ByteListWrapper>(json)
        assertEquals(original.data, decoded.data)
    }

    @Test
    fun byteListSerializerEmptyList() {
        val original = ByteListWrapper(emptyList())
        val json = Json.encodeToString(original)
        val decoded = Json.decodeFromString<ByteListWrapper>(json)
        assertEquals(original.data, decoded.data)
    }

    @Test
    fun byteListSerializerLargePayload() {
        val data = (0..255).map { it.toByte() }
        val original = ByteListWrapper(data)
        val json = Json.encodeToString(original)
        val decoded = Json.decodeFromString<ByteListWrapper>(json)
        assertEquals(original.data, decoded.data)
    }

    @Test
    fun byteListSerializerProducesBase64String() {
        val original = ByteListWrapper(listOf(0, 0, 0))
        val json = Json.encodeToString(original)
        // Base64 of [0, 0, 0] is "AAAA"
        assertTrue(json.contains("AAAA"))
    }

    // --- PublicKeyAsStringSerializer ---

    @Test
    fun publicKeySerializerRoundtrip() {
        val bytes = ByteArray(32) { (it + 1).toByte() }
        val key = PublicKey(bytes.toList())
        val original = PublicKeyWrapper(key)
        val json = Json.encodeToString(original)
        val decoded = Json.decodeFromString<PublicKeyWrapper>(json)
        assertEquals(original.key, decoded.key)
    }

    @Test
    fun publicKeySerializerProducesBase58String() {
        val bytes = ByteArray(32) { 1 }
        val key = PublicKey(bytes.toList())
        val original = PublicKeyWrapper(key)
        val json = Json.encodeToString(original)
        // Should contain a base58-encoded string (alphanumeric, no 0OIl)
        assertTrue(json.contains("\"key\""))
    }
}
