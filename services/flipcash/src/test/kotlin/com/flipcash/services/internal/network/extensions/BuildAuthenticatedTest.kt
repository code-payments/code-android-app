package com.flipcash.services.internal.network.extensions

import com.codeinc.flipcash.gen.common.v1.Common
import com.getcode.ed25519.Ed25519
import com.getcode.utils.toByteString
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.codeinc.flipcash.gen.chat.v1.ChatService as RpcChatService
import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel

/**
 * The server verifies a request's auth against the request with auth cleared, so the signature has
 * to be taken once every other field is set. [buildAuthenticated] signs and builds in one step to
 * make that ordering impossible to get wrong; these tests hold it to that, and the second one
 * executes the failure it prevents.
 */
class BuildAuthenticatedTest {

    private val owner: Ed25519.KeyPair = Ed25519.createKeyPair()

    private val chatId = Common.ChatId.newBuilder()
        .setValue(ByteArray(32) { it.toByte() }.toByteString())
        .build()

    @Test
    fun `auth covers every field set on the request`() {
        val request = RpcChatService.GetChatRequest.newBuilder()
            .setChatId(chatId)
            .setViewMode(MessagingModel.ViewMode.REDACTED)
            .buildAuthenticated(owner) { setAuth(it) }

        assertTrue(request.authCoversWholeRequest())
    }

    @Test
    fun `a field set after the signature falls outside it`() {
        // The hazard, written out: setViewMode lands after the bytes were signed, so the server
        // verifies over a request the client never signed and rejects it as UNAUTHENTICATED.
        val request = RpcChatService.GetChatRequest.newBuilder()
            .setChatId(chatId)
            .apply { setAuth(authenticate(owner)) }
            .setViewMode(MessagingModel.ViewMode.REDACTED)
            .build()

        assertFalse(request.authCoversWholeRequest())
    }

    @Test
    fun `auth is not signed over itself`() {
        val request = RpcChatService.GetChatRequest.newBuilder()
            .setChatId(chatId)
            .buildAuthenticated(owner) { setAuth(it) }

        assertTrue(request.hasAuth())
        assertFalse(request.toBuilder().clearAuth().build().hasAuth())
        assertTrue(request.authCoversWholeRequest())
    }

    /** Verifies the way the server does: over the request with its auth cleared. */
    private fun RpcChatService.GetChatRequest.authCoversWholeRequest(): Boolean = Ed25519.verify(
        auth.keyPair.signature.value.toByteArray(),
        toBuilder().clearAuth().build().toByteArray(),
        auth.keyPair.pubKey.value.toByteArray(),
    )
}
