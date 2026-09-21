package com.flipcash.shared.chat.ui

import com.flipcash.core.R
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.handle
import com.flipcash.shared.chat.ChatSummary
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper

/**
 * Maps a [ChatSummary] to the presentation [ConversationReference] shared by every
 * conversation-style list (send contacts, tip DMs, …): counterparty identity, a
 * formatted last-message preview, the last-activity timestamp, unread count, and the viewer's
 * own state on the chat.
 *
 * The counterparty ([ConversationReference.displayName]/[ConversationReference.image])
 * is taken from the chat member that isn't [selfId] — used directly by rows with no
 * separate contact (e.g. tip DMs); the send flow ignores those in favour of its matched
 * device contact.
 *
 * The handle rides along with the name because a tip DM counterparty need not have a display
 * name; [ConversationReference.name] is what rows should render.
 */
fun ChatSummary.toConversationReference(
    selfId: ID?,
    tokensByMint: Map<Mint, Token>,
    resources: ResourceHelper,
): ConversationReference {
    val isGroup = metadata.type == ChatType.GROUP
    val other = metadata.members.firstOrNull { it.userId != selfId }
    return ConversationReference(
        chatId = metadata.chatId,
        // A group has no single counterparty, and passing one would name the row after whichever
        // member happened to be first in a truncated roster.
        userId = other?.userId.takeUnless { isGroup },
        displayName = other?.userProfile?.displayName.takeUnless { isGroup },
        handle = other?.userProfile?.handle.takeUnless { isGroup },
        image = if (isGroup) metadata.picture else other?.userProfile?.profilePicture,
        title = metadata.title,
        isGroup = isGroup,
        lastMessagePreview = formatPreview(selfId, tokensByMint, resources),
        hasMessages = metadata.lastMessage != null,
        lastActivity = metadata.lastActivity,
        unreadCount = unreadCount,
        viewerState = metadata.viewerState,
    )
}

private fun ChatSummary.formatPreview(
    selfId: ID?,
    tokensByMint: Map<Mint, Token>,
    resources: ResourceHelper,
): String? {
    val lastMsg = metadata.lastMessage ?: return null
    val isGroup = metadata.type == ChatType.GROUP
    val sentBySelf = lastMsg.senderId != null && lastMsg.senderId == selfId
    // Only a group attributes, and only someone else's message: "You:" already covers the viewer's,
    // and a DM's counterparty is the row's own name. Null when the roster subset does not have the
    // sender — the list does not fetch profiles, so an unattributed body is the honest fallback.
    val senderName = if (isGroup && !sentBySelf) {
        metadata.members.firstOrNull { it.userId == lastMsg.senderId }?.userProfile?.displayName
    } else {
        null
    }
    return lastMsg.content.firstOrNull()
        ?.previewText(sentBySelf, senderName, isGroup, tokensByMint, resources)
}

/**
 * The row's line for one piece of message content, or null when there is nothing worth previewing.
 *
 * [depth] bounds the reply unwrap below. Nothing the app sends nests a reply inside a reply, but
 * this content arrives off the wire, so a malformed chain must not recurse forever.
 */
private fun MessageContent.previewText(
    sentBySelf: Boolean,
    senderName: String?,
    isGroup: Boolean,
    tokensByMint: Map<Mint, Token>,
    resources: ResourceHelper,
    depth: Int = 0,
): String? = when (this) {
    is MessageContent.Text -> {
        val message = text.takeIf { it.isNotEmpty() }
        when {
            message == null -> null
            sentBySelf -> resources.getString(R.string.label_chat_preview_sentMessage, message)
            senderName != null ->
                resources.getString(R.string.label_chat_preview_senderMessage, senderName, message)
            else -> message
        }
    }

    is MessageContent.Cash -> {
        val formatted = amount.formatted()
        // The reserve is branded "Dollars", so naming it reads as "$1.00 of Dollars" —
        // the amount alone already says it. Every other token still gets named.
        val name = if (mint == Mint.usdf) {
            ""
        } else {
            tokenName.ifBlank { tokensByMint[mint]?.name.orEmpty() }
        }
        val label = if (name.isNotBlank()) {
            resources.getString(R.string.label_chat_preview_cash_suffix, formatted, name)
        } else {
            formatted
        }
        // "You received" does not carry over: in a DM the cash came to the viewer, in a group it
        // went to the group and the viewer may have got none of it. So a group message from anyone
        // but the viewer previews as the amount alone — named when the roster subset has the
        // sender, bare when it does not.
        if (senderName != null) {
            val previewRes = when (action) {
                MessageContent.Cash.Action.TIPPED -> R.string.label_chat_preview_tippedCash_bySender
                MessageContent.Cash.Action.SENT -> R.string.label_chat_preview_sentCash_bySender
            }
            resources.getString(previewRes, senderName, label)
        } else if (isGroup && !sentBySelf) {
            label
        } else {
            val previewRes = when (action) {
                MessageContent.Cash.Action.TIPPED ->
                    if (sentBySelf) R.string.label_chat_preview_tippedCash else R.string.label_chat_preview_receivedCash
                MessageContent.Cash.Action.SENT ->
                    if (sentBySelf) R.string.label_chat_preview_sentCash else R.string.label_chat_preview_receivedCash
            }
            resources.getString(previewRes, label)
        }
    }

    // A reply wraps what the sender actually typed, so it previews as that content would have
    // without the citation. The row has no room to say what was cited, and the reply's own body is
    // the part that changed. The "You:" prefix comes from the inner content, so it lands once
    // rather than once per layer.
    is MessageContent.Reply -> if (depth >= MAX_REPLY_UNWRAP_DEPTH) {
        null
    } else {
        content.firstOrNull()
            ?.previewText(sentBySelf, senderName, isGroup, tokensByMint, resources, depth + 1)
    }

    // The feed carries the newest message that still has content, so a tombstone only
    // reaches here when every message in the chat is deleted — and then there is nothing
    // to preview.
    is MessageContent.Deleted -> null

    // TODO:
    is MessageContent.Media -> null
    is MessageContent.System -> null
}

private const val MAX_REPLY_UNWRAP_DEPTH = 4
