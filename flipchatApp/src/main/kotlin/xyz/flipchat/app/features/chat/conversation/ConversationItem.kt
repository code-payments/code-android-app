package xyz.flipchat.app.features.chat.conversation

import androidx.compose.runtime.Stable
import com.getcode.model.ID
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.Sender
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
    data class Spectator(val cover: Kin): ChattableState

    fun isActiveMember() = this is Enabled || this is DisabledByMute
}

data class MessageReplyAnchor(
    val id: ID,
    val sender: Sender,
    val message: MessageContent
)