package com.flipcash.app.core.chat

import android.os.Parcelable
import com.flipcash.services.models.chat.ChatId
import com.getcode.navigation.HalfSheet
import com.getcode.navigation.Sheet
import com.getcode.navigation.WrapContentSheet
import com.getcode.navigation.flow.FlowStep
import com.getcode.navigation.results.NavigationRetVal
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data object ChatSendResult : Parcelable

/**
 * The chat the invite sheet sends the viewer to once its invites are out: the first one picked.
 *
 * Returned rather than navigated to from the sheet, because the sheet's own navigation raced its
 * dismissal and was dropped when the conversation's event collector was not on screen (the sheet
 * opened from the group's profile). The screen that opened the sheet is still composed when the
 * result arrives, so it navigates.
 */
@Parcelize
@Serializable
data class GroupInviteResult(val chatId: ChatId) : Parcelable

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
     * Nodes 10329:12104 and 10330:19549 — share or copy a group's invite link, or send it to
     * recent 1:1 chats.
     *
     * A sheet over the conversation rather than a screen of its own: inviting is something you do
     * *from* the group, not a place you go. Reached from the empty transcript's CTA and from the
     * group's profile, which is why it hangs off the chat flow rather than the create flow that
     * used to end on it.
     *
     * Not a [WrapContentSheet]: the chat list can run past the screen, and a wrap-content body is
     * measured at its full height and then clipped, taking the message bar under it off the bottom.
     * A full sheet bounds the body, so the list scrolls and the bar stays on screen. Full screen,
     * as on iOS, which presents it as a cover.
     */
    @Parcelize
    @Serializable
    data object InviteToGroup :
        ChatStep,
        NavigationRetVal<GroupInviteResult>,
        Sheet,
        com.getcode.navigation.FullscreenSheet

    @Parcelize
    @Serializable
    data class Profile(
        val contact: ChatParticipant,
        val origin: ProfileOrigin = ProfileOrigin.Chat,
    ): ChatStep

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

    /**
     * The full emoji picker for a message's reaction, opened from the pill row's "+" or the
     * quick strip's "+". A plain [Sheet] rather than [WrapContentSheet]: the picker's catalog
     * needs the screen's height, unlike the short, fixed-content sheets above. [HalfSheet]
     * matches iOS's `EmojiPickerSheet.presentationDetents([.medium, .large])` — it rests at half
     * height and only grows to the expanded detent once the grid reports it has more than that to
     * show (see `AllowSheetExpansionWhenScrollable`).
     */
    @Parcelize
    @Serializable
    data class ReactionPicker(val messageId: Long) : ChatStep, Sheet, HalfSheet

    /**
     * Who reacted, and with what — opened by long-pressing a pill. A plain [Sheet] for the same
     * reason as [ReactionPicker]: a per-emoji reactor list can run long enough to need the full
     * sheet height rather than wrapping its content. [HalfSheet] matches iOS's
     * `ReactorsSheet.presentationDetents([.medium, .large])`.
     */
    @Parcelize
    @Serializable
    data class Reactors(val chatId: ChatId, val messageId: Long) : ChatStep, Sheet, HalfSheet
}

/** What opened a [ChatStep.Profile], which decides the actions it offers. */
@Serializable
enum class ProfileOrigin {
    /**
     * The chat's own title, or a member's picture or reactor row. The chat decides: a tip DM's
     * profile is the other person in it, so it offers Mute and not Message.
     */
    Chat,

    /**
     * A tapped `@handle` naming someone other than the DM's counterpart. That person is not who
     * the chat is with, so Mute would act on the wrong chat and Message and Send Cash have
     * somewhere new to go. iOS's `UserProfileOrigin.mention`.
     */
    Mention,

    /** A `flipcash.com/...` link. The default for `AppRoute.Messaging.Profile`. Blocking pops back. */
    Link,

    /** A scanned profile card. Blocking resets to the chat list, which the scan came from. */
    Scan,

    /** A username search result. Blocking resets to the chat list, like a scan. */
    UsernameLookup,

    /** A transaction's counterparty. Blocking pops back to the transaction. */
    Transaction;

    /** Whether blocking from here leaves the stack to the chat list rather than popping one entry. */
    val resetsToChatsAfterBlock: Boolean
        get() = this == Scan || this == UsernameLookup
}
