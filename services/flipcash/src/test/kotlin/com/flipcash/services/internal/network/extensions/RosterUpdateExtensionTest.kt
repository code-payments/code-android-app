package com.flipcash.services.internal.network.extensions

import com.codeinc.flipcash.gen.chat.v1.Model as ChatModel
import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.events.v1.Model as EventModel
import com.codeinc.flipcash.gen.profile.v1.Model as ProfileModel
import com.flipcash.services.models.chat.RosterChange
import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Roster changes are a convergent overlay outside the gap-detected event log, so they map to
 * their own domain type and carry the post-change RosterSummary that decides whether to apply
 * them.
 */
class RosterUpdateExtensionTest {

    private fun userId(byte: Byte): Common.UserId =
        Common.UserId.newBuilder()
            .setValue(ByteString.copyFrom(ByteArray(16) { byte }))
            .build()

    private fun chatId(byte: Byte): Common.ChatId =
        Common.ChatId.newBuilder()
            .setValue(ByteString.copyFrom(ByteArray(32) { byte }))
            .build()

    private fun rosterSummary(memberCount: Long, version: Long): ChatModel.RosterSummary =
        ChatModel.RosterSummary.newBuilder()
            .setMemberCount(memberCount)
            .setVersion(version)
            .build()

    private fun member(byte: Byte): ChatModel.Member =
        ChatModel.Member.newBuilder()
            .setUserId(userId(byte))
            .setUserProfile(ProfileModel.UserProfile.newBuilder().setDisplayName("Member $byte"))
            .build()

    private fun metadata(byte: Byte): ChatModel.Metadata =
        ChatModel.Metadata.newBuilder()
            .setChatId(chatId(byte))
            .setType(ChatModel.ChatType.GROUP)
            .setTitle("Flipcash Staff")
            .setLastActivity(Timestamp.newBuilder().setSeconds(2000))
            .build()

    @Test
    fun `member joined carries the member and the post-change summary`() {
        val update = ChatModel.RosterUpdate.newBuilder()
            .setMemberJoined(
                ChatModel.RosterUpdate.MemberJoined.newBuilder().setMember(member(9))
            )
            .setRosterSummary(rosterSummary(memberCount = 3, version = 7))
            .build()

        val change = assertIs<RosterChange.MemberJoined>(update.toRosterChangeOrNull())

        assertEquals(ByteArray(16) { 9 }.toList(), change.member.userId)
        assertEquals("Member 9", change.member.userProfile.displayName)
        assertEquals(3L, change.rosterSummary.memberCount)
        assertEquals(7L, change.rosterSummary.version)
    }

    @Test
    fun `member joined has no metadata when the joiner is not the recipient`() {
        val update = ChatModel.RosterUpdate.newBuilder()
            .setMemberJoined(
                ChatModel.RosterUpdate.MemberJoined.newBuilder().setMember(member(9))
            )
            .setRosterSummary(rosterSummary(memberCount = 3, version = 7))
            .build()

        val change = assertIs<RosterChange.MemberJoined>(update.toRosterChangeOrNull())

        assertNull(change.metadata)
    }

    @Test
    fun `member joined carries the chat snapshot when the joiner is the recipient`() {
        val update = ChatModel.RosterUpdate.newBuilder()
            .setMemberJoined(
                ChatModel.RosterUpdate.MemberJoined.newBuilder()
                    .setMember(member(9))
                    .setMetadata(metadata(1))
            )
            .setRosterSummary(rosterSummary(memberCount = 3, version = 7))
            .build()

        val change = assertIs<RosterChange.MemberJoined>(update.toRosterChangeOrNull())

        assertEquals("Flipcash Staff", assertNotNull(change.metadata).title)
    }

    @Test
    fun `member left carries the departing user and the post-change summary`() {
        val update = ChatModel.RosterUpdate.newBuilder()
            .setMemberLeft(
                ChatModel.RosterUpdate.MemberLeft.newBuilder().setUserId(userId(4))
            )
            .setRosterSummary(rosterSummary(memberCount = 2, version = 8))
            .build()

        val change = assertIs<RosterChange.MemberLeft>(update.toRosterChangeOrNull())

        assertEquals(ByteArray(16) { 4 }.toList(), change.userId)
        assertEquals(2L, change.rosterSummary.memberCount)
        assertEquals(8L, change.rosterSummary.version)
    }

    @Test
    fun `an update with no kind set is dropped rather than defaulted`() {
        val update = ChatModel.RosterUpdate.newBuilder()
            .setRosterSummary(rosterSummary(memberCount = 2, version = 8))
            .build()

        assertNull(update.toRosterChangeOrNull())
    }

    @Test
    fun `chat update carries the roster batch`() {
        val update = EventModel.ChatUpdate.newBuilder()
            .setChat(chatId(1))
            .setRosterUpdates(
                ChatModel.RosterUpdateBatch.newBuilder()
                    .addRosterUpdates(
                        ChatModel.RosterUpdate.newBuilder()
                            .setMemberLeft(
                                ChatModel.RosterUpdate.MemberLeft.newBuilder().setUserId(userId(4))
                            )
                            .setRosterSummary(rosterSummary(memberCount = 2, version = 8))
                    )
            )
            .build()

        val domain = update.toChatUpdate()

        assertEquals(1, domain.rosterUpdates.size)
        assertIs<RosterChange.MemberLeft>(domain.rosterUpdates.first())
    }

    @Test
    fun `chat update with no roster batch has no roster changes`() {
        val update = EventModel.ChatUpdate.newBuilder()
            .setChat(chatId(1))
            .build()

        assertTrue(update.toChatUpdate().rosterUpdates.isEmpty())
    }
}
