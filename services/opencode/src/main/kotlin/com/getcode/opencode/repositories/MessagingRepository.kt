package com.getcode.opencode.repositories

import com.codeinc.opencode.gen.messaging.v1.MessagingService
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
        ackFilter: (MessagingService.Message) -> Boolean = { true },
        transformer: (List<MessagingService.Message>) -> R?,
        onEvent: (Result<R>) -> Unit,
    ): OcpMessageStreamReference

    suspend fun pollMessages(
        rendezvous: KeyPair,
    ): Result<List<MessagingService.Message>>

    suspend fun ackMessages(
        rendezvous: KeyPair,
        messageIds: List<PublicKey> = emptyList(),
    ): Result<Unit>

    suspend fun sendMessage(
        rendezvous: KeyPair,
        message: MessagingService.Message.Builder,
    ): Result<PublicKey>
}