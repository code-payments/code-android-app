package com.flipcash.shared.chat.models

import androidx.compose.runtime.staticCompositionLocalOf
import com.getcode.solana.keys.Mint

sealed interface ChatAction {
    data class RetryMessage(val bubble: ChatListItem.ContentBubble) : ChatAction
    data class AdvanceReadPointer(val messageId: Long) : ChatAction
    object RefreshContact : ChatAction
    data class ViewToken(val mint: Mint) : ChatAction
    data object ViewProfile : ChatAction

    /**
     * Adds [bubble] to the selection, or removes it if it is already selected.
     *
     * The bubble travels whole rather than as an id because the selection bar needs what the
     * transcript already resolved — the capability set and the body to copy or edit — and re-reading
     * it from the paging list would mean deciding availability at the menu instead.
     */
    data class ToggleSelection(val bubble: ChatListItem.ContentBubble) : ChatAction

    /** Leaves selection mode without acting on anything. */
    data object ClearSelection : ChatAction
}

typealias ChatActionHandler = (ChatAction) -> Unit

val LocalChatActionHandler = staticCompositionLocalOf<ChatActionHandler> { {} }
