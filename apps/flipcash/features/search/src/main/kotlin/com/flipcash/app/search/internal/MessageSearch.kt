package com.flipcash.app.search.internal

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.WidgetContent
import com.flipcash.shared.chat.ui.labelInviteLinks
import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

/** A stored message as Search reads it: which chat it is in, and the message itself. */
internal data class StoredMessage(val chatId: ChatId, val message: ChatMessage)

/** A message whose text matched the query. */
internal data class MessageHit(
    val chatId: ChatId,
    val messageId: Long,
    val senderId: ID?,
    val isFromSelf: Boolean,
    val text: String,
    val timestamp: Instant,
)

/**
 * The text of [content] that Search reads: message text, a reply's own text, media captions and
 * the username a share-profile widget carries. Cash, system, deleted and undecrypted content have
 * none. Null when nothing readable is left.
 */
internal fun searchableText(content: List<MessageContent>): String? =
    content.mapNotNull(::searchableText).joinToString(" ").takeIf { it.isNotBlank() }

private fun searchableText(content: MessageContent): String? = when (content) {
    is MessageContent.Text -> content.text
    is MessageContent.Reply -> searchableText(content.content)
    is MessageContent.Media -> content.caption?.text
    is MessageContent.Widget -> when (val widget = content.widget) {
        is WidgetContent.ShareProfile -> widget.username
        // Its title lives in the request, not the message.
        is WidgetContent.PaymentRequest, WidgetContent.Unsupported -> null
    }
    else -> null
}

/**
 * The messages in [messages] whose text contains [query] (already folded), newest first.
 *
 * Each invite link reads as [inviteLinkLabel], both for matching and in the hit's text. Its fragment
 * is the group's key, so a query can't find a message by its secret and a result never shows it.
 */
internal fun searchMessages(
    messages: List<StoredMessage>,
    query: String,
    inviteLinkLabel: String,
): List<MessageHit> =
    messages.mapNotNull { (chatId, message) ->
        val text = searchableText(message.content)
            ?.let { labelInviteLinks(it, inviteLinkLabel) }
            ?: return@mapNotNull null
        if (!SearchMatching.matches(query, text)) return@mapNotNull null
        MessageHit(
            chatId = chatId,
            messageId = message.messageId,
            senderId = message.senderId,
            isFromSelf = message.isFromSelf,
            text = text,
            timestamp = message.timestamp,
        )
    }.sortedByDescending { it.timestamp }
