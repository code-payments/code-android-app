package com.flipcash.shared.payments

import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.models.buildDmPaymentMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Token
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends cash within a contact (phone-number) DM: resolves the contact's on-chain owner, attaches
 * contact-DM app metadata (source + destination phone number), transfers, debits the local
 * balance, and syncs the chat feed.
 *
 * The phone-based counterpart to [TipPaymentDelegate] (keyed by a user id, attaching tip-DM
 * metadata). Performs no UI or navigation — the caller owns send state and error presentation.
 */
@Singleton
class ContactPaymentDelegate @Inject constructor(
    private val contactCoordinator: ContactCoordinator,
    private val transactionController: TransactionController,
    private val tokenCoordinator: TokenCoordinator,
    private val chatCoordinator: ChatCoordinator,
) {
    suspend fun send(
        contact: DeviceContact,
        chatId: ChatId?,
        verifiedFiat: VerifiedFiat,
        token: Token,
        source: AccountCluster,
    ): Result<Unit> {
        val appMetadataBytes = buildDmPaymentMetadata(
            chatId = chatId,
            sourcePhone = contactCoordinator.selfPhone,
            destinationPhone = contact.e164,
        )

        return contactCoordinator.resolve(contact.e164)
            .mapCatching { destination ->
                transactionController.directTransfer(
                    amount = verifiedFiat,
                    token = token,
                    source = source,
                    destinationOwner = destination,
                    appMetadata = appMetadataBytes,
                ).getOrThrow()
                tokenCoordinator.subtract(token, verifiedFiat.localFiat)
                if (chatId != null) {
                    chatCoordinator.loadMessages(chatId)
                } else {
                    // New conversation — server just created the DM chat.
                    // Sync the feed so it appears in the contact list.
                    chatCoordinator.refreshFeed()
                }
            }
    }
}
