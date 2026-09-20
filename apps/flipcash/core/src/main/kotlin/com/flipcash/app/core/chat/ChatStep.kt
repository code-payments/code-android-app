package com.flipcash.app.core.chat

import android.os.Parcelable
import com.getcode.navigation.Sheet
import com.getcode.navigation.WrapContentSheet
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

    /**
     * The payment that opens a tip DM, at the fee the recipient charges.
     *
     * Separate from [AmountEntry] because there is nothing to enter: the fee is the price of the
     * conversation, so the sheet states it and asks only for the swipe. A [WrapContentSheet] for
     * the same reason — three lines of content shouldn't claim the screen.
     */
    @Parcelize
    @Serializable
    data object InitPayment :
        ChatStep, NavigationRetVal<ChatSendResult>, Sheet, WrapContentSheet

    /**
     * Node 10127:118315 — the two ways to hand out a group's invite link.
     *
     * A [WrapContentSheet] over the conversation rather than a screen of its own: it is two rows,
     * and inviting is something you do *from* the group, not a place you go. Reached from the empty
     * transcript's CTA and from the group's profile, which is why it hangs off the chat flow rather
     * than the create flow that used to end on it.
     */
    @Parcelize
    @Serializable
    data object InviteToGroup : ChatStep, Sheet, WrapContentSheet

    /**
     * How long to mute the conversation for.
     *
     * A [WrapContentSheet] for the same reason [InviteToGroup] is one: four rows, chosen from the
     * group's profile, and the profile is where you end up again once one is picked. Carries
     * nothing — the chat is the one the flow is already open on, and the shape the user picks
     * travels back as an event rather than as a nav result, because muting is a request the view
     * model makes rather than an answer the sheet returns.
     */
    @Parcelize
    @Serializable
    data object MuteChat : ChatStep, Sheet, WrapContentSheet

    @Parcelize
    @Serializable
    data class Profile(val contact: ChatParticipant): ChatStep

    /**
     * The group's own profile — the counterpart of [Profile] for a chat that has no counterparty.
     *
     * Carries nothing, because there is nothing a group can be identified by that the flow does
     * not already hold: the screen reads the group off the conversation's view model, the same
     * way the transcript does. [Profile] has to carry its participant because a member's profile
     * is opened from a bubble rather than from the chat the flow was opened on.
     */
    @Parcelize
    @Serializable
    data object GroupProfile : ChatStep
}
