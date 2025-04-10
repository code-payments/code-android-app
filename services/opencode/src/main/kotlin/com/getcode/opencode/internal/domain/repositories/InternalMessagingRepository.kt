package com.getcode.opencode.internal.domain.repositories

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.services.MessagingService
import com.getcode.opencode.internal.network.services.OcpMessageStreamReference
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.messaging.Message
import com.getcode.opencode.repositories.MessagingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

internal class InternalMessagingRepository @Inject constructor(
    private val service: MessagingService,
): MessagingRepository {
    override suspend fun openMessageStream(
        rendezvous: Ed25519.KeyPair,
        timeout: Long
    ): Flow<List<Message>> {
        return service.openMessageStream(rendezvous, timeout) {
            it == com.codeinc.opencode.gen.messaging.v1.MessagingService.Message.KindCase.REQUEST_TO_GRAB_BILL
        }
    }

    override fun openMessageStreamWithKeepAlive(
        scope: CoroutineScope,
        rendezvous: Ed25519.KeyPair,
        onEvent: (Result<List<Message>>) -> Unit
    ): OcpMessageStreamReference {
        TODO("Not yet implemented")
    }

    override suspend fun pollMessages(rendezvous: Ed25519.KeyPair): Result<List<Message>> {
        TODO("Not yet implemented")
    }

    override suspend fun ackMessages(
        rendezvous: Ed25519.KeyPair,
        messageIds: List<ID>
    ): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun sendMessage(rendezvous: Ed25519.KeyPair, message: Message): Result<ID> {
        TODO("Not yet implemented")
    }
}