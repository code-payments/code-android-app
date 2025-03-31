package com.getcode.view.main.balance

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import com.getcode.model.chat.Chat
import com.getcode.model.chat.MessageContent
import com.getcode.ui.components.chat.ChatNode
import com.getcode.ui.components.chat.utils.localized
import com.getcode.ui.components.chat.utils.localizedText

@Composable
fun ChatNode(
    modifier: Modifier = Modifier,
    chat: Chat,
    showAvatar: Boolean = false,
    onClick: () -> Unit,
) {
    ChatNode(
        modifier = modifier,
        title = chat.title.localized,
        messagePreview = chat.messagePreview,
        avatar = if (showAvatar) chat.imageData else null,
        timestamp = chat.lastMessageMillis,
        isMuted = chat.isMuted,
        unreadCount = chat.unreadCount,
        onClick = onClick
    )
}

private val Chat.messagePreview: Pair<AnnotatedString, Map<String, InlineTextContent>>
    @Composable get() {
        val contents = newestMessage?.contents ?: return AnnotatedString("No content") to emptyMap()

        var filtered: List<MessageContent> = contents.filterIsInstance<MessageContent.Localized>()
        if (filtered.isEmpty()) {
            filtered = contents
        }

        // joinToString does expose a Composable scoped lambda
        @Suppress("SimplifiableCallChain")
        val messageBody = filtered.map { it.localizedText }.joinToString(" ")

        return AnnotatedString(messageBody) to emptyMap()
    }