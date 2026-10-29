package com.flipcash.services.internal.network.extensions

import com.codeinc.flipcash.gen.chat.v1.Model as ChatModel
import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel
import com.flipcash.services.models.chat.MetadataUpdate
import com.flipcash.services.models.chat.PinChange
import com.flipcash.services.models.chat.PinSummary
import com.flipcash.services.models.chat.fromStored
import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PinMappingTest {

    private fun messageId(value: Long): MessagingModel.MessageId =
        MessagingModel.MessageId.newBuilder().setValue(value).build()

    private fun pin(id: Long): ChatModel.Pin = ChatModel.Pin.newBuilder()
        .setMessageId(messageId(id))
        .setPinnedBy(
            Common.UserId.newBuilder().setValue(ByteString.copyFrom(ByteArray(16) { 4 }))
        )
        .setPinnedAt(Timestamp.newBuilder().setSeconds(3000).setNanos(5))
        .setMessage(MessagingModel.Message.newBuilder().setMessageId(messageId(id)))
        .build()

    private fun summary(latest: Long?, count: Int, version: Long): ChatModel.PinSummary =
        ChatModel.PinSummary.newBuilder()
            .apply { if (latest != null) setLatest(pin(latest)) }
            .setCount(count)
            .setVersion(version)
            .build()

    @Test
    fun `pin maps its message id, pinner, time and message`() {
        val result = pin(7).toPin()

        assertEquals(7L, result.messageId)
        assertEquals(ByteArray(16) { 4 }.toList(), result.pinnedBy)
        assertEquals(3000L, result.pinnedAt.epochSeconds)
        assertEquals(7L, result.message.messageId)
    }

    @Test
    fun `pin summary without a latest pin maps to null latest`() {
        val result = summary(latest = null, count = 0, version = 2).toPinSummary()

        assertNull(result.latest)
        assertEquals(0, result.count)
        assertEquals(2L, result.version)
    }

    @Test
    fun `a stored pin summary decodes to the summary it was encoded from`() {
        val original = summary(latest = 9, count = 3, version = 11).toPinSummary()
        val encoded = requireNotNull(original.latestEncoded)

        val restored = PinSummary.fromStored(original.count, original.version, encoded)

        assertEquals(original, restored)
        assertEquals(9L, restored.latest?.messageId)
        assertContentEquals(encoded, restored.latestEncoded)
    }

    @Test
    fun `a stored summary with no latest pin, or unreadable bytes, has no latest`() {
        assertNull(PinSummary.fromStored(0, 4, null).latest)
        val garbage = PinSummary.fromStored(2, 4, byteArrayOf(0x0A, 0x7F, 0x01))
        assertNull(garbage.latest)
        assertNull(garbage.latestEncoded)
        assertEquals(2, garbage.count)
    }

    @Test
    fun `pin summary carries latest, count and version`() {
        val result = summary(latest = 9, count = 3, version = 11).toPinSummary()

        assertEquals(9L, result.latest?.messageId)
        assertEquals(3, result.count)
        assertEquals(11L, result.version)
    }

    @Test
    fun `metadata without a pin summary maps to null`() {
        val metadata = ChatModel.Metadata.newBuilder()
            .setType(ChatModel.ChatType.GROUP)
            .setLastActivity(Timestamp.newBuilder().setSeconds(2000))
            .build()

        assertNull(metadata.toChatMetadata().pinSummary)
    }

    @Test
    fun `metadata maps its pin summary`() {
        val metadata = ChatModel.Metadata.newBuilder()
            .setType(ChatModel.ChatType.GROUP)
            .setLastActivity(Timestamp.newBuilder().setSeconds(2000))
            .setPinSummary(summary(latest = 1, count = 1, version = 1))
            .build()

        assertEquals(1, metadata.toChatMetadata().pinSummary?.count)
    }

    @Test
    fun `pins_changed with a pinned change decodes to PinsChanged Pinned`() {
        val update = ChatModel.MetadataUpdate.newBuilder()
            .setPinsChanged(
                ChatModel.MetadataUpdate.PinsChanged.newBuilder()
                    .setPinned(pin(5))
                    .setSummary(summary(latest = 5, count = 1, version = 4))
            )
            .build()

        val result = assertIs<MetadataUpdate.PinsChanged>(update.toMetadataUpdate { it.toChatMetadata() })

        assertEquals(5L, assertIs<PinChange.Pinned>(result.change).pin.messageId)
        assertEquals(4L, result.summary.version)
    }

    @Test
    fun `pins_changed with an unpinned change decodes to PinsChanged Unpinned`() {
        val update = ChatModel.MetadataUpdate.newBuilder()
            .setPinsChanged(
                ChatModel.MetadataUpdate.PinsChanged.newBuilder()
                    .setUnpinned(messageId(5))
                    .setSummary(summary(latest = null, count = 0, version = 5))
            )
            .build()

        val result = assertIs<MetadataUpdate.PinsChanged>(update.toMetadataUpdate { it.toChatMetadata() })

        assertEquals(PinChange.Unpinned(5L), result.change)
        assertNull(result.summary.latest)
    }

    @Test
    fun `viewer state maps can_pin`() {
        fun viewerState(canPin: Boolean) = ChatModel.ViewerState.newBuilder()
            .setPermissions(ChatModel.ViewerState.Permissions.newBuilder().setCanPin(canPin))
            .build()
            .toViewerState()

        assertTrue(viewerState(true).permissions.canPin)
        assertFalse(viewerState(false).permissions.canPin)
    }
}
