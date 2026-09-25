package com.flipcash.shared.chat.models

import androidx.compose.runtime.staticCompositionLocalOf
import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.Mint

sealed interface ChatAction {
    data class RetryMessage(val bubble: ChatListItem.ContentBubble) : ChatAction
    data class AdvanceReadPointer(val messageId: Long) : ChatAction
    object RefreshContact : ChatAction
    /**
     * Opens a currency's info screen. [returnAfterBuy] brings the reader back here once they buy
     * it — set by the group gate, where buying is how they get in.
     */
    data class ViewToken(val mint: Mint, val returnAfterBuy: Boolean = false) : ChatAction

    /**
     * Opens the add-cash flow — the group gate's answer to a balance rule any holding satisfies,
     * or one on the reserve, where there is no other token to buy.
     */
    data object AddCash : ChatAction

    /**
     * Opens the group a link card names, pushed over this chat so Back returns here. The pushed
     * screen gates itself; a card never joins or buys on the reader's behalf.
     */
    data class OpenGroup(val chatId: ChatId) : ChatAction
    data object ViewProfile : ChatAction

    /**
     * Opens the profile of the member who sent a bubble, from their picture in the gutter.
     *
     * Separate from [ViewProfile], which opens whoever the chat itself is with. A group is
     * with no one, so the only profile reachable from its transcript is a sender's, and the
     * id is how the handler finds them — the profile it needs is the one the transcript
     * already resolved to draw this picture.
     */
    data class ViewMemberProfile(val userId: ID) : ChatAction

    /** Joins the group this screen is showing. */
    data object JoinChat : ChatAction

    /**
     * Shares the link that invites someone into this group.
     *
     * The link itself is not carried: the screen state already builds it from the chat id, and the
     * handler reads it from there, so the action stays a verb rather than a payload the transcript
     * would have to keep in sync.
     */
    data object InviteToGroup : ChatAction

    /**
     * Adds [bubble] to the selection, or removes it if it is already selected.
     *
     * The bubble travels whole rather than as an id because the selection bar needs what the
     * transcript already resolved — the capability set and the body to copy or edit — and re-reading
     * it from the paging list would mean deciding availability at the menu instead.
     */
    data class ToggleSelection(val bubble: ChatListItem.ContentBubble) : ChatAction

    /** Leaves selection mode without acting on anything. */
    data object ClearSelection : ChatAction

    /** Abandons an edit in progress, as a tap on the backdrop behind the edited message does. */
    data object CancelEdit : ChatAction

    /** Scrolls the transcript to the message a quote cites. */
    data class JumpToMessage(val messageId: Long) : ChatAction

    /** Opens the composer's reply strip for [bubble]. */
    data class ReplyTo(val bubble: ChatListItem.ContentBubble) : ChatAction

    /** Takes the reply strip back down, leaving the draft where it is. */
    data object CancelReply : ChatAction

    /**
     * The reader tapped a cash voucher.
     *
     * Not a request to claim it. The handler decides whether [url] opens at all — a non-member
     * reading a group is told to join first — and when it does, the link leaves through the URL
     * handler and comes back as a deeplink, which is what keeps the card path off the claim path.
     * [entropy] is the chat noting which link left from here, because that is the one thing lost at
     * that boundary — a claim arrives back knowing an entropy and nothing about a transcript.
     */
    data class CashLinkOpened(val entropy: String, val url: String) : ChatAction

    /**
     * Toggles the viewer's reaction with [emoji] on [messageId] — a tap on a pill, on a quick-strip
     * entry, or a pick from the full picker all resolve to this one action. [fromStrip] is true
     * only for the quick strip above a selected bubble, whose tap also exits selection mode.
     */
    data class ToggleReaction(
        val messageId: Long,
        val emoji: String,
        val fromStrip: Boolean = false,
    ) : ChatAction

    /** Opens the full emoji picker for [messageId], from the pill row's or the quick strip's "+". */
    data class OpenReactionPicker(val messageId: Long) : ChatAction

    /** Opens who-reacted for [messageId], from a long-press on a pill. */
    data class OpenReactors(val messageId: Long) : ChatAction
}

typealias ChatActionHandler = (ChatAction) -> Unit

val LocalChatActionHandler = staticCompositionLocalOf<ChatActionHandler> { {} }
