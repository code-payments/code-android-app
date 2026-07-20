package com.flipcash.services.repository

import com.flipcash.services.internal.network.services.EventStreamReference
import com.flipcash.services.models.chat.BlobUpdate
import com.flipcash.services.models.chat.ChatUpdate
import com.getcode.ed25519.Ed25519.KeyPair
import kotlinx.coroutines.CoroutineScope

interface EventStreamingRepository {
    fun openEventStream(
        scope: CoroutineScope,
        owner: KeyPair,
        onEvent: (ChatUpdate) -> Unit,
        onBlobUpdate: (BlobUpdate) -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ): EventStreamReference
}
