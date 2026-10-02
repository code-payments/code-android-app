package com.flipcash.app.notifications

import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.services.models.PushChatMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ui.detectMentions
import com.flipcash.shared.chat.ui.detectUrls
import javax.inject.Inject

/**
 * Whether a received message is addressed to the viewer: the two ways a push breaks through an
 * archived chat's silence (rule 3).
 */
data class PushClassification(
    val mentionsViewer: Boolean,
    val repliesToViewer: Boolean,
) {
    companion object {
        /** Not addressed to the viewer, or not knowable. The silent default. */
        val None = PushClassification(mentionsViewer = false, repliesToViewer = false)
    }
}

/**
 * Reads a push's message against what this device knows.
 *
 * Both answers default to false when the device cannot tell, which is rule 3's "stays silent":
 * - the message is not in the payload and not yet stored, or has no readable text (encrypted,
 *   cash, media without a caption), so there is nothing to scan;
 * - the viewer has no username, so no handle can be theirs;
 * - the replied-to message is not stored locally, so its author is unknown.
 *
 * Mentions are `@handle` text found by [detectMentions], compared case-insensitively with the
 * viewer's username. A reply is a [MessageContent.Reply] whose cited message was sent by the
 * viewer.
 */
class PushMessageClassifier @Inject constructor(
    private val userManager: UserManager,
    private val messages: ChatMessageDataSource,
) {

    suspend fun classify(chatId: ChatId, metadata: PushChatMetadata?): PushClassification {
        val message = metadata?.message
            ?: metadata?.messageId?.let { messages.getMessage(chatId, it) }
            ?: return PushClassification.None

        return PushClassification(
            mentionsViewer = mentionsViewer(message),
            repliesToViewer = repliesToViewer(chatId, message),
        )
    }

    private fun mentionsViewer(message: ChatMessage): Boolean {
        val handle = userManager.profile?.username?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
        return message.content.texts().any { text ->
            detectMentions(text, detectUrls(text)).any { it.username == handle }
        }
    }

    private suspend fun repliesToViewer(chatId: ChatId, message: ChatMessage): Boolean {
        val selfId = userManager.accountId ?: return false
        val cited = message.content.filterIsInstance<MessageContent.Reply>().firstOrNull() ?: return false
        return messages.getMessage(chatId, cited.repliedMessageId)?.senderId == selfId
    }

    /** Every piece of plain text a message carries: its body, a reply's nested body, a caption. */
    private fun List<MessageContent>.texts(): List<String> = flatMap { content ->
        when (content) {
            is MessageContent.Text -> listOf(content.text)
            is MessageContent.Reply -> content.content.filterIsInstance<MessageContent.Text>().map { it.text }
            is MessageContent.Media -> listOfNotNull(content.caption?.text)
            else -> emptyList()
        }
    }
}
