package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import com.flipcash.app.menu.FullMenuItem
import com.flipcash.app.menu.MenuItem
import com.flipcash.features.messenger.R

/**
 * What the group's profile can act on.
 *
 * Its own type rather than [com.flipcash.app.messenger.internal.ChatViewModel.Event], because the
 * rows do different kinds of thing: leaving is a request to the conversation's view model, inviting
 * and muting are navigations. Naming them here lets one
 * [com.flipcash.app.menu.MenuList] carry them and the screen decide which is which.
 */
internal sealed interface GroupProfileAction {
    data object Invite : GroupProfileAction
    data object Mute : GroupProfileAction
    data object Leave : GroupProfileAction
    data object Report : GroupProfileAction

    /** Opens node 10187:110373's list of what about the group can be changed. */
    data object Edit : GroupProfileAction
}

/**
 * The same invite the empty transcript offers, for a group that already has messages in it.
 *
 * Shown only while there is a link to hand out, which is the same condition as being a member.
 *
 * A person-with-plus glyph, matching iOS' `person.badge.plus` on the same row. Not the `@` one:
 * `ic_at` belongs to the row *inside* the sheet this opens, where it stands for the username the
 * link is sent to (node 10127:118328). Not `ic_group_3` either, which names a group rather than the
 * act of adding to one — that is the New Chat row's job.
 */
internal data object InviteToGroup : FullMenuItem<GroupProfileAction>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.PersonAdd)

    override val name: String
        @Composable get() = stringResource(R.string.action_invitePeopleToJoin)

    override val action: GroupProfileAction = GroupProfileAction.Invite
}

internal data object ReportGroup : FullMenuItem<GroupProfileAction>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.Flag)

    override val name: String
        @Composable get() = stringResource(R.string.title_report)

    override val action: GroupProfileAction = GroupProfileAction.Report
}

internal data object LeaveChat : FullMenuItem<GroupProfileAction>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.AutoMirrored.Outlined.Logout)

    override val name: String
        @Composable get() = stringResource(R.string.title_leaveChat)

    override val action: GroupProfileAction = GroupProfileAction.Leave
}

/** The group's mute row. Shared definition; see [MuteChatItem]. */
internal val MuteChat = MuteChatItem<GroupProfileAction>(GroupProfileAction.Mute)

/**
 * The group's edit row, in the profile's top-right overflow rather than in the list below it.
 *
 * A pencil, the same glyph the message overflow uses for editing a message — one verb, one icon.
 * Not `ic_group_3`, which names a group rather than the act of changing one.
 *
 * Node 10187:110373 is the screen this opens; the overflow it hangs from is the message
 * long-press menu's shape reused (see `ChatTopBar.MessageOverflow`).
 */
internal data object EditGroup : FullMenuItem<GroupProfileAction>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.Edit)

    override val name: String
        @Composable get() = stringResource(R.string.action_edit)

    override val action: GroupProfileAction = GroupProfileAction.Edit
}

/**
 * What the profile's top-right overflow holds, for a viewer the server says may edit.
 *
 * [canEdit] comes from `ViewerState.Permissions` and is server-computed. It is passed in rather
 * than derived here on purpose: membership, being the creator, and the chat's kind all *look* like
 * they answer the question and none of them is the answer the server gives, so a local guess would
 * offer an Edit that `EditChat` then refuses with `DENIED`.
 *
 * Returns a list so the overflow has nothing to show — and so draws nothing — when the answer is
 * no. A single-item menu is still a menu; an empty one is not a button.
 */
internal fun groupProfileOverflowItems(canEdit: Boolean): List<MenuItem<GroupProfileAction>> =
    if (canEdit) listOf(EditGroup) else emptyList()
