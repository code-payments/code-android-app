package com.flipcash.services.internal.network.extensions

import com.flipcash.services.models.chat.MessageContent
import com.google.protobuf.ByteString
import org.junit.Test
import kotlin.test.assertEquals
import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel

/**
 * `messaging.v1.Content.encrypted` is never decrypted client-side -- see [MessageContent.Encrypted]'s
 * doc -- but it has to survive proto -> domain -> proto intact, because [asContent] now re-sends it
 * verbatim on an edit that rewrites an envelope around it, rather than throwing.
 */
class MessageContentEncryptedMappingTest {

    @Test
    fun `Content encrypted decodes to MessageContent Encrypted with its fields intact`() {
        val proto = MessagingModel.Content.newBuilder()
            .setEncrypted(
                MessagingModel.EncryptedContent.newBuilder()
                    .setSchemeValue(1)
                    .setNonce(ByteString.copyFrom(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
                    .setCiphertext(ByteString.copyFrom(byteArrayOf(9, 10, 11, 12)))
            )
            .build()

        val content = proto.toMessageContent() as MessageContent.Encrypted

        assertEquals(1, content.scheme)
        assertEquals(true, byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8).contentEquals(content.nonce))
        assertEquals(true, byteArrayOf(9, 10, 11, 12).contentEquals(content.ciphertext))
    }

    @Test
    fun `MessageContent Encrypted re-encodes to the same Content encrypted bytes`() {
        val original = MessageContent.Encrypted(
            scheme = 1,
            nonce = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            ciphertext = byteArrayOf(9, 10, 11, 12),
        )

        val proto = original.asContent()

        assertEquals(MessagingModel.Content.TypeCase.ENCRYPTED, proto.typeCase)
        assertEquals(1, proto.encrypted.schemeValue)
        assertEquals(ByteString.copyFrom(original.nonce), proto.encrypted.nonce)
        assertEquals(ByteString.copyFrom(original.ciphertext), proto.encrypted.ciphertext)
    }

    @Test
    fun `proto to domain to proto round trip preserves scheme, nonce and ciphertext`() {
        val proto = MessagingModel.Content.newBuilder()
            .setEncrypted(
                MessagingModel.EncryptedContent.newBuilder()
                    .setSchemeValue(1)
                    .setNonce(ByteString.copyFrom(ByteArray(24) { it.toByte() }))
                    .setCiphertext(ByteString.copyFrom(ByteArray(32) { (it * 3).toByte() }))
            )
            .build()

        val roundTripped = proto.toMessageContent().asContent()

        assertEquals(proto.encrypted, roundTripped.encrypted)
    }
}
