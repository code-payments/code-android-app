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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    /** The pill's chat is the one the user has open, so its top row reads "This chat ↓". */
    val inActiveChat: Boolean,
    val generation: Int,
)

/** What a tap on the pill asks for. */
sealed interface ReactionAlertAction {
    /** The chat is open: scroll to the message and pulse it. */
    data class ScrollTo(val chatId: ChatId, val messageId: Long) : ReactionAlertAction

    /** Anywhere else: open the chat at the message. */
    data class OpenChat(val chatId: ChatId, val messageId: Long) : ReactionAlertAction
}

/**
 * The pill's state machine: foreground and on-screen gating, the [ReactionAlertQueue], and the hold
 * timer. It has its own state rather than extending `SessionToastController`, whose queue writes a
 * `BillToast(amount, isDeposit)` into the bill state; a pill carries chat names, emoji and merges.
 *
 * The chat screen reports which messages are on screen through [reportOnScreen]. A reaction on one
 * of them draws nothing here: the chip on the bubble already pops, and a boost shows its amount.
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
    private var onScreen: Set<Long> = emptySet()

    private val _state = MutableStateFlow<ReactionAlertState?>(null)
    val state: StateFlow<ReactionAlertState?> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<ReactionAlertAction>(extraBufferCapacity = 4)
    val actions: SharedFlow<ReactionAlertAction> = _actions.asSharedFlow()

    /** The open chat's visible message ids. Replaces the earlier report for [chatId]. */
    fun reportOnScreen(chatId: ChatId, messageIds: Set<Long>) = synchronized(lock) {
        activeChat = chatId
        onScreen = messageIds
        refreshActiveChatFlag()
    }

    /** The chat screen for [chatId] left. A later chat's report is not cleared by an earlier one's exit. */
    fun clearOnScreen(chatId: ChatId) = synchronized(lock) {
        if (activeChat == chatId) {
            activeChat = null
            onScreen = emptySet()
            refreshActiveChatFlag()
        }
    }

    override fun offer(event: ReactionAlertEvent) {
        synchronized(lock) {
            if (!isForeground()) return
            if (event.chatId == activeChat && event.messageId in onScreen) return
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
        _actions.tryEmit(
            if (shown.inActiveChat) ReactionAlertAction.ScrollTo(chatId, messageId)
            else ReactionAlertAction.OpenChat(chatId, messageId),
        )
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
        _state.value = ReactionAlertState(content, content.chatId == activeChat, generation)
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

    private fun refreshActiveChatFlag() {
        _state.update { shown ->
            shown?.copy(inActiveChat = shown.content.chatId == activeChat)
        }
    }

    private fun CoroutineScope.after(millis: Long, block: () -> Unit): Job = launch {
        delay(millis)
        block()
    }
}

val LocalReactionAlerts = staticCompositionLocalOf<ReactionAlertController?> { null }
