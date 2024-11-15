package xyz.flipchat.features.chat.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.getcode.model.chat.MessageContent
import com.getcode.oct24.R
import com.getcode.ui.components.chat.utils.localizedText
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastMessage

@Composable
fun ChatNode(
    chat: ConversationWithMembersAndLastMessage,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    com.getcode.ui.components.chat.ChatNode(
        modifier = modifier,
        title = chat.title,
        messagePreview = chat.messagePreview,
        avatar = chat.imageUri ?: chat.id,
        avatarIconWhenFallback = {
            Image(
                modifier = Modifier.padding(5.dp),
                painter = painterResource(R.drawable.ic_fc_chats),
                colorFilter = ColorFilter.tint(Color.White),
                contentDescription = null,
            )
        },
        timestamp = chat.lastMessage?.message?.dateMillis,
        isMuted = chat.isMuted,
        unreadCount = chat.unreadCount,
        onClick = onClick
    )
}

private val ConversationWithMembersAndLastMessage.messagePreview: Pair<AnnotatedString, Map<String, InlineTextContent>>
    @Composable get() {
        val contents = lastMessage?.contents ?: return AnnotatedString("No content") to emptyMap()

        var filtered: List<MessageContent> = contents.filterIsInstance<MessageContent.Localized>()
        if (filtered.isEmpty()) {
            filtered = contents
        }

        // joinToString does expose a Composable scoped lambda
        @Suppress("SimplifiableCallChain")
        val messageBody = filtered.map { it.localizedText }.joinToString(" ")

        return AnnotatedString(messageBody) to emptyMap()
    }