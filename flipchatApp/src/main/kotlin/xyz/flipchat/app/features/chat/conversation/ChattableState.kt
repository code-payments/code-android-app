package xyz.flipchat.app.features.chat.conversation

import com.getcode.model.Kin


sealed interface ChattableState {
    interface Active
    data object DisabledByMute: ChattableState, Active
    data class Spectator(val messageFee: Kin): ChattableState
    data object TemporarilyEnabled: ChattableState
    data object Enabled: ChattableState
    data object DisabledByClosedRoom: ChattableState

    fun isActiveMember() = this is Active
}