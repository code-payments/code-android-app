package com.flipcash.shared.chat.alerts

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.chat.ChatId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Where the stream hands a qualifying reaction. [None] drops it, for callers that have no pill. */
fun interface ReactionAlertSink {
    fun offer(event: ReactionAlertEvent)

    companion object {
        val None = ReactionAlertSink { }
    }
}

/** A pill on screen. [generation] bumps when content merges in, so the UI can restart its effects. */
data class ReactionAlertState(
    val content: ReactionAlertContent,
    val generation: Int,
)

/** What a tap on the pill asks for. */
sealed interface ReactionAlertAction {
    /** Open the chat at the message. */
    data class OpenChat(val chatId: ChatId, val messageId: Long) : ReactionAlertAction
}

/**
 * The pill's state machine: foreground and on-screen gating, the [ReactionAlertQueue], and the hold
 * timer. It has its own state rather than extending `SessionToastController`, whose queue writes a
 * `BillToast(amount, isDeposit)` into the bill state; a pill carries chat names, emoji and merges.
 *
 * The chat screen reports itself as the open chat through [reportOnScreen]. A reaction in that chat
 * draws no pill, on screen or not: the pill is for chats the user is not in. Opening a chat also
 * closes its pill and drops its queued ones.
 */
@Singleton
class ReactionAlertController internal constructor(
    private val scope: CoroutineScope,
    private val isForeground: () -> Boolean,
) : ReactionAlertSink {

    @Inject
    constructor(dispatchers: DispatcherProvider) : this(
        scope = CoroutineScope(dispatchers.Main + SupervisorJob()),
        isForeground = {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        },
    )

    companion object {
        /** The banner's exit (220 ms), plus a frame, before the next one drops in. */
        const val CLOSE_MILLIS = 230L
    }

    private val queue = ReactionAlertQueue()
    private val lock = Any()
    private var holdJob: Job? = null
    private var generation = 0

    private var activeChat: ChatId? = null

    private val _state = MutableStateFlow<ReactionAlertState?>(null)
    val state: StateFlow<ReactionAlertState?> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<ReactionAlertAction>(extraBufferCapacity = 4)
    val actions: SharedFlow<ReactionAlertAction> = _actions.asSharedFlow()

    /**
     * [chatId] is the open chat. [messageIds], the ids visible in it, no longer affect alerts. When
     * the open chat changes, its pill closes and its queued pills are dropped.
     */
    @Suppress("UNUSED_PARAMETER")
    fun reportOnScreen(chatId: ChatId, messageIds: Set<Long>) = synchronized(lock) {
        if (activeChat == chatId) return@synchronized
        activeChat = chatId
        queue.dropPending(chatId)
        if (queue.current?.chatId == chatId) close()
    }

    /** The chat screen for [chatId] left. A later chat's report is not cleared by an earlier one's exit. */
    fun clearOnScreen(chatId: ChatId) = synchronized(lock) {
        if (activeChat == chatId) {
            activeChat = null
        }
    }

    override fun offer(event: ReactionAlertEvent) {
        synchronized(lock) {
            if (!isForeground()) return
            if (event.chatId == activeChat) return
            when (queue.offer(event)) {
                ReactionAlertQueue.Outcome.Started -> publishAndHold()
                ReactionAlertQueue.Outcome.Merged -> publishAndHold()
                ReactionAlertQueue.Outcome.Queued -> Unit
            }
        }
    }

    /** Closes the pill early (swipe up) or when its hold ends, then starts the next one. */
    fun dismiss() = synchronized(lock) { close() }

    fun onTap() {
        val shown = synchronized(lock) {
            val current = _state.value ?: return
            close()
            current
        }
        val chatId = shown.content.chatId
        val messageId = shown.content.messageId
        _actions.tryEmit(ReactionAlertAction.OpenChat(chatId, messageId))
    }

    /** Drops everything, for sign-out. */
    fun reset() = synchronized(lock) {
        holdJob?.cancel()
        queue.clear()
        _state.value = null
    }

    private fun publishAndHold() {
        val content = queue.current ?: return
        generation++
        _state.value = ReactionAlertState(content, generation)
        holdJob?.cancel()
        holdJob = scope.after(content.holdMillis) { synchronized(lock) { close() } }
    }

    private fun close() {
        if (_state.value == null) return
        holdJob?.cancel()
        _state.value = null
        // The next pill waits for this one's close animation.
        holdJob = scope.after(CLOSE_MILLIS) {
            synchronized(lock) {
                val next = queue.advance()
                if (next != null) {
                    if (isForeground()) publishAndHold() else queue.clear()
                }
            }
        }
    }

    private fun CoroutineScope.after(millis: Long, block: () -> Unit): Job = launch {
        delay(millis)
        block()
    }
}

val LocalReactionAlerts = staticCompositionLocalOf<ReactionAlertController?> { null }
