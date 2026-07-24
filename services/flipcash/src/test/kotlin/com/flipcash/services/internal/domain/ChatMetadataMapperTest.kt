package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.chat.v1.Model as ChatModel
import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel
import com.codeinc.flipcash.gen.profile.v1.Model as ProfileModel
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.PointerType
import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChatMetadataMapperTest {

    private val mapper = ChatMetadataMapper(
        userProfileMapper = UserProfileMapper(socialMapper = SocialAccountMapper()),
    )

    private fun chatId(byte: Byte = 1): Common.ChatId =
        Common.ChatId.newBuilder()
            .setValue(ByteString.copyFrom(ByteArray(32) { byte }))
            .build()

    private fun userId(byte: Byte = 2): Common.UserId =
        Common.UserId.newBuilder()
            .setValue(ByteString.copyFrom(ByteArray(16) { byte }))
            .build()

    private fun messageId(value: Long = 1): MessagingModel.MessageId =
        MessagingModel.MessageId.newBuilder().setValue(value).build()

    private fun textContent(text: String = "hello"): MessagingModel.Content =
        MessagingModel.Content.newBuilder()
            .setText(MessagingModel.TextContent.newBuilder().setText(text))
            .build()

    private fun message(
        id: Long = 1,
        text: String = "hello",
    ): MessagingModel.Message = MessagingModel.Message.newBuilder()
        .setMessageId(messageId(id))
        .setSenderId(userId())
        .addContent(textContent(text))
        .setTs(Timestamp.newBuilder().setSeconds(1000))
        .setUnreadSeq(1)
        .build()

    private fun member(
        userIdByte: Byte = 2,
        displayName: String = "User",
    ): ChatModel.Member = ChatModel.Member.newBuilder()
        .setUserId(userId(userIdByte))
        .setUserProfile(ProfileModel.UserProfile.newBuilder().setDisplayName(displayName))
        .addPointers(
            MessagingModel.Pointer.newBuilder()
                .setType(MessagingModel.Pointer.Type.READ)
                .setUserId(userId(userIdByte))
                .setValue(messageId(5))
        )
        .build()

    private fun metadata(
        block: ChatModel.Metadata.Builder.() -> Unit = {},
    ): ChatModel.Metadata = ChatModel.Metadata.newBuilder()
        .setChatId(chatId())
        .setType(ChatModel.ChatType.CONTACT_DM)
        .setLastActivity(Timestamp.newBuilder().setSeconds(2000))
        .apply(block)
        .build()

    @Test
    fun `maps chat type DM`() {
        val result = mapper.map(metadata())
        assertEquals(ChatType.CONTACT_DM, result.type)
    }

    @Test
    fun `maps chat id bytes`() {
        val result = mapper.map(metadata())
        assertEquals(32, result.chatId.bytes.size)
    }

    @Test
    fun `maps last activity`() {
        val result = mapper.map(metadata())
        assertEquals(2000L, result.lastActivity.epochSeconds)
    }

    @Test
    fun `maps isHidden field`() {
        val result = mapper.map(metadata { setIsHidden(true) })
        assertEquals(true, result.isHidden)
    }

    @Test
    fun `isHidden defaults to false`() {
        val result = mapper.map(metadata())
        assertEquals(false, result.isHidden)
    }

    @Test
    fun `maps members with pointers`() {
        val result = mapper.map(metadata { addMembers(member()) })
        assertEquals(1, result.members.size)
        assertEquals("User", result.members[0].userProfile.displayName)
        assertEquals(1, result.members[0].pointers.size)
        assertEquals(PointerType.READ, result.members[0].pointers[0].type)
        assertEquals(5L, result.members[0].pointers[0].value)
    }

    @Test
    fun `maps last message when present`() {
        val result = mapper.map(metadata { setLastMessage(message(text = "hey")) })
        assertNotNull(result.lastMessage)
        assertEquals(1L, result.lastMessage!!.messageId)
    }

    @Test
    fun `last message null when absent`() {
        val result = mapper.map(metadata())
        assertNull(result.lastMessage)
    }
}
