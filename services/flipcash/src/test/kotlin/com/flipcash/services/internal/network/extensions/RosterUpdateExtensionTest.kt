package com.flipcash.services.internal.network.extensions

import com.codeinc.flipcash.gen.chat.v1.Model as ChatModel
import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.events.v1.Model as EventModel
import com.google.protobuf.ByteString
import org.junit.Test
import kotlin.test.assertTrue

/**
 * An unset/unrecognized RosterUpdate.kind is how a future oneof arm looks to an already-shipped
 * client. It must be dropped rather than turned into a fabricated update: EventStreamDelegate
 * advances its in-memory roster-version tracker for every entry it sees, so a fabricated update
 * would record a version as applied without actually applying anything, and the real change
 * would never be caught up by the refetch RosterSummary's contract calls for.
 */
class RosterUpdateExtensionTest {

    private fun chatId(): Common.ChatId =
        Common.ChatId.newBuilder()
            .setValue(ByteString.copyFrom(ByteArray(32) { 1 }))
            .build()

    private fun rosterSummary(version: Long): ChatModel.RosterSummary =
        ChatModel.RosterSummary.newBuilder()
            .setMemberCount(1)
            .setVersion(version)
            .build()

    @Test
    fun `roster update with no kind set maps to null`() {
        val unset = ChatModel.RosterUpdate.newBuilder()
            .setRosterSummary(rosterSummary(version = 5))
            .build()

        assertTrue(unset.toRosterUpdateOrNull() == null)
    }

    @Test
    fun `chat update drops a roster update with no kind set instead of fabricating one`() {
        val unset = ChatModel.RosterUpdate.newBuilder()
            .setRosterSummary(rosterSummary(version = 5))
            .build()

        val chatUpdate = EventModel.ChatUpdate.newBuilder()
            .setChat(chatId())
            .setRosterUpdates(
                ChatModel.RosterUpdateBatch.newBuilder()
                    .addRosterUpdates(unset)
                    .build()
            )
            .build()
            .toChatUpdate()

        assertTrue(chatUpdate.rosterUpdates.isEmpty())
    }
}
