package com.flipcash.services.chat

import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.getcode.chatcipher.ChatEncryptionPolicy
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class E2eePolicyTest {

    private val flipcash = ChatEncryptionPolicy.FLIPCASH_USER_ID.toList()
    private val friend = List(16) { 2.toByte() }
    private val policy = E2eePolicy()

    private fun member(id: List<Byte>) =
        ChatMember(userId = id, userProfile = UserProfile.Empty, pointers = emptyList())

    private fun chat(
        type: ChatType,
        useE2ee: Boolean,
        with: List<Byte> = friend,
    ) = ChatMetadata(
        chatId = ChatId(ByteArray(32)),
        type = type,
        members = listOf(member(with)),
        lastMessage = null,
        lastActivity = Instant.fromEpochSeconds(0),
        useE2ee = useE2ee,
    )

    @Test
    fun `a DM with the flag on is encrypted`() {
        assertTrue(policy.shouldEncrypt(chat(ChatType.CONTACT_DM, useE2ee = true)))
        assertTrue(policy.shouldEncrypt(chat(ChatType.TIP_DM, useE2ee = true)))
    }

    @Test
    fun `a DM with the flag off is not encrypted`() {
        assertFalse(policy.shouldEncrypt(chat(ChatType.CONTACT_DM, useE2ee = false)))
        assertFalse(policy.shouldEncrypt(chat(ChatType.TIP_DM, useE2ee = false)))
    }

    @Test
    fun `a group is never encrypted`() {
        assertFalse(policy.shouldEncrypt(chat(ChatType.GROUP, useE2ee = true)))
    }

    @Test
    fun `a chat with the flipcash account is never encrypted`() {
        assertFalse(policy.shouldEncrypt(chat(ChatType.TIP_DM, useE2ee = true, with = flipcash)))
    }
}
