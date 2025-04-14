package com.getcode.opencode.controllers

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.DEFAULT_STREAM_TIMEOUT
import com.getcode.opencode.internal.network.services.OcpMessageStreamReference
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.messaging.Message
import com.getcode.opencode.repositories.MessagingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagingController @Inject constructor(
    private val repository: MessagingRepository
) {
    suspend fun openMessageStream(
        rendezvous: KeyPair,
        timeout: Long = DEFAULT_STREAM_TIMEOUT,
    ): Flow<List<Message>> = repository.openMessageStream(rendezvous, timeout)

    fun openMessageStreamWithKeepAlive(
        scope: CoroutineScope,
        rendezvous: KeyPair,
        onEvent: (Result<List<Message>>) -> Unit,
    ): OcpMessageStreamReference = repository.openMessageStreamWithKeepAlive(scope, rendezvous, onEvent)

    suspend fun pollMessages(
        rendezvous: KeyPair,
    ): Result<List<Message>> = repository.pollMessages(rendezvous)

    fun ackMessages(
        rendezvous: KeyPair,
        messageIds: List<ID> = emptyList(),
    ): Result<Unit> = ackMessages(rendezvous, messageIds)

    suspend fun sendMessage(
        rendezvous: KeyPair,
        message: Message,
    ): Result<ID> = repository.sendMessage(rendezvous, message)
}