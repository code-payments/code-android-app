package com.flipcash.shared.chat.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.getcode.solana.keys.Mint

sealed interface ChatAction {
    data class RetryMessage(val bubble: ChatListItem.ContentBubble) : ChatAction
    data class AdvanceReadPointer(val messageId: Long) : ChatAction
    object RefreshContact : ChatAction
    data class ViewToken(val mint: Mint) : ChatAction
}

typealias ChatActionHandler = (ChatAction) -> Unit

val LocalChatActionHandler = staticCompositionLocalOf<ChatActionHandler> { {} }
