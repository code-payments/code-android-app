package com.flipcash.shared.chat.internal

import com.flipcash.services.models.chat.ChatType
import com.getcode.opencode.model.core.ID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Vectors mirror the server's `MustDeriveDmChatID(chatType, a, b)`
 * (flipcash2-server `chat/model.go`): SHA-256 over a per-type domain followed by
 * the unsigned-lexicographically sorted set of the two 16-byte user IDs, with a
 * self-pair collapsed to a single member. CONTACT_DM hashes under the bare
 * legacy domain `"flipcash:chat:dm"`; TIP_DM appends its enum value
 * (`"flipcash:chat:dm:2"`). The server rejects any intent whose chat id doesn't
 * match, so these bytes are a cross-platform wire contract. The TIP_DM vectors
 * are the exact iOS `TipDmChatIDTests` vectors.
 */
class ChatIdGeneratorTest {

    private val generator = ChatIdGenerator()

    // UUID 11111111-2222-3333-4444-555555555555
    private val userA: ID = "11111111222233334444555555555555".hexToId()
    // UUID aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee
    private val userB: ID = "aaaaaaaabbbbccccddddeeeeeeeeeeee".hexToId()

    @Test
    fun `TIP_DM derives the server vector for a sorted pair`() {
        val id = generator.generate(ChatType.TIP_DM, userA, userB)
        assertEquals(
            "8b2f0e5da9fa050dc0040fb23dc0aa0028a0c5d9fca36b50cd3c163be3cb09e7",
            id.bytes.toHex(),
        )
    }

    @Test
    fun `TIP_DM self-pair collapses to a single member`() {
        val id = generator.generate(ChatType.TIP_DM, userA, userA)
        assertEquals(
            "525de420ef8a70e1cf1483090f937d7563a7bbe7501558ab5f6aae59780c3af2",
            id.bytes.toHex(),
        )
    }

    @Test
    fun `CONTACT_DM derives the server vector for a sorted pair`() {
        val id = generator.generate(ChatType.CONTACT_DM, userA, userB)
        assertEquals(
            "542acadd83bb3ae2c341b5f058bbbdcf001258707634f2e2281811e52e3c6263",
            id.bytes.toHex(),
        )
    }

    @Test
    fun `CONTACT_DM self-pair collapses to a single member`() {
        val id = generator.generate(ChatType.CONTACT_DM, userA, userA)
        assertEquals(
            "4c15eb24c70f19b8266fa9ac7d32f63aaf6500b4346a3567d45a2533da72d139",
            id.bytes.toHex(),
        )
    }

    @Test
    fun `argument order does not change the id`() {
        assertEquals(
            generator.generate(ChatType.CONTACT_DM, userA, userB),
            generator.generate(ChatType.CONTACT_DM, userB, userA),
        )
        assertEquals(
            generator.generate(ChatType.TIP_DM, userA, userB),
            generator.generate(ChatType.TIP_DM, userB, userA),
        )
    }

    @Test
    fun `domain separation makes CONTACT_DM and TIP_DM ids differ for the same pair`() {
        assertNotEquals(
            generator.generate(ChatType.CONTACT_DM, userA, userB),
            generator.generate(ChatType.TIP_DM, userA, userB),
        )
    }

    @Test
    fun `produces the 32-byte ChatId length`() {
        assertEquals(32, generator.generate(ChatType.CONTACT_DM, userA, userB).bytes.size)
    }

    private fun String.hexToId(): ID = chunked(2).map { it.toInt(16).toByte() }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
