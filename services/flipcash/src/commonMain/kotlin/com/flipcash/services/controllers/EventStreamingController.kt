package com.flipcash.services.controllers

import com.flipcash.services.internal.network.services.EventStreamReference
import com.flipcash.services.internal.network.services.FollowArchiveSender
import com.flipcash.services.models.chat.BlobUpdate
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.repository.EventStreamingRepository
import com.flipcash.services.user.UserManager
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _blobUpdates = Channel<BlobUpdate>(capacity = Channel.UNLIMITED)
    val blobUpdates: Flow<BlobUpdate> = _blobUpdates.receiveAsFlow()

    // Conflated: the signal is "there is no stream any more", which two failures state no better
    // than one, and it has to survive being emitted while the consumer is between waits.
    private val _streamFailures = Channel<Unit>(capacity = Channel.CONFLATED)

    /**
     * Emits when a stream ends with nothing retrying it — a status outside the reconnect loop's
     * retryable set, or the loop exhausting its attempts. Reopening is the consumer's call; this
     * only says the connection is gone, so a supervisor need not poll to find out.
     */
    val streamFailures: Flow<Unit> = _streamFailures.receiveAsFlow()

    // Guards all reads/writes of [streamRef]. open()/close() are invoked
    // concurrently from multiple triggers (login, lifecycle onStart, network
    // reconnect, feature-flag, heartbeat) on the multi-threaded IO scope, so
    // the check-then-act on streamRef must be atomic — otherwise two threads
    // both observe a null ref and each open a stream, producing a second
    // orphaned gRPC stream and the server's "ABORTED: stream already exists".
    private val lock = Any()
    private var streamRef: EventStreamReference? = null

    // Whether the client wants the stream to follow the archive. Held here, not on a stream, so
    // it outlives close()/open() and the reconnect loop's attempts: each new stream starts not
    // following, and is told to follow again right after Params. Guarded by [lock].
    private var wantsArchive = false
    // The current stream attempt's sender and its seq bookkeeping. Guarded by [lock].
    private var followSender: FollowArchiveSender? = null
    private val followSequencer = FollowArchiveSequencer()

    private val _followArchiveAcks = MutableSharedFlow<Boolean>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * The follow state each stream confirms, from the ack for the latest FollowArchive sent on it.
     * Acks for an earlier seq are dropped. `true` arrives again after every reconnect while
     * [setFollowArchive] is on: the server replays nothing a stream missed, so a consumer that
     * re-reads on `true` covers the gap.
     */
    val followArchiveAcks: Flow<Boolean> = _followArchiveAcks.asSharedFlow()

    /**
     * Asks the stream to carry archived groups ([follow] true) or to stop. Survives reconnects
     * until changed. A stream that is not up yet is told when it starts.
     */
    fun setFollowArchive(follow: Boolean) = synchronized(lock) {
        if (wantsArchive == follow) return@synchronized
        wantsArchive = follow
        // A stream that never followed has nothing to turn off.
        val sender = followSender ?: return@synchronized
        sender(follow, followSequencer.next(follow))
    }

    val isConnected: Boolean get() = synchronized(lock) { streamRef != null }

    val isStreamActive: Boolean get() = synchronized(lock) { streamRef?.isActive == true }

    fun open(scope: CoroutineScope): Boolean = synchronized(lock) {
        if (streamRef != null) {
            trace("EventStreamingController: Stream already open, skipping")
            return true
        }

        // A pending failure describes the stream being replaced here. Left queued, it would wake
        // the supervisor against the new stream before it has had a chance to connect.
        while (_streamFailures.tryReceive().isSuccess) Unit

        val owner = userManager.accountCluster?.authority?.keyPair ?: run {
            trace("EventStreamingController: No account cluster, cannot open stream")
            return false
        }

        lateinit var openedRef: EventStreamReference
        openedRef = repository.openEventStream(
            scope = scope,
            owner = owner,
            onEvent = { update ->
                trace("EventStreamingController: Received chat update, events=${update.events.size}")
                _chatUpdates.trySend(update)
            },
            onBlobUpdate = { update ->
                trace("EventStreamingController: Received blob update, blobs=${update.blobs.size}")
                _blobUpdates.trySend(update)
            },
            onStreamStarted = { sender ->
                synchronized(lock) {
                    if (streamRef !== openedRef) return@synchronized
                    followSequencer.reset()
                    followSender = sender
                    if (wantsArchive) sender(true, followSequencer.next(true))
                }
            },
            onFollowArchiveAck = { seq ->
                synchronized(lock) {
                    if (streamRef !== openedRef) return@synchronized
                    followSequencer.ack(seq)?.let { _followArchiveAcks.tryEmit(it) }
                }
            },
            onError = { error ->
                trace("EventStreamingController: Stream error: ${error.message}")
                // Clear the ref so the next heartbeat, lifecycle, or network
                // event creates a fresh stream. Only clear if it is still THIS
                // stream — never null out a newer ref opened after us.
                synchronized(lock) {
                    if (streamRef === openedRef) {
                        streamRef = null
                        followSender = null
                        _streamFailures.trySend(Unit)
                    }
                }
            },
        )
        streamRef = openedRef
        return true
    }

    fun close() = synchronized(lock) {
        streamRef?.destroy()
        streamRef = null
        followSender = null
    }
}
