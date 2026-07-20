package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.network.services.EventStreamReference
import com.flipcash.services.internal.network.services.EventStreamingService
import com.flipcash.services.models.chat.BlobUpdate
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.repository.EventStreamingRepository
import com.getcode.ed25519.Ed25519.KeyPair
import kotlinx.coroutines.CoroutineScope

internal class InternalEventStreamingRepository(
    private val service: EventStreamingService,
) : EventStreamingRepository {
    override fun openEventStream(
        scope: CoroutineScope,
        owner: KeyPair,
        onEvent: (ChatUpdate) -> Unit,
        onBlobUpdate: (BlobUpdate) -> Unit,
        onError: (Throwable) -> Unit,
    ): EventStreamReference {
        return service.openEventStream(scope, owner, onEvent, onBlobUpdate, onError)
    }
}
