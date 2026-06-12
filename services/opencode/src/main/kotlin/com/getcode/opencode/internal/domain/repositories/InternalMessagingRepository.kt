package com.getcode.opencode.internal.domain.repositories

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.extensions.asMessageId
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
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
import com.codeinc.opencode.gen.messaging.v1.OcpMessagingService as RpcMessagingService

internal class InternalMessagingRepository @Inject constructor(
    private val service: MessagingService,
): MessagingRepository {
    override fun <R: TransferRequest> openMessageStreamWithKeepAlive(
        scope: CoroutineScope,
        rendezvous: Ed25519.KeyPair,
        ackFilter: (RpcMessagingService.Message) -> Boolean,
        transformer: (List<RpcMessagingService.Message>) -> R?,
        onEvent: (Result<R>) -> Unit
    ): OcpMessageStreamReference {
        return service.openMessageStreamWithKeepAlive(
            scope = scope,
            rendezvous = rendezvous,
            messageFilter = { messages ->
                messages.any { it.kindCase == RpcMessagingService.Message.KindCase.REQUEST_TO_GRAB_BILL }
            }
        ) { result ->
            result.onSuccess { messages ->
                scope.launch {
                    val acks = messages.filter { ackFilter(it) }.map { it.id }
                    val ackResult = if (acks.isNotEmpty()) {
                        // ack them back to server
                        service.ackMessages(
                            rendezvous,
                            acks,
                        ).onSuccess {
                            trace(
                                tag = "MessagingService",
                                message = "acked ${acks.size} messages",
                                type = TraceType.Silent
                            )
                        }.onFailure { ErrorUtils.handleError(it) }
                    } else {
                        Result.success(Unit)
                    }

                    ackResult.onSuccess {
                        // extract request
                        val request = transformer(messages)
                        if (request != null) {
                            // send back to transactor
                            onEvent(Result.success(request))
                        }
                    }
                }
            }.onFailure { onEvent(Result.failure(it)) }
        }
    }

    override suspend fun pollMessages(rendezvous: Ed25519.KeyPair): Result<List<RpcMessagingService.Message>> {
        return service.pollMessages(rendezvous)
    }

    override suspend fun ackMessages(
        rendezvous: Ed25519.KeyPair,
        messageIds: List<PublicKey>
    ): Result<Unit> {
        return service.ackMessages(rendezvous, messageIds.map { it.asMessageId() })
    }

    override suspend fun sendMessage(rendezvous: Ed25519.KeyPair, message: RpcMessagingService.Message.Builder): Result<PublicKey> {
        return service.sendMessage(rendezvous, message)
    }
}