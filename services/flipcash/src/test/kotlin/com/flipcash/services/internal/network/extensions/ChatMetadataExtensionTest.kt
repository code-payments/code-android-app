package com.flipcash.services.internal.network.extensions

import com.codeinc.flipcash.gen.chat.v1.Model as ChatModel
import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.profile.v1.Model as ProfileModel
import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import com.flipcash.services.models.chat.MetadataUpdate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The member id is what authorizes re-minting a profile picture's expired download URL, and the
 * server sets it on the chat member rather than inside the nested profile.
 */
class ChatMetadataExtensionTest {

    private fun userId(byte: Byte): Common.UserId =
        Common.UserId.newBuilder()
            .setValue(ByteString.copyFrom(ByteArray(16) { byte }))
            .build()

    private fun metadata(member: ChatModel.Member): ChatModel.Metadata =
        ChatModel.Metadata.newBuilder()
            .setChatId(
                Common.ChatId.newBuilder()
                    .setValue(ByteString.copyFrom(ByteArray(32) { 1 }))
            )
            .setType(ChatModel.ChatType.CONTACT_DM)
            .setLastActivity(Timestamp.newBuilder().setSeconds(2000))
            .addMembers(member)
            .build()

    @Test
    fun `member profile takes the member's user id when the nested profile omits it`() {
        val member = ChatModel.Member.newBuilder()
            .setUserId(userId(9))
            .setUserProfile(ProfileModel.UserProfile.newBuilder().setDisplayName("User"))
            .build()

        val result = metadata(member).toChatMetadata()

        assertEquals(ByteArray(16) { 9 }.toList(), result.members[0].userProfile.userId)
    }

    @Test
    fun `member profile keeps its own user id when the server sets one`() {
        val member = ChatModel.Member.newBuilder()
            .setUserId(userId(9))
            .setUserProfile(
                ProfileModel.UserProfile.newBuilder()
                    .setDisplayName("User")
                    .setUserId(userId(3))
            )
            .build()

        val result = metadata(member).toChatMetadata()

        assertEquals(ByteArray(16) { 3 }.toList(), result.members[0].userProfile.userId)
    }

    private fun groupMetadata(description: String): ChatModel.Metadata =
        ChatModel.Metadata.newBuilder()
            .setChatId(
                Common.ChatId.newBuilder()
                    .setValue(ByteString.copyFrom(ByteArray(32) { 1 }))
            )
            .setType(ChatModel.ChatType.GROUP)
            .setLastActivity(Timestamp.newBuilder().setSeconds(2000))
            .setDescription(description)
            .build()

    @Test
    fun `metadata carries the description`() {
        assertEquals("Weekly tips", groupMetadata("Weekly tips").toChatMetadata().description)
    }

    @Test
    fun `an empty description maps to null`() {
        assertNull(groupMetadata("").toChatMetadata().description)
    }

    @Test
    fun `a description_changed update decodes to DescriptionChanged`() {
        val update = ChatModel.MetadataUpdate.newBuilder()
            .setDescriptionChanged(
                ChatModel.MetadataUpdate.DescriptionChanged.newBuilder().setNewDescription("Fresh")
            )
            .build()

        val result = update.toMetadataUpdate { it.toChatMetadata() }

        assertEquals(MetadataUpdate.DescriptionChanged("Fresh"), result)
    }

    @Test
    fun `a description_changed update with an empty value decodes as a clear`() {
        val update = ChatModel.MetadataUpdate.newBuilder()
            .setDescriptionChanged(ChatModel.MetadataUpdate.DescriptionChanged.getDefaultInstance())
            .build()

        val result = update.toMetadataUpdate { it.toChatMetadata() }

        assertEquals(MetadataUpdate.DescriptionChanged(""), result)
    }
}
