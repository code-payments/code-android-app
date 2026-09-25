package com.flipcash.app.session

import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Token

/**
 * Sends cash into a chat as a cash link: the app funds the link and posts its URL as an ordinary
 * text message, which the transcript draws as a cash card.
 *
 * This is how a group chat sends cash. A group has no single recipient to transfer to, so the
 * first member to tap the card claims it.
 *
 * Separate from [SessionController] for the same reason [CashLinkClaims] is: chat needs this one
 * call, not the whole session.
 */
interface ChatCashLinks {
    /**
     * Funds a new cash link with [amount] of [token] from [owner], then posts the link to [chatId].
     *
     * Nothing is posted unless the link is funded. If the post fails, the link is cancelled and its
     * funds return to [owner]; the result is a failure either way. Once started, the send runs to
     * completion even if the caller is cancelled, so leaving the chat mid-send cannot strand a
     * funded link that was never posted.
     */
    suspend fun sendToChat(
        chatId: ChatId,
        amount: VerifiedFiat,
        token: Token,
        owner: AccountCluster,
    ): Result<Unit>
}
