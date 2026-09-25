package com.flipcash.app.session.internal.delegates

import com.flipcash.app.core.util.Linkify
import com.flipcash.app.session.ChatCashLinks
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.accounts.entropy
import com.getcode.opencode.model.financial.Token
import com.getcode.utils.trace
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts a funded cash link into a chat. See [ChatCashLinks].
 *
 * The message is the URL and nothing else. The transcript draws a message that is only a link as
 * the link's card, and the card shows the amount, so any text around the URL would be drawn as a
 * second bubble saying less than the card does.
 */
@Singleton
class ChatCashLinkDelegate @Inject constructor(
    private val funding: GiftCardFunding,
    private val chatCoordinator: ChatCoordinator,
) : ChatCashLinks {

    override suspend fun sendToChat(
        chatId: ChatId,
        amount: VerifiedFiat,
        token: Token,
        owner: AccountCluster,
    ): Result<Unit> = withContext(NonCancellable) {
        val verifiedState = amount.verifiedState
            ?: return@withContext Result.failure(IllegalStateException("Cash link amount has no verified state"))

        val giftCard = GiftCardAccount.create(token)

        funding.fund(giftCard, owner, amount.localFiat, token, verifiedState)
            .onFailure { return@withContext Result.failure(it) }

        chatCoordinator.sendMessage(chatId, Linkify.cashLink(giftCard.entropy))
            .map { }
            .onFailure { cause ->
                trace(tag = "Session", message = "Cash link post failed; cancelling the link", error = cause)
                funding.cancel(owner, giftCard).onFailure {
                    trace(
                        tag = "Session",
                        message = "Cancelling an unposted cash link failed; its funds return on expiry",
                        error = it,
                    )
                }
            }
    }
}
