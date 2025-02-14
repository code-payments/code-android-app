package xyz.flipchat.services.internal.data.mapper

import com.codeinc.flipchat.gen.messaging.v1.Model
import com.getcode.services.mapper.Mapper
import xyz.flipchat.services.internal.network.chat.IsTyping
import xyz.flipchat.services.internal.network.chat.TypingState
import javax.inject.Inject

class TypingMapper @Inject constructor(): Mapper<Model.IsTyping, IsTyping> {
    override fun map(from: Model.IsTyping): IsTyping {
        return IsTyping(
            userId = from.userId.value.toList(),
            state = TypingState.entries.getOrElse(from.typingStateValue) { TypingState.Unknown }
        )
    }
}