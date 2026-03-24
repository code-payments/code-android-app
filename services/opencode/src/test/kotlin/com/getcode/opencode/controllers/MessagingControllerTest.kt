package com.getcode.opencode.controllers

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.core.errors.AckMessagesError
import com.getcode.opencode.repositories.MessagingRepository
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.Key32
import com.getcode.solana.keys.PublicKey
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MessagingControllerTest {

    private val repository = FakeMessagingRepository()
    private val controller = MessagingController(repository)

    // region ackMessages

    @Test
    fun `ackMessages delegates to repository`() = runTest {
        repository.ackMessagesResult = Result.success(Unit)

        val rendezvous = mockk<Ed25519.KeyPair>(relaxed = true)
        val messageIds = listOf(Key32.mock as PublicKey, Mint.usdf as PublicKey)
        val result = controller.ackMessages(rendezvous, messageIds)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `ackMessages propagates failure`() = runTest {
        val error = AckMessagesError.Unrecognized()
        repository.ackMessagesResult = Result.failure(error)

        val rendezvous = mockk<Ed25519.KeyPair>(relaxed = true)
        val result = controller.ackMessages(rendezvous, emptyList())

        assertTrue(result.isFailure)
        assertIs<AckMessagesError.Unrecognized>(result.exceptionOrNull())
    }

    @Test
    fun `ackMessages with empty messageIds succeeds`() = runTest {
        repository.ackMessagesResult = Result.success(Unit)

        val rendezvous = mockk<Ed25519.KeyPair>(relaxed = true)
        val result = controller.ackMessages(rendezvous, emptyList())

        assertTrue(result.isSuccess)
    }

    // endregion
}

/**
 * Partial fake for [MessagingRepository]. Only methods under test are implemented;
 * the rest throw to catch unintended calls.
 *
 * Note: [openMessageStreamWithKeepAlive], [pollMessages], and [sendMessage] involve
 * protobuf types that are expensive to construct in tests. They are covered here as
 * stubs that throw; dedicated tests can be added when the payoff justifies the setup cost.
 */
private class FakeMessagingRepository : MessagingRepository {
    var ackMessagesResult: Result<Unit> = Result.success(Unit)

    override fun <R : com.getcode.opencode.model.transactions.TransferRequest> openMessageStreamWithKeepAlive(
        scope: kotlinx.coroutines.CoroutineScope,
        rendezvous: Ed25519.KeyPair,
        ackFilter: (com.codeinc.opencode.gen.messaging.v1.MessagingService.Message) -> Boolean,
        transformer: (List<com.codeinc.opencode.gen.messaging.v1.MessagingService.Message>) -> R?,
        onEvent: (Result<R>) -> Unit,
    ): com.getcode.opencode.internal.network.services.OcpMessageStreamReference {
        throw UnsupportedOperationException("Not implemented in tests")
    }

    override suspend fun pollMessages(
        rendezvous: Ed25519.KeyPair,
    ): Result<List<com.codeinc.opencode.gen.messaging.v1.MessagingService.Message>> {
        throw UnsupportedOperationException("Not implemented in tests")
    }

    override suspend fun ackMessages(
        rendezvous: Ed25519.KeyPair,
        messageIds: List<PublicKey>,
    ): Result<Unit> = ackMessagesResult

    override suspend fun sendMessage(
        rendezvous: Ed25519.KeyPair,
        message: com.codeinc.opencode.gen.messaging.v1.MessagingService.Message.Builder,
    ): Result<PublicKey> {
        throw UnsupportedOperationException("Not implemented in tests")
    }
}
