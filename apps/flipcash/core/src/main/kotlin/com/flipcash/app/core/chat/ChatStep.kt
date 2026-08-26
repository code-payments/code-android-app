package com.flipcash.app.core.chat

import android.os.Parcelable
import com.getcode.navigation.Sheet
import com.getcode.navigation.flow.FlowStep
import com.getcode.navigation.results.NavigationRetVal
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data object ChatSendResult : Parcelable

@Serializable
sealed interface ChatStep : FlowStep, Parcelable {
    @Parcelize
    @Serializable
    data object Conversation : ChatStep

    /**
     * Amount entry for an in-chat send, presented as a bottom sheet over the conversation.
     *
     * A [Sheet] rather than a pushed step so the thread stays on screen behind it — the amount is
     * being sent to the conversation you can still see, and dismissing it returns you to the
     * message you were part-way through. The chat's [com.getcode.navigation.flow.FlowHost] runs
     * the sheet scene strategy for this; see `ChatFlowScreen`.
     */
    @Parcelize
    @Serializable
    data object AmountEntry : ChatStep, NavigationRetVal<ChatSendResult>, Sheet

    @Parcelize
    @Serializable
    data class Profile(val contact: ChatParticipant): ChatStep
}
