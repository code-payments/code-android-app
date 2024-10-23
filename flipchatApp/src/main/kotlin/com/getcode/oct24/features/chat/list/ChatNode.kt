package com.getcode.oct24.features.chat.list

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import com.getcode.model.chat.MessageContent
import com.getcode.oct24.data.Room
import com.getcode.ui.components.chat.utils.localizedText

@Composable
fun ChatNode(
    chat: Room,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    com.getcode.ui.components.chat.ChatNode(
        modifier = modifier,
        title = chat.title,
        messagePreview = chat.messagePreview,
        avatar = chat.imageData,
        timestamp = chat.lastMessageMillis,
        isMuted = chat.isMuted,
        unreadCount = chat.unreadCount,
        onClick = onClick
    )
}

private val Room.messagePreview: Pair<AnnotatedString, Map<String, InlineTextContent>>
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