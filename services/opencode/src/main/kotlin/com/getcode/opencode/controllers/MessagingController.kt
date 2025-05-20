package com.getcode.opencode.controllers

import com.codeinc.opencode.gen.messaging.v1.MessagingService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.services.OcpMessageStreamReference
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.transactions.TransferRequest
import com.getcode.opencode.repositories.MessagingRepository
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    ): TransferRequest? {
        cancelAwaitForBillGrab()
        delay(500)

        return suspendCancellableCoroutine { cont ->
            try {
                scope.launch {
                    streamReferenceMutex.withLock {
                        streamReference =
                            repository.openMessageStreamWithKeepAlive(scope, rendezvous) { result ->
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
}