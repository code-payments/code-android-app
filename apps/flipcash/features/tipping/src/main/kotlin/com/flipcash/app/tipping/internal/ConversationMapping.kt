package com.flipcash.app.tipping.internal

import com.getcode.opencode.exchange.Exchange
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.LobbyMember
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatSummary
import com.flipcash.shared.chat.waitingFor
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.chat.ui.previewedPaymentRequestId
import com.flipcash.shared.chat.ui.toConversationReference
import com.flipcash.shared.chat.ui.unnamedGroupSender
import com.getcode.opencode.model.financial.Token
import com.getcode.util.resources.ResourceHelper
import com.getcode.opencode.model.core.ID

/**
 * Chat summaries as list rows. One definition for the Chats list and the Archived list, so a row
 * looks the same in both.
 */
internal fun mapConversations(
    summaries: List<ChatSummary>,
    tokens: List<Token>,
    // Null on the first-frame draw, before the table has been read: asking then would re-fetch
    // every sender already on disk.
    senderProfiles: Map<String, UserProfile>?,
    selfId: ID?,
    resources: ResourceHelper,
    chatCoordinator: ChatCoordinator,
    exchange: Exchange,
    paymentRequests: Map<ID, PaymentRequest>,
    // Asked for each previewed request the store doesn't hold yet; the row redraws when it lands.
    requestPaymentRequest: (ID) -> Unit,
    // Hosted groups' lobbies; a row shows the ones waiting on a group the viewer hosts.
    lobbies: Map<ChatId, List<LobbyMember>> = emptyMap(),
): List<ConversationReference> {
    val tokensByMint = tokens.associateBy { it.address }
    return summaries.map { summary ->
        // The feed's group roster is usually just the viewer, so a group's last sender is named
        // from the profiles the transcript resolves. Anyone not there yet is asked for here;
        // SenderResolver collapses repeats, and the row re-emits when the write lands.
        if (senderProfiles != null) {
            summary.unnamedGroupSender(selfId, senderProfiles)?.let(chatCoordinator::requestSenderProfile)
        }
        summary.previewedPaymentRequestId()
            ?.takeIf { it !in paymentRequests }
            ?.let(requestPaymentRequest)
        summary.toConversationReference(selfId, tokensByMint, resources, senderProfiles.orEmpty(), exchange, paymentRequests)
            .copy(joinRequests = lobbies.waitingFor(summary.metadata, selfId).sortedByDescending { it.enteredAt })
    }
}
