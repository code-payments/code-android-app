package com.flipcash.services.controllers

import com.flipcash.services.internal.network.services.EventStreamReference
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.repository.EventStreamingRepository
import com.flipcash.services.user.UserManager
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventStreamingController @Inject constructor(
    private val repository: EventStreamingRepository,
    private val userManager: UserManager,
) {
    private val _chatUpdates = Channel<ChatUpdate>(capacity = Channel.UNLIMITED)
    val chatUpdates: Flow<ChatUpdate> = _chatUpdates.receiveAsFlow()

    private var streamRef: EventStreamReference? = null

    val isConnected: Boolean get() = streamRef != null

    val isStreamActive: Boolean get() = streamRef?.isActive == true

    fun open(scope: CoroutineScope): Boolean {
        if (streamRef != null) {
            trace("EventStreamingController: Stream already open, skipping")
            return true
        }

        val owner = userManager.accountCluster?.authority?.keyPair ?: run {
            trace("EventStreamingController: No account cluster, cannot open stream")
            return false
        }

        streamRef = repository.openEventStream(
            scope = scope,
            owner = owner,
            onEvent = { update ->
                trace("EventStreamingController: Received chat update, messages=${update.newMessages.size}")
                _chatUpdates.trySend(update)
            },
            onError = { error ->
                trace("EventStreamingController: Stream error: ${error.message}")
                // Clear the ref so the next heartbeat, lifecycle, or network
                // event creates a fresh stream. The framework guarantees this
                // fires only once per stream, so no risk of clearing a newer ref.
                streamRef = null
            },
        )
        return true
    }

    fun close() {
        streamRef?.destroy()
        streamRef = null
    }
}
