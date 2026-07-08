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

    // Guards all reads/writes of [streamRef]. open()/close() are invoked
    // concurrently from multiple triggers (login, lifecycle onStart, network
    // reconnect, feature-flag, heartbeat) on the multi-threaded IO scope, so
    // the check-then-act on streamRef must be atomic — otherwise two threads
    // both observe a null ref and each open a stream, producing a second
    // orphaned gRPC stream and the server's "ABORTED: stream already exists".
    private val lock = Any()
    private var streamRef: EventStreamReference? = null

    val isConnected: Boolean get() = synchronized(lock) { streamRef != null }

    val isStreamActive: Boolean get() = synchronized(lock) { streamRef?.isActive == true }

    fun open(scope: CoroutineScope): Boolean = synchronized(lock) {
        if (streamRef != null) {
            trace("EventStreamingController: Stream already open, skipping")
            return true
        }

        val owner = userManager.accountCluster?.authority?.keyPair ?: run {
            trace("EventStreamingController: No account cluster, cannot open stream")
            return false
        }

        lateinit var openedRef: EventStreamReference
        openedRef = repository.openEventStream(
            scope = scope,
            owner = owner,
            onEvent = { update ->
                trace("EventStreamingController: Received chat update, messages=${update.newMessages.size}")
                _chatUpdates.trySend(update)
            },
            onError = { error ->
                trace("EventStreamingController: Stream error: ${error.message}")
                // Clear the ref so the next heartbeat, lifecycle, or network
                // event creates a fresh stream. Only clear if it is still THIS
                // stream — never null out a newer ref opened after us.
                synchronized(lock) {
                    if (streamRef === openedRef) streamRef = null
                }
            },
        )
        streamRef = openedRef
        return true
    }

    fun close() = synchronized(lock) {
        streamRef?.destroy()
        streamRef = null
    }
}
