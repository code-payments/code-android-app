package com.flipcash.app.search.internal

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.WidgetContent
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
        WidgetContent.Unsupported -> null
    }
    else -> null
}

/** The messages in [messages] whose text contains [query] (already folded), newest first. */
internal fun searchMessages(messages: List<StoredMessage>, query: String): List<MessageHit> =
    messages.mapNotNull { (chatId, message) ->
        val text = searchableText(message.content) ?: return@mapNotNull null
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
