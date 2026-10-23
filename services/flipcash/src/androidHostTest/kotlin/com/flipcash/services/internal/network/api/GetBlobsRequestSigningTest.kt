package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.blob.v1.BlobStorageService as RpcBlobStorageService
import com.codeinc.flipcash.gen.common.v1.Common
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.ChatId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `authenticate` signs the request as built so far and the server verifies against the whole
 * request minus the auth, so every field has to be set before the signature is taken. `GetBlobs`
 * set its access context afterwards, which made the server reject the call as `UNAUTHENTICATED`:
 * re-minting another user's expired avatar URL always failed, and group-chat sender avatars stayed
 * on their BlurHash. These assert on the bytes handed to the signer, so no signing key is needed.
 */
class GetBlobsRequestSigningTest {

    private val blobId = BlobId(ByteArray(16) { 7 })

    /** The message the signature is taken over, as the signer sees it. */
    private fun signedBytes(
        context: BlobAccessContext,
    ): RpcBlobStorageService.GetBlobsRequest {
        var signed: ByteArray? = null
        getBlobsRequest(listOf(blobId), context) {
            signed = buildPartial().toByteArray()
            Common.Auth.getDefaultInstance()
        }
        return RpcBlobStorageService.GetBlobsRequest.parseFrom(
            requireNonNull(signed)
        )
    }

    private fun requireNonNull(bytes: ByteArray?): ByteArray =
        bytes ?: error("the authenticator was never invoked")

    @Test
    fun `a profile context is signed`() {
        val userId = List<Byte>(16) { 2 }

        val signed = signedBytes(BlobAccessContext.Profile(userId))

        assertTrue(signed.hasContext())
        assertEquals(userId, signed.context.userProfile.value.toByteArray().toList())
    }

    @Test
    fun `a chat context is signed`() {
        val chatId = ChatId(ByteArray(32) { 1 })

        val signed = signedBytes(BlobAccessContext.Chat(chatId))

        assertTrue(signed.hasContext())
        assertEquals(chatId.bytes.toList(), signed.context.chat.value.toByteArray().toList())
    }

    @Test
    fun `a chat profile context is signed`() {
        val chatId = ChatId(ByteArray(32) { 1 })

        val signed = signedBytes(BlobAccessContext.ChatProfile(chatId))

        assertTrue(signed.hasContext())
        assertEquals(chatId.bytes.toList(), signed.context.chatProfile.value.toByteArray().toList())
    }

    @Test
    fun `the blob ids are signed`() {
        val signed = signedBytes(BlobAccessContext.Owned)

        assertEquals(
            listOf(blobId.bytes.toList()),
            signed.blobIds.blobIdsList.map { it.value.toByteArray().toList() },
        )
    }

    @Test
    fun `an owned read signs no context`() {
        val signed = signedBytes(BlobAccessContext.Owned)

        assertFalse(signed.hasContext())
    }

    @Test
    fun `the auth is not part of what is signed`() {
        val signed = signedBytes(BlobAccessContext.Profile(List<Byte>(16) { 2 }))

        assertFalse(signed.hasAuth())
    }
}
