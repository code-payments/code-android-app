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

    /**
     * Node 10187:110373 — what about the group can be changed, as a list of the things that can.
     *
     * A pushed screen rather than a sheet, unlike [InviteToGroup]: that ends on the choice it
     * presents, while this one is a way through to another screen and has to be
     * somewhere back can return to. Carries nothing for the same reason [GroupProfile] does not —
     * the flow is already open on the group, and every step here reads it off the conversation's
     * view model.
     *
     * The design node lists four rows; only [EditGroupPicture] and [EditGroupName] are built.
     * Membership card, description and social links have no field on `EditChatRequest` in
     * flipcash2 0.11.0, so there is nothing for them to write.
     */
    @Parcelize
    @Serializable
    data object EditGroup : ChatStep

    /** The group's title, on the same entry screen shape the profile name uses. */
    @Parcelize
    @Serializable
    data object EditGroupName : ChatStep

    /** The group's picture — pick, upload, then `EditChat` with the READY blob. */
    @Parcelize
    @Serializable
    data object EditGroupPicture : ChatStep
}
