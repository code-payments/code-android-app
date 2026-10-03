package com.flipcash.app.tipping.internal

import com.flipcash.services.models.UserProfile
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatSummary
import com.flipcash.shared.chat.ui.ConversationReference
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
): List<ConversationReference> {
    val tokensByMint = tokens.associateBy { it.address }
    return summaries.map { summary ->
        // The feed's group roster is usually just the viewer, so a group's last sender is named
        // from the profiles the transcript resolves. Anyone not there yet is asked for here;
        // SenderResolver collapses repeats, and the row re-emits when the write lands.
        if (senderProfiles != null) {
            summary.unnamedGroupSender(selfId, senderProfiles)?.let(chatCoordinator::requestSenderProfile)
        }
        summary.toConversationReference(selfId, tokensByMint, resources, senderProfiles.orEmpty())
    }
}
