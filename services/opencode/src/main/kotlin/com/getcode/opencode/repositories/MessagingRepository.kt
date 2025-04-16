package com.getcode.opencode.repositories

import com.codeinc.opencode.gen.messaging.v1.MessagingService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.services.OcpMessageStreamReference
import com.getcode.opencode.model.transactions.TransferRequest
import com.getcode.solana.keys.PublicKey
import kotlinx.coroutines.CoroutineScope

interface MessagingRepository {
//    suspend fun awaitRequestToGrabBill(
//        rendezvous: KeyPair,
//        timeout: Long = DEFAULT_STREAM_TIMEOUT,
//    ): Flow<PaymentRequest>

    fun openMessageStreamWithKeepAlive(
        scope: CoroutineScope,
        rendezvous: KeyPair,
        onEvent: (Result<TransferRequest>) -> Unit,
    ): OcpMessageStreamReference

    suspend fun pollMessages(
        rendezvous: KeyPair,
    ): Result<List<MessagingService.Message>>

    suspend fun ackMessages(
        rendezvous: KeyPair,
        messageIds: List<MessagingService.MessageId> = emptyList(),
    ): Result<Unit>

    suspend fun sendMessage(
        rendezvous: KeyPair,
        message: MessagingService.Message.Builder,
    ): Result<PublicKey>
}