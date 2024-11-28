package xyz.flipchat.app.features.chat.conversation

import androidx.compose.runtime.Stable
import com.getcode.model.chat.MessageContent
import xyz.flipchat.services.domain.model.chat.ConversationMember
import xyz.flipchat.services.domain.model.chat.ConversationMessage

@Stable
data class ConversationMessageIndice(
    val message: ConversationMessage,
    val sender: ConversationMember?,
    val messageContent: MessageContent,
)

sealed interface ChattableState {
    data object Enabled: ChattableState
    data object DisabledByMute: ChattableState
}