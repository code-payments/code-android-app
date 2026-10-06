package com.flipcash.app.messenger.internal.screens.profile

import androidx.annotation.StringRes
import com.flipcash.features.messenger.R
import com.getcode.opencode.model.financial.Fiat

/** The one primary action pinned to the bottom of another user's profile. */
internal sealed interface ProfilePinnedAction {
    data object Unblock : ProfilePinnedAction
    data object OpenChat : ProfilePinnedAction

    /** A null [fee] means "Start Chatting" opens the amount entry rather than a confirmation. */
    data class StartChatting(val fee: Fiat?) : ProfilePinnedAction
}

/**
 * Null for your own profile. Blocked wins over an existing DM, because a blocked DM is hidden.
 */
internal fun resolvePinnedAction(
    isSelf: Boolean,
    isBlocked: Boolean,
    dmExists: Boolean,
    fee: Fiat?,
): ProfilePinnedAction? = when {
    isSelf -> null
    isBlocked -> ProfilePinnedAction.Unblock
    dmExists -> ProfilePinnedAction.OpenChat
    else -> ProfilePinnedAction.StartChatting(fee)
}

/**
 * The rows under another user's profile, in order: the reversible and routine first, the one that
 * ends the conversation last. Muting is the DM's, so it only appears once one exists.
 */
internal fun profileMenuItems(isBlocked: Boolean, hasDm: Boolean): List<ChatProfileAction> =
    if (isBlocked) {
        listOf(ChatProfileAction.Report, ChatProfileAction.Unblock)
    } else {
        buildList {
            if (hasDm) add(ChatProfileAction.Mute)
            add(ChatProfileAction.Report)
            add(ChatProfileAction.Block)
        }
    }

/**
 * The string behind the pinned button. Only [ProfilePinnedAction.StartChatting] with a known fee
 * takes an argument, the formatted fee.
 */
@StringRes
internal fun ProfilePinnedAction.labelRes(): Int = when (this) {
    ProfilePinnedAction.Unblock -> R.string.action_unblock
    ProfilePinnedAction.OpenChat -> R.string.action_openChat
    is ProfilePinnedAction.StartChatting ->
        if (fee != null) R.string.action_sendToStartChatting else R.string.action_startChatting
}
