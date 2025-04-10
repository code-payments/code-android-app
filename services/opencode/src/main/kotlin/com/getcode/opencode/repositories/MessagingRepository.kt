package com.getcode.opencode.repositories

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.DEFAULT_STREAM_TIMEOUT
import com.getcode.opencode.internal.network.services.OcpMessageStreamReference
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.messaging.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface MessagingRepository {
    suspend fun openMessageStream(
        rendezvous: KeyPair,
        timeout: Long = DEFAULT_STREAM_TIMEOUT,
    ): Flow<List<Message>>

    fun openMessageStreamWithKeepAlive(
        scope: CoroutineScope,
        rendezvous: KeyPair,
        onEvent: (Result<List<Message>>) -> Unit,
    ): OcpMessageStreamReference

    suspend fun pollMessages(
        rendezvous: KeyPair,
    ): Result<List<Message>>

    suspend fun ackMessages(
        rendezvous: KeyPair,
        messageIds: List<ID> = emptyList(),
    ): Result<Unit>

    suspend fun sendMessage(
        rendezvous: KeyPair,
        message: Message,
    ): Result<ID>
}