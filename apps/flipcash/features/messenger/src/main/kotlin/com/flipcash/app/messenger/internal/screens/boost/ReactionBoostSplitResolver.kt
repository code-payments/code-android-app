package com.flipcash.app.messenger.internal.screens.boost

import com.flipcash.app.messenger.internal.payment.ReactionBoostPayer
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatCoordinator
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Who a Paid Reaction on a message pays, as the copy both the sheet and its keypad show. Shared so
 * the two can't disagree about the same message.
 */
internal class ReactionBoostSplitResolver @Inject constructor(
    private val chatCoordinator: ChatCoordinator,
    private val payer: ReactionBoostPayer,
) {
    /** The split for [messageId] in [chatId]; null until its author and chat kind are known. */
    suspend fun resolve(chatId: ChatId, messageId: Long): ReactionBoostViewModel.SplitCopy? {
        val author = chatCoordinator.getMessage(chatId, messageId)?.senderId
        val metadata = chatCoordinator.observeMetadata(chatId).first()?.metadata
        val chat = ReactionBoostViewModel.boostChatOf(metadata?.type, metadata?.creator)
        // Any amount that clears the minimum shows the same payees; 100 cents splits cleanly.
        val plan = if (author != null && chat != null) payer.plan(author, chat, 100) else null
        return ReactionBoostViewModel.splitCopyOf(chat, plan)
    }
}
