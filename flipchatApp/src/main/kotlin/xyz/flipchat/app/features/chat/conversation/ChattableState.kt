package xyz.flipchat.app.features.chat.conversation

import com.getcode.model.Kin
import xyz.flipchat.app.features.chat.conversation.ChattableState.Enabled
import xyz.flipchat.app.features.chat.conversation.ChattableState.Spectator
import xyz.flipchat.app.features.chat.conversation.ChattableState.TemporarilyEnabled


sealed interface ChattableState {
    interface Active
    data object DisabledByMute: ChattableState, Active
    data class Spectator(val messageFee: Kin): ChattableState
    data object TemporarilyEnabled: ChattableState
    data object Enabled: ChattableState
    data object DisabledByClosedRoom: ChattableState

    fun isActiveMember() = this is Active
}

fun ChattableState?.canTriggerInput() = this == null || this is Spectator || this is Enabled || this is TemporarilyEnabled