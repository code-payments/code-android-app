package com.flipcash.app.messenger.internal.screens.profile

import androidx.annotation.StringRes
import com.flipcash.features.messenger.R
import com.flipcash.app.messenger.internal.MembershipCheck
import com.flipcash.app.messenger.internal.membershipCheck
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat

/** The one primary action pinned to the bottom of another user's profile. */
internal sealed interface ProfilePinnedAction {
    data object Unblock : ProfilePinnedAction
    data object OpenChat : ProfilePinnedAction

    /** The fee is paid and the DM has not appeared yet; shown disabled, so it cannot be paid twice. */
    data object OpeningChat : ProfilePinnedAction

    data class StartChatting(val fee: Fiat) : ProfilePinnedAction
}

/**
 * Null for your own profile, and for a person with no DM while [fee] is still null. The fee falls
 * back to the regional default, so null only means it has not loaded yet; the button waits for it
 * rather than offer a payment with no amount to confirm. Blocked wins over an existing DM, because
 * a blocked DM is hidden.
 */
internal fun resolvePinnedAction(
    isSelf: Boolean,
    isBlocked: Boolean,
    dmExists: Boolean,
    fee: Fiat?,
    paid: Boolean = false,
): ProfilePinnedAction? = when {
    isSelf -> null
    isBlocked -> ProfilePinnedAction.Unblock
    dmExists -> ProfilePinnedAction.OpenChat
    paid -> ProfilePinnedAction.OpeningChat
    else -> fee?.let(ProfilePinnedAction::StartChatting)
}

/**
 * Whether the pinned button is left off because it would only open [chatUnderneath], the chat
 * this profile was opened from: back already returns there.
 */
internal fun ProfilePinnedAction.opensChatUnderneath(dmChatId: ChatId?, chatUnderneath: ChatId?): Boolean =
    this == ProfilePinnedAction.OpenChat && chatUnderneath != null && dmChatId == chatUnderneath

/**
 * The rows under another user's profile, in order: the reversible and routine first, the one that
 * ends the conversation last. Muting is the DM's, so it only appears once one exists.
 *
 * [group] is the group chat the profile sits in, when it sits in one; its Silence and Remove follow
 * as their own section, each only when the viewer holds the grant, and neither on the group's creator.
 */
internal fun profileMenuItems(
    isBlocked: Boolean,
    hasDm: Boolean,
    group: ProfileGroup? = null,
    userId: ID? = null,
): List<ChatProfileAction> = buildList {
    if (isBlocked) {
        add(ChatProfileAction.Report)
        add(ChatProfileAction.Unblock)
    } else {
        if (hasDm) add(ChatProfileAction.Mute)
        add(ChatProfileAction.Report)
        add(ChatProfileAction.Block)
    }
    if (group != null && userId != null && userId != group.creatorId && group.check(userId) != MembershipCheck.NotMember) {
        if (group.canSilence) add(ChatProfileAction.Silence)
        if (group.canBan) add(ChatProfileAction.Remove)
    }
}

/** The group chat a profile was opened inside, and what the viewer may do to its members. */
internal data class ProfileGroup(
    val chatId: ChatId,
    val title: String,
    val creatorId: ID?,
    val canSilence: Boolean,
    val canBan: Boolean,
    /** A private group's roster, or null until it is read or when the read failed. */
    val roster: List<ChatMember>? = null,
    val isPrivate: Boolean = false,
    /** The roster read failed, so membership could not be confirmed. */
    val rosterFailed: Boolean = false,
) {
    fun check(userId: ID): MembershipCheck = membershipCheck(isPrivate, roster, rosterFailed, userId)
}

/** The group items shown but not tappable: a private group's, until the roster confirms the person. */
internal fun disabledProfileItems(group: ProfileGroup?, userId: ID?): Set<ChatProfileAction> =
    if (group != null && userId != null && group.check(userId) in setOf(MembershipCheck.Pending, MembershipCheck.Failed)) {
        setOf(ChatProfileAction.Silence, ChatProfileAction.Remove)
    } else {
        emptySet()
    }

/**
 * The string behind the pinned button. Only [ProfilePinnedAction.StartChatting] takes an argument,
 * the formatted fee.
 */
@StringRes
internal fun ProfilePinnedAction.labelRes(): Int = when (this) {
    ProfilePinnedAction.Unblock -> R.string.action_unblock
    ProfilePinnedAction.OpenChat, ProfilePinnedAction.OpeningChat -> R.string.action_openChat
    is ProfilePinnedAction.StartChatting -> R.string.action_sendToStartChatting
}
