package com.flipcash.services.internal.proto

import com.codeinc.flipcash.gen.blob.v1.Model.UploadTarget
import com.codeinc.flipcash.gen.chat.v1.Model
import com.codeinc.flipcash.gen.common.v1.Common
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import com.google.protobuf.Timestamp
import org.junit.Test
import kotlin.test.assertEquals
import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel

/**
 * Round-trips generated messages through the protobuf wire codec.
 *
 * The generated classes arrive precompiled in `com.flipcash:flipcash2-client-protocol`, built
 * against the protobuf version that repo pinned, while the app resolves the protobuf runtime from
 * its own version catalog. The two move independently, and nothing asserts they agree — the
 * `RuntimeVersion` check that guards full gencode is not emitted for lite. The rest of the suite
 * builds proto messages and reads fields back, which exercises the accessors but never the encoder
 * or the parser, so a codec regression would pass unnoticed.
 *
 * These cover the paths that a runtime/gencode skew would break: nested and repeated messages,
 * enums, varints at their boundaries, non-ASCII strings, bytes, maps, oneofs, well-known types,
 * and unknown-field retention.
 */
class ProtoWireFormatTest {

    @Test
    fun `round-trips nested, repeated, enum and scalar fields`() {
        val original = Model.Metadata.newBuilder()
            .setChatId(Common.ChatId.newBuilder().setValue(bytes(32) { it }).build())
            .setType(Model.ChatType.GROUP)
            .addMembers(member(1))
            .addMembers(member(2))
            .setLastActivity(
                Timestamp.newBuilder().setSeconds(1_764_000_000L).setNanos(123_456_789).build()
            )
            .setLatestEventSequence(Long.MAX_VALUE)
            .setIsHidden(true)
            .setTitle("café ☕ 🧊")
            .build()

        val decoded = Model.Metadata.parseFrom(original.toByteArray())

        assertEquals(original, decoded)
        // Asserted individually so a broken equals() cannot make the check above vacuous.
        assertEquals(Model.ChatType.GROUP, decoded.type)
        assertEquals(2, decoded.membersCount)
        assertEquals(Long.MAX_VALUE, decoded.latestEventSequence)
        assertEquals("café ☕ 🧊", decoded.title)
        assertEquals(123_456_789, decoded.lastActivity.nanos)
    }

    @Test
    fun `round-trips a oneof and keeps the case that was set`() {
        val original = MessagingModel.Content.newBuilder()
            .setText(MessagingModel.TextContent.newBuilder().setText("hello").build())
            .build()

        val decoded = MessagingModel.Content.parseFrom(original.toByteArray())

        assertEquals(original, decoded)
        assertEquals(MessagingModel.Content.TypeCase.TEXT, decoded.typeCase)
        assertEquals("hello", decoded.text.text)
    }

    @Test
    fun `round-trips map fields`() {
        val original = UploadTarget.newBuilder()
            .setMethod(UploadTarget.Method.PUT)
            .setUrl("https://example.invalid/upload")
            .putHeaders("Content-Type", "image/png")
            .putHeaders("X-Trace", "abc123")
            .putFormFields("key", "value")
            .build()

        val decoded = UploadTarget.parseFrom(original.toByteArray())

        assertEquals(original, decoded)
        assertEquals(2, decoded.headersCount)
        assertEquals("image/png", decoded.headersMap["Content-Type"])
        assertEquals("value", decoded.formFieldsMap["key"])
    }

    @Test
    fun `round-trips bytes across the full byte range`() {
        val payload = bytes(256) { it }
        val original = Common.PublicKey.newBuilder().setValue(payload).build()

        val decoded = Common.PublicKey.parseFrom(original.toByteArray())

        assertEquals(payload, decoded.value)
    }

    @Test
    fun `preserves unknown fields when re-encoding`() {
        val original = Model.Metadata.newBuilder()
            .setType(Model.ChatType.TIP_DM)
            .setLatestEventSequence(42L)
            .setTitle("forward compatible")
            .build()

        // Empty declares no fields, so every field lands in the unknown-field set. Re-encoding has
        // to emit them again for a client to survive a server that is ahead of it.
        val reEncoded = Empty.parseFrom(original.toByteArray()).toByteArray()

        assertEquals(original, Model.Metadata.parseFrom(reEncoded))
    }

    private fun member(seed: Int) = Model.Member.newBuilder()
        .setUserId(Common.UserId.newBuilder().setValue(bytes(32) { seed }).build())
        .build()

    private fun bytes(size: Int, value: (Int) -> Int) =
        ByteString.copyFrom(ByteArray(size) { value(it).toByte() })
}
