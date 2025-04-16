package com.getcode.opencode.internal.domain.repositories

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.network.services.MessagingService
import com.getcode.opencode.internal.network.services.OcpMessageStreamReference
import com.getcode.opencode.model.transactions.TransferRequest
import com.getcode.opencode.repositories.MessagingRepository
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.codeinc.opencode.gen.messaging.v1.MessagingService as RpcMessagingService

internal class InternalMessagingRepository @Inject constructor(
    private val service: MessagingService,
): MessagingRepository {
    override fun openMessageStreamWithKeepAlive(
        scope: CoroutineScope,
        rendezvous: Ed25519.KeyPair,
        onEvent: (Result<TransferRequest>) -> Unit
    ): OcpMessageStreamReference {
        return service.openMessageStreamWithKeepAlive(scope, rendezvous) { result ->
            result.onSuccess { messages ->
                scope.launch {
                    // ack them back to server
                    service.ackMessages(
                        rendezvous,
                        messages.map { it.id }
                    ).onSuccess {
                        trace(
                            tag = "MessagingService",
                            message = "acked",
                            type = TraceType.Silent
                        )
                        // extract request to grab bill
                        val request = extractRequestToGrabBill(messages)
                        if (request != null) {
                            // send back to transactor
                            onEvent(Result.success(request))
                        }
                    }.onFailure { ErrorUtils.handleError(it) }
                }
            }.onFailure { onEvent(Result.failure(it)) }
        }
    }

    private fun extractRequestToGrabBill(messages: List<RpcMessagingService.Message>): TransferRequest?  {
        val message = messages.firstOrNull { it.kindCase == RpcMessagingService.Message.KindCase.REQUEST_TO_GRAB_BILL } ?: return null
        val account =
            message.requestToGrabBill.requestorAccount.value.toByteArray().toPublicKey()
        val signature =
            com.getcode.solana.keys.Signature(
                message.sendMessageRequestSignature.value.toByteArray().toList()
            )

        return TransferRequest(account, signature)
    }

    override suspend fun pollMessages(rendezvous: Ed25519.KeyPair): Result<List<RpcMessagingService.Message>> {
        return service.pollMessages(rendezvous)
    }

    override suspend fun ackMessages(
        rendezvous: Ed25519.KeyPair,
        messageIds: List<RpcMessagingService.MessageId>
    ): Result<Unit> {
        return service.ackMessages(rendezvous, messageIds)
    }

    override suspend fun sendMessage(rendezvous: Ed25519.KeyPair, message: RpcMessagingService.Message.Builder): Result<PublicKey> {
        return service.sendMessage(rendezvous, message)
    }
}