package com.flipcash.services.controllers

import com.flipcash.services.internal.network.services.EventStreamReference
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.repository.EventStreamingRepository
import com.flipcash.services.user.UserManager
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventStreamingController @Inject constructor(
    private val repository: EventStreamingRepository,
    private val userManager: UserManager,
) {
    private val _chatUpdates = MutableSharedFlow<ChatUpdate>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val chatUpdates: SharedFlow<ChatUpdate> = _chatUpdates.asSharedFlow()

    private var streamRef: EventStreamReference? = null

    val isConnected: Boolean get() = streamRef?.isActive == true

    fun open(
        scope: CoroutineScope,
        onStreamError: (() -> Unit)? = null,
    ): Boolean {
        val owner = userManager.accountCluster?.authority?.keyPair ?: run {
            trace("EventStreamingController: No account cluster, cannot open stream")
            return false
        }

        close()

        streamRef = repository.openEventStream(
            scope = scope,
            owner = owner,
            onEvent = { update ->
                trace("EventStreamingController: Received chat update, messages=${update.newMessages.size}")
                _chatUpdates.tryEmit(update)
            },
            onError = { error ->
                trace("EventStreamingController: Stream error: ${error.message}")
                onStreamError?.invoke()
            },
        )
        return true
    }

    fun close() {
        streamRef?.destroy()
        streamRef = null
    }
}
