package com.flipcash.shared.chat.models

import androidx.compose.runtime.staticCompositionLocalOf
import com.flipcash.services.models.UserProfile
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.Mint

sealed interface ChatAction {
    data class RetryMessage(val bubble: ChatListItem.ContentBubble) : ChatAction
    data class AdvanceReadPointer(val messageId: Long) : ChatAction
    object RefreshContact : ChatAction
    data class ViewToken(val mint: Mint) : ChatAction
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
     * The reader tapped a cash voucher, naming the link they are about to open.
     *
     * Not a request to claim it, and not what opens it: the tap still leaves through the URL
     * handler and comes back as a deeplink, which is what keeps the card path off the claim path.
     * This is the chat saying which link left from here, because that is the one thing lost at
     * that boundary — a claim arrives back knowing an entropy and nothing about a transcript.
     */
    data class CashLinkOpened(val entropy: String) : ChatAction

    /**
     * The reader tapped a tip card link, asking for the tip DM with its owner.
     *
     * Carries what the card already resolved rather than the URL. [userId] is what the canonical
     * TIP_DM id is derived from, so the conversation opens whether or not it exists yet, and
     * [profile] is what draws its header on the first frame — a chat with no messages has no
     * members fetch to wait on. It is the same pair the real tip card hands the navigator when it
     * is dismissed (`TipCardDecorator`), which is what makes the link and the card the same door.
     */
    data class OpenTipChat(val userId: ID, val profile: UserProfile) : ChatAction
}

typealias ChatActionHandler = (ChatAction) -> Unit

val LocalChatActionHandler = staticCompositionLocalOf<ChatActionHandler> { {} }
