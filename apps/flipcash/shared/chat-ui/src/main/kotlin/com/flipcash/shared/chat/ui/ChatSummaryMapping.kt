package com.flipcash.shared.chat.ui

import com.flipcash.core.R
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.WidgetContent
import com.flipcash.services.models.handle
import com.flipcash.shared.chat.ChatSummary
import com.flipcash.shared.chat.media.ChatMediaText
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.hexEncodedString

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
 *
 * [senderProfiles] names a group's last sender when the roster does not: the feed carries a
 * roster subset that is often only the viewer, so without it a group preview is never attributed.
 * Keyed by user-id hex, as [com.flipcash.shared.chat.ChatCoordinator.observeSenderProfiles] emits.
 */
fun ChatSummary.toConversationReference(
    selfId: ID?,
    tokensByMint: Map<Mint, Token>,
    resources: ResourceHelper,
    senderProfiles: Map<String, UserProfile> = emptyMap(),
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
        chatType = metadata.type,
        lastMessagePreview = formatPreview(selfId, tokensByMint, resources, senderProfiles),
        hasMessages = metadata.lastMessage != null,
        lastActivity = metadata.lastActivity,
        unreadCount = unreadCount,
        viewerState = metadata.viewerState,
    )
}

/**
 * The sender of a group's last message when nothing on the device can name them yet — neither the
 * roster subset nor [senderProfiles] — so the list can ask for their profile. Null for a DM, for
 * the viewer's own message, and for a sender already named.
 */
fun ChatSummary.unnamedGroupSender(selfId: ID?, senderProfiles: Map<String, UserProfile>): ID? {
    if (metadata.type != ChatType.GROUP) return null
    val senderId = metadata.lastMessage?.senderId ?: return null
    if (senderId == selfId) return null
    return senderId.takeIf { groupSenderName(senderId, senderProfiles) == null }
}

private fun ChatSummary.groupSenderName(senderId: ID, senderProfiles: Map<String, UserProfile>): String? {
    // A blank name counts as missing: a cached member with no profile row maps to an empty one,
    // which would otherwise read as ": gm".
    val fromRoster = metadata.members.firstOrNull { it.userId == senderId }
        ?.userProfile?.displayName?.takeIf { it.isNotBlank() }
    return fromRoster
        ?: senderProfiles[senderId.hexEncodedString()]?.displayName?.takeIf { it.isNotBlank() }
}

private fun ChatSummary.formatPreview(
    selfId: ID?,
    tokensByMint: Map<Mint, Token>,
    resources: ResourceHelper,
    senderProfiles: Map<String, UserProfile>,
): String? {
    val lastMsg = metadata.lastMessage ?: return null
    val isGroup = metadata.type == ChatType.GROUP
    val sentBySelf = lastMsg.senderId != null && lastMsg.senderId == selfId
    // Only a group attributes, and only someone else's message: "You:" already covers the viewer's,
    // and a DM's counterparty is the row's own name. Null when neither the roster subset nor the
    // resolved profiles have the sender yet — an unattributed body is the honest fallback until
    // the profile lands.
    val senderName = if (isGroup && !sentBySelf) {
        lastMsg.senderId?.let { groupSenderName(it, senderProfiles) }
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
    is MessageContent.Encrypted -> null

    // The caption, else "Photo", behind the camera emoji; prefixed like a typed message.
    is MessageContent.Media -> {
        val message = ChatMediaText.preview(caption?.text, resources.getString(R.string.label_chat_media_photo))
        when {
            sentBySelf -> resources.getString(R.string.label_chat_preview_sentMessage, message)
            senderName != null ->
                resources.getString(R.string.label_chat_preview_senderMessage, senderName, message)
            else -> message
        }
    }
    is MessageContent.System -> null

    // A widget this client can't draw previews as nothing, as an undecryptable message does.
    is MessageContent.Widget -> when (widget) {
        is WidgetContent.ShareProfile -> resources.getString(R.string.label_chat_preview_sharedProfile)
        WidgetContent.Unsupported -> null
    }
}

private const val MAX_REPLY_UNWRAP_DEPTH = 4
