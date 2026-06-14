package com.getcode.opencode.repositories

import com.codeinc.opencode.gen.messaging.v1.OcpMessagingService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.services.OcpMessageStreamReference
import com.getcode.opencode.model.transactions.GrabRequest
import com.getcode.opencode.model.transactions.TransferRequest
import com.getcode.solana.keys.PublicKey
import kotlinx.coroutines.CoroutineScope

interface MessagingRepository {
    fun <R: TransferRequest> openMessageStreamWithKeepAlive(
        scope: CoroutineScope,
        rendezvous: KeyPair,
        ackFilter: (OcpMessagingService.Message) -> Boolean = { true },
        transformer: (List<OcpMessagingService.Message>) -> R?,
        onEvent: (Result<R>) -> Unit,
    ): OcpMessageStreamReference

    suspend fun pollMessages(
        rendezvous: KeyPair,
    ): Result<List<OcpMessagingService.Message>>

    suspend fun ackMessages(
        rendezvous: KeyPair,
        messageIds: List<PublicKey> = emptyList(),
    ): Result<Unit>

    suspend fun sendMessage(
        rendezvous: KeyPair,
        message: OcpMessagingService.Message.Builder,
    ): Result<PublicKey>
}