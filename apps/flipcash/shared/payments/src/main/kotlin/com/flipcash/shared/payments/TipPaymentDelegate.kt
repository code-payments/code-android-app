package com.flipcash.shared.payments

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ResolverController
import com.flipcash.services.models.buildTipDmPaymentMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Token
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends cash within a tip DM: derives the canonical tip chat for [ID], resolves the recipient's
 * on-chain owner, attaches tip-DM app metadata, transfers, debits the local balance, and syncs the
 * chat feed. Returns the canonical tip [ChatId] (for message reload / navigation), or null if it
 * couldn't be derived.
 *
 * The user-id counterpart to [ContactPaymentDelegate] (keyed by a phone number, attaching
 * contact-DM metadata). Shared by the out-of-chat tip flow
 * ([com.flipcash.shared.tipping.TippingCoordinator]) and the in-chat send (a tip DM opened in the
 * messenger). Performs no UI or navigation — the caller owns send state, error presentation, and
 * any post-send navigation.
 */
@Singleton
class TipPaymentDelegate @Inject constructor(
    private val resolverController: ResolverController,
    private val transactionController: TransactionController,
    private val tokenCoordinator: TokenCoordinator,
    private val chatCoordinator: ChatCoordinator,
) {
    suspend fun send(
        userId: ID,
        verifiedFiat: VerifiedFiat,
        token: Token,
        source: AccountCluster,
    ): Result<ChatId?> {
        val canonicalChatId = chatCoordinator.generateChatId(userId = userId).getOrNull()
        val appMetadataBytes = buildTipDmPaymentMetadata(chatId = canonicalChatId)

        return resolverController.resolve(userId = userId)
            .mapCatching { destination ->
                transactionController.directTransfer(
                    amount = verifiedFiat,
                    token = token,
                    source = source,
                    destinationOwner = destination,
                    appMetadata = appMetadataBytes,
                ).getOrThrow()
                tokenCoordinator.subtract(token, verifiedFiat.localFiat)
                if (canonicalChatId != null) {
                    chatCoordinator.loadMessages(canonicalChatId)
                } else {
                    // New conversation — server just created the DM chat.
                    // Sync the feed so it appears in the contact list.
                    chatCoordinator.refreshFeed()
                }
                canonicalChatId
            }
    }
}
