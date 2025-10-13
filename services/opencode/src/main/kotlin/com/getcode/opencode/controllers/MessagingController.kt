package com.getcode.opencode.controllers

import com.codeinc.opencode.gen.messaging.v1.MessagingService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.extensions.toPublicKey
import com.getcode.opencode.internal.network.services.OcpMessageStreamReference
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.transactions.GiveRequest
import com.getcode.opencode.model.transactions.GrabRequest
import com.getcode.opencode.repositories.MessagingRepository
import com.getcode.opencode.utils.flowInterval
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class MessagingController @Inject constructor(
    private val repository: MessagingRepository,
) {
    private var streamReference: OcpMessageStreamReference? = null

    // Thread-safe streamReference management
    private val streamReferenceMutex = Mutex()

    suspend fun awaitRequestToGrabBill(
        scope: CoroutineScope,
        rendezvous: KeyPair,
    ): GrabRequest? {
        cancelAwaitForBillGrab()
        delay(500)

        fun extractRequestToGrabBill(messages: List<MessagingService.Message>): GrabRequest?  {
            val message = messages.firstOrNull { it.kindCase == MessagingService.Message.KindCase.REQUEST_TO_GRAB_BILL } ?: return null
            val account =
                message.requestToGrabBill.requestorAccount.value.toByteArray().toPublicKey()
            val signature =
                com.getcode.solana.keys.Signature(
                    message.sendMessageRequestSignature.value.toByteArray().toList()
                )

            return GrabRequest(account, signature)
        }

        return suspendCancellableCoroutine { cont ->
            try {
                scope.launch {
                    streamReferenceMutex.withLock {
                        streamReference =
                            repository.openMessageStreamWithKeepAlive(
                                scope = scope,
                                rendezvous = rendezvous,
                                ackFilter = {
                                    // do NOT ack the give requests as the sender
                                    // doing so will delete the messages before the recipient can get them
                                    it.kindCase != MessagingService.Message.KindCase.REQUEST_TO_GIVE_BILL
                                },
                                transformer = { extractRequestToGrabBill(it) }
                            ) { result ->
                                result.onSuccess {
                                    cont.resume(it)
                                }.onFailure { throwable ->
                                    trace(
                                        tag = "Messaging",
                                        message = throwable.message.orEmpty(),
                                        type = TraceType.Silent
                                    )
                                    cont.resume(null) // Resume with null on failure
                                }
                            }
                    }
                }
            } catch (e: Exception) {
                trace(
                    tag = "Messaging",
                    message = "Failed to open message stream: ${e.message}",
                    type = TraceType.Silent
                )
                cont.resume(null)
            }

            cont.invokeOnCancellation {
                scope.launch {
                    // Clean up streamReference on coroutine cancellation
                    runCatching {
                        streamReferenceMutex.withLock {
                            streamReference?.destroy()
                            streamReference = null
                        }
                    }.onFailure { throwable ->
                        trace(
                            tag = "Messaging",
                            message = "Cancellation cleanup failed: ${throwable.message}",
                            type = TraceType.Silent
                        )
                    }
                }
            }
        }
    }

    suspend fun cancelAwaitForBillGrab() {
        val currentStream = streamReferenceMutex.withLock { streamReference } ?: return
        try {
            withContext(Dispatchers.IO) {
                currentStream.destroy()
            }
        } catch (e: Exception) {
            trace(
                tag = "Messaging",
                message = "Failed to destroy stream: ${e.message}",
                type = TraceType.Silent
            )
            // Continue with cleanup even if destroy fails
        } finally {
            streamReferenceMutex.withLock { streamReference = null }
        }
    }

    suspend fun sendRequestToGrabBill(
        destination: PublicKey,
        payload: OpenCodePayload,
    ): Result<PublicKey> {
        val paymentRequest = MessagingService.RequestToGrabBill.newBuilder()
            .setRequestorAccount(destination.asSolanaAccountId())

        val message = MessagingService.Message.newBuilder()
            .setRequestToGrabBill(paymentRequest)

        return repository.sendMessage(
            message = message,
            rendezvous = payload.rendezvous
        )
    }

    suspend fun pollForGiveRequest(
        rendezvous: KeyPair,
    ): Result<Pair<PublicKey, Mint>>{

        return repository.pollMessages(rendezvous)
            .map { messages ->
                messages.filter {
                    it.kindCase == MessagingService.Message.KindCase.REQUEST_TO_GIVE_BILL
                }
            }.mapCatching { messages ->
                val message = messages.firstOrNull() ?: throw IllegalStateException("No message found")
                val mint = message.requestToGiveBill.mint.toPublicKey()

                (message.id.toPublicKey() to mint)
            }
    }

    suspend fun sendRequestToGiveBill(
        tokenMint: Mint,
        rendezvous: KeyPair,
    ): Result<PublicKey> {
        val paymentRequest = MessagingService.RequestToGiveBill.newBuilder()
            .setMint(tokenMint.asSolanaAccountId())

        val message = MessagingService.Message.newBuilder()
            .setRequestToGiveBill(paymentRequest)

        return repository.sendMessage(
            message = message,
            rendezvous = rendezvous
        )
    }

    suspend fun ackMessages(
        rendezvous: KeyPair,
        messageIds: List<PublicKey>,
    ): Result<Unit> = repository.ackMessages(
        rendezvous = rendezvous,
        messageIds = messageIds
    )
}