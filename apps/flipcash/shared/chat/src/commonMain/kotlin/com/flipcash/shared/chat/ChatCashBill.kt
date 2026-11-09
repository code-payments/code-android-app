package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MessageContent
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.flipcash.shared.chat.internal.delegates.receivedBy
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** How a cash message reached the client. Only [Live] can raise the chat's received bill. */
enum class ArrivalSource {
    /** Pushed on the event stream while the app is connected. */
    Live,

    /** Delta sync, gap fill, history paging, or the initial load of a chat. */
    CatchUp,
}

/** A cash message that arrived on the live stream from someone other than the viewer. */
data class LiveCashArrival(
    val chatId: ChatId,
    val messageId: Long,
    val senderId: ID?,
    val content: MessageContent.Cash,
)

/** What the viewer is paid by a [LiveCashArrival]: their share, never a group's total. */
data class ChatCashBillShare(
    val amount: Fiat,
    val mint: Mint,
)

/**
 * Whether a cash message earns the received bill over the chat it arrived in, and for how much.
 * Null means no bill; the bubble still draws, and nothing is queued for later.
 *
 * Pure so each condition is a row of a test. [nothingOnTop] is the UI's answer to "is this chat the
 * top screen" (no sheet, viewer, amount entry or dialog over it), and [billShowing] is whether
 * another bill holds the slot, checked before the entry starts a give for this one.
 */
fun chatCashBillShare(
    source: ArrivalSource,
    flagOn: Boolean,
    openChat: ChatId?,
    chatId: ChatId,
    selfId: ID?,
    senderId: ID?,
    content: MessageContent.Cash,
    nothingOnTop: Boolean,
    billShowing: Boolean,
): ChatCashBillShare? {
    if (!flagOn) return null
    if (source != ArrivalSource.Live) return null
    if (openChat == null || openChat != chatId) return null
    if (selfId == null || senderId == selfId) return null
    if (!nothingOnTop) return null
    if (billShowing) return null
    val amount = content.receivedBy(selfId) ?: return null
    return ChatCashBillShare(amount = amount, mint = content.mint)
}

/** Where the event stream hands a live cash arrival. [None] drops it. */
fun interface LiveCashSink {
    fun offer(arrival: LiveCashArrival)

    companion object {
        val None = LiveCashSink { }
    }
}

/**
 * The live cash arrivals, for whichever screen is open to act on. Hot and replay-less: an arrival
 * nobody is collecting for is dropped, as a chat that is not open has no bill to show.
 */
@Singleton
class LiveCashArrivals @Inject constructor() : LiveCashSink {
    private val _arrivals = MutableSharedFlow<LiveCashArrival>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val arrivals: Flow<LiveCashArrival> = _arrivals.asSharedFlow()

    override fun offer(arrival: LiveCashArrival) {
        _arrivals.tryEmit(arrival)
    }
}
