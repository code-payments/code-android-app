package com.flipcash.shared.chat.ui

import com.flipcash.core.R
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.ChatSummary
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper

/**
 * Maps a [ChatSummary] to the presentation [ConversationReference] shared by every
 * conversation-style list (send contacts, tip DMs, …): counterparty identity, a
 * formatted last-message preview, the last-activity timestamp, and unread count.
 *
 * The counterparty ([ConversationReference.displayName]/[ConversationReference.image])
 * is taken from the chat member that isn't [selfId] — used directly by rows with no
 * separate contact (e.g. tip DMs); the send flow ignores those in favour of its matched
 * device contact.
 */
fun ChatSummary.toConversationReference(
    selfId: ID?,
    tokensByMint: Map<Mint, Token>,
    resources: ResourceHelper,
): ConversationReference {
    val other = metadata.members.firstOrNull { it.userId != selfId }
    return ConversationReference(
        chatId = metadata.chatId,
        displayName = other?.userProfile?.displayName,
        image = other?.userProfile?.profilePicture,
        lastMessagePreview = formatPreview(selfId, tokensByMint, resources),
        lastActivity = metadata.lastActivity,
        unreadCount = unreadCount,
    )
}

private fun ChatSummary.formatPreview(
    selfId: ID?,
    tokensByMint: Map<Mint, Token>,
    resources: ResourceHelper,
): String? {
    val lastMsg = metadata.lastMessage ?: return null
    val sentBySelf = lastMsg.senderId != null && lastMsg.senderId == selfId
    return lastMsg.content.firstOrNull()?.let { content ->
        when (content) {
            is MessageContent.Text -> {
                val message = content.text.takeIf { it.isNotEmpty() } ?: return null
                if (sentBySelf) {
                    resources.getString(R.string.label_chat_preview_sentMessage, message)
                } else {
                    message
                }
            }
            is MessageContent.Cash -> {
                val formatted = content.amount.formatted()
                val name = content.tokenName.ifBlank { tokensByMint[content.mint]?.name.orEmpty() }
                val label = if (name.isNotBlank()) {
                    resources.getString(R.string.label_chat_preview_cash_suffix, formatted, name)
                } else {
                    formatted
                }
                val previewRes = when (content.action) {
                    MessageContent.Cash.Action.TIPPED ->
                        if (sentBySelf) R.string.label_chat_preview_tippedCash else R.string.label_chat_preview_receivedTip
                    MessageContent.Cash.Action.SENT ->
                        if (sentBySelf) R.string.label_chat_preview_sentCash else R.string.label_chat_preview_receivedCash
                }
                resources.getString(previewRes, label)
            }

            // TODO:
            is MessageContent.Deleted -> null
            is MessageContent.Media -> null
            is MessageContent.Reply -> null
            is MessageContent.System -> null
        }
    }
}
