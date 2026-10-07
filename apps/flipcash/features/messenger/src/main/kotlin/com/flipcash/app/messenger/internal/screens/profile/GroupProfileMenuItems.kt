package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.ui.graphics.vector.ImageVector
import com.flipcash.features.messenger.R

/**
 * What the group profile's top-right overflow can do, in the order it lists them.
 *
 * Kept apart from the composable that draws the menu so what each viewer is offered can be tested
 * without a screen: Mute follows membership, and Report does not.
 */
internal enum class GroupProfileMenuAction {
    Encryption,
    Mute,
    Report;

    /** Report is drawn in the destructive colour, after a divider. */
    val isDestructive: Boolean get() = this == Report

    @androidx.annotation.StringRes
    fun labelRes(): Int = when (this) {
        Encryption -> R.string.title_encryption
        Mute -> R.string.title_muteChat
        Report -> R.string.title_report
    }

    fun icon(): ImageVector = when (this) {
        Encryption -> Icons.Outlined.LockOpen
        Mute -> Icons.Outlined.NotificationsOff
        Report -> Icons.Outlined.Feedback
    }
}

/**
 * The overflow's rows for a viewer.
 *
 * A non-member has no notification setting to change, so Mute is members only. Report is always
 * there: a group you have already left is the one you are most likely to report. Invite, Edit and
 * Leave are not here; they live on the profile's own buttons.
 */
internal fun groupProfileMenuActions(isMember: Boolean): List<GroupProfileMenuAction> = buildList {
    add(GroupProfileMenuAction.Encryption)
    if (isMember) add(GroupProfileMenuAction.Mute)
    add(GroupProfileMenuAction.Report)
}
